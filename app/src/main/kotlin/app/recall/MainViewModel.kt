package app.recall

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.recall.core.Embedder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Hit(val photo: Photo, val score: Float)

data class UiState(
    val hasPermission: Boolean = false,
    val fullAccess: Boolean = true,
    val photos: List<Photo> = emptyList(),
    val modelReady: Boolean = false,
    val downloading: Boolean = false,
    val downloadDone: Long = 0,
    val downloadTotal: Long = 0,
    val downloadFailed: Boolean = false,
    val indexing: Boolean = false,
    val indexDone: Int = 0,
    val indexTotal: Int = 0,
    val indexedCount: Int = 0,
    val query: String = "",
    val searching: Boolean = false,
    val results: List<Hit>? = null,
    val resultsTitle: String = "",
    val searchMillis: Long = 0,
    val message: String? = null,
    val viewing: Photo? = null,
    val photoDetail: Int = 70,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as RecallApp
    private val work = WorkManager.getInstance(app)
    private val _state = MutableStateFlow(UiState(photoDetail = app.settings.photoDetail))
    val state: StateFlow<UiState> = _state

    private var searchJob: Job? = null
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        private var pending: Job? = null
        override fun onChange(selfChange: Boolean) {
            pending?.cancel()
            pending = viewModelScope.launch { delay(1500); loadPhotos() }
        }
    }

    init {
        app.contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        viewModelScope.launch {
            work.getWorkInfosForUniqueWorkFlow(Work.DOWNLOAD).collect { infos ->
                val info = infos.firstOrNull()
                val running = info?.state == WorkInfo.State.RUNNING || info?.state == WorkInfo.State.ENQUEUED
                _state.update {
                    it.copy(
                        downloading = running,
                        downloadDone = info?.progress?.getLong("done", it.downloadDone) ?: it.downloadDone,
                        downloadTotal = info?.progress?.getLong("total", it.downloadTotal) ?: it.downloadTotal,
                        downloadFailed = info?.state == WorkInfo.State.FAILED,
                        modelReady = app.models.isReady,
                    )
                }
            }
        }
        viewModelScope.launch {
            work.getWorkInfosForUniqueWorkFlow(Work.INDEX).collect { infos -> onIndexInfo(infos.firstOrNull()) }
        }
        viewModelScope.launch {
            work.getWorkInfosForUniqueWorkFlow(Work.INDEX_PERIODIC).collect { infos ->
                infos.firstOrNull()?.takeIf { it.state == WorkInfo.State.RUNNING }?.let(::onIndexInfo)
            }
        }
    }

    private fun onIndexInfo(info: WorkInfo?) {
        val running = info?.state == WorkInfo.State.RUNNING
        _state.update {
            it.copy(
                indexing = running,
                indexDone = info?.progress?.getInt("done", it.indexDone) ?: it.indexDone,
                indexTotal = info?.progress?.getInt("total", it.indexTotal) ?: it.indexTotal,
            )
        }
        viewModelScope.launch { refreshCounts() }
    }

    /** Called from onResume: permissions or the library may have changed while we were away. */
    fun refresh() {
        _state.update {
            it.copy(hasPermission = app.hasPhotoPermission(), fullAccess = app.hasFullPhotoAccess(), modelReady = app.models.isReady)
        }
        viewModelScope.launch { loadPhotos() }
    }

    private suspend fun loadPhotos() {
        if (!app.hasPhotoPermission()) return
        val photos = withContext(Dispatchers.IO) { app.media.loadAll() }
        _state.update { it.copy(photos = photos) }
        refreshCounts()
        if (app.models.isReady && !_state.value.indexing) {
            // Start indexing when there are new photos (KEEP makes this a no-op while a run is active)
            val processed = withContext(Dispatchers.IO) { app.db.processed() }
            if (photos.any { processed[it.id] != it.modified }) Work.startIndexing(app)
        }
    }

    private suspend fun refreshCounts() {
        val n = withContext(Dispatchers.IO) { app.db.indexedCount() }
        _state.update { it.copy(indexedCount = n) }
    }

    fun onPermissionResult() = refresh()

    fun downloadModel() {
        _state.update { it.copy(downloadFailed = false, downloading = true) }
        Work.startDownload(app)
    }

    fun startIndexing() = Work.startIndexing(app)
    fun stopIndexing() = Work.stopIndexing(app)

    fun setPhotoDetail(budget: Int) {
        app.settings.photoDetail = budget
        _state.update { it.copy(photoDetail = budget) }
    }

    /** Re-embeds every photo, e.g. after changing photo detail. */
    fun reindexAll() {
        viewModelScope.launch {
            Work.stopIndexing(app)
            withContext(Dispatchers.IO) { app.db.clear() }
            refreshCounts()
            Work.startIndexing(app)
        }
    }

    fun setQuery(q: String) {
        _state.update { it.copy(query = q) }
        if (q.isBlank()) clearResults()
    }

    fun clearResults() {
        searchJob?.cancel()
        _state.update { it.copy(results = null, resultsTitle = "", searching = false, query = "") }
    }

    fun search() {
        val q = _state.value.query.trim()
        if (q.isEmpty()) return
        if (!app.models.isReady) { toast("Download the search model first."); return }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.update { it.copy(searching = true) }
            try {
                val t0 = System.currentTimeMillis()
                val qv = withContext(Dispatchers.Default) { app.models.embedder().embedQuery(q) }
                val hits = rank(qv, exclude = null)
                _state.update {
                    it.copy(results = hits, resultsTitle = "“$q”", searching = false, searchMillis = System.currentTimeMillis() - t0)
                }
            } catch (e: Exception) {
                _state.update { it.copy(searching = false) }
                toast("Search failed: ${e.message}")
            }
        }
    }

    fun findSimilar(photo: Photo) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val vec = withContext(Dispatchers.IO) { app.db.vectors()[photo.id] }
            if (vec == null) { toast("This photo isn't indexed yet."); return@launch }
            val hits = rank(vec, exclude = photo.id)
            _state.update { it.copy(results = hits, resultsTitle = "Similar to ${photo.name}", viewing = null, query = "") }
        }
    }

    private suspend fun rank(q: FloatArray, exclude: Long?): List<Hit> = withContext(Dispatchers.Default) {
        val vectors = app.db.vectors()
        val byId = _state.value.photos.associateBy { it.id }
        vectors.mapNotNull { (id, v) ->
            if (id == exclude) null else byId[id]?.let { Hit(it, Embedder.dot(q, v)) }
        }.sortedByDescending { it.score }.take(200)
    }

    fun warmUp() {
        if (!app.models.isReady) return
        viewModelScope.launch(Dispatchers.Default) { runCatching { app.models.embedder().warmUpText() } }
    }

    fun view(photo: Photo?) = _state.update { it.copy(viewing = photo) }

    fun toast(msg: String) = _state.update { it.copy(message = msg) }
    fun messageShown() = _state.update { it.copy(message = null) }

    override fun onCleared() {
        app.contentResolver.unregisterContentObserver(observer)
    }
}
