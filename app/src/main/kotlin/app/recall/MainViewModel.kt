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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Hit(val photo: Photo, val score: Float)

/** The photo viewer shows one list (gallery or results) and can swipe through it. */
data class ViewerState(val photos: List<Photo>, val start: Int)

data class UiState(
    val hasPermission: Boolean = false,
    val fullAccess: Boolean = true,
    val photos: List<Photo> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val folderFilter: String? = null,
    val modelReady: Boolean = false,
    val downloading: Boolean = false,
    val downloadDone: Long = 0,
    val downloadTotal: Long = 0,
    val downloadFailed: Boolean = false,
    val indexing: Boolean = false,
    val indexDone: Int = 0,
    val indexTotal: Int = 0,
    val msPerPhoto: Long = 0,
    val etaSec: Long = 0,
    val indexedCount: Int = 0,
    val selectedCount: Int = 0,
    val selectedIndexed: Int = 0,
    val query: String = "",
    val searching: Boolean = false,
    val results: List<Hit>? = null,
    val resultsTitle: String = "",
    val searchMillis: Long = 0,
    val message: String? = null,
    val viewer: ViewerState? = null,
    val photoDetail: Int = 70,
    val scopeFolders: Set<String>? = null,
    val dateRange: DateRange = DateRange.ALL,
    val oldestFirst: Boolean = false,
    val scopeDirty: Boolean = false,
) {
    /** Gallery photos after the folder chip filter. */
    val visiblePhotos: List<Photo>
        get() = folderFilter?.let { f -> photos.filter { it.bucketId == f } } ?: photos
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as RecallApp
    private val work = WorkManager.getInstance(app)
    private val _state = MutableStateFlow(
        UiState(
            photoDetail = app.settings.photoDetail,
            scopeFolders = app.settings.folders,
            dateRange = app.settings.dateRange,
            oldestFirst = app.settings.oldestFirst,
        ),
    )
    val state: StateFlow<UiState> = _state

    private var searchJob: Job? = null
    private var lastQueryVec: FloatArray? = null
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

    private var lastCountRefresh = 0L

    private fun onIndexInfo(info: WorkInfo?) {
        val running = info?.state == WorkInfo.State.RUNNING
        val p = info?.progress
        _state.update {
            it.copy(
                indexing = running,
                indexDone = p?.getInt("done", it.indexDone) ?: it.indexDone,
                indexTotal = p?.getInt("total", it.indexTotal) ?: it.indexTotal,
                msPerPhoto = p?.getLong("msPerPhoto", 0L)?.takeIf { v -> v > 0 } ?: it.msPerPhoto,
                etaSec = p?.getLong("etaSec", it.etaSec) ?: it.etaSec,
            )
        }
        // Counting rows is cheap but not free; refresh at most every few seconds while indexing
        val now = System.currentTimeMillis()
        if (!running || now - lastCountRefresh > 4000) {
            lastCountRefresh = now
            viewModelScope.launch { refreshCounts() }
        }
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
        val folders = withContext(Dispatchers.Default) { foldersOf(photos) }
        _state.update { s ->
            s.copy(photos = photos, folders = folders, folderFilter = s.folderFilter?.takeIf { f -> folders.any { it.id == f } })
        }
        refreshCounts()
        if (app.models.isReady && !_state.value.indexing) {
            // Start indexing when selected photos are new (KEEP makes this a no-op while a run is active)
            val processed = withContext(Dispatchers.IO) { app.db.processed() }
            if (app.indexSelection(photos).any { processed[it.id] != it.modified }) Work.startIndexing(app)
        }
    }

    private suspend fun refreshCounts() {
        val photos = _state.value.photos
        val (indexed, selected, selectedIndexed) = withContext(Dispatchers.IO) {
            val ids = app.db.indexedIds()
            val sel = app.indexSelection(photos)
            Triple(ids.size, sel.size, sel.count { it.id in ids })
        }
        _state.update { it.copy(indexedCount = indexed, selectedCount = selected, selectedIndexed = selectedIndexed) }
    }

    fun onPermissionResult() = refresh()

    fun downloadModel() {
        _state.update { it.copy(downloadFailed = false, downloading = true) }
        Work.startDownload(app)
    }

    fun startIndexing() = Work.startIndexing(app)
    fun stopIndexing() = Work.stopIndexing(app)

    // ------------------------------------------------------------------ indexing scope

    fun setPhotoDetail(budget: Int) {
        app.settings.photoDetail = budget
        _state.update { it.copy(photoDetail = budget, scopeDirty = true) }
    }

    fun setDateRange(r: DateRange) {
        app.settings.dateRange = r
        _state.update { it.copy(dateRange = r, scopeDirty = true) }
    }

    fun setOldestFirst(v: Boolean) {
        app.settings.oldestFirst = v
        _state.update { it.copy(oldestFirst = v, scopeDirty = true) }
    }

    fun setAllFolders(all: Boolean) {
        val v: Set<String>? = if (all) null else _state.value.folders.map { it.id }.toSet()
        app.settings.folders = v
        _state.update { it.copy(scopeFolders = v, scopeDirty = true) }
    }

    fun toggleFolder(id: String) {
        val current = _state.value.scopeFolders ?: _state.value.folders.map { it.id }.toSet()
        val next = if (id in current) current - id else current + id
        app.settings.folders = next
        _state.update { it.copy(scopeFolders = next, scopeDirty = true) }
    }

    /** Applies scope changes when the settings sheet closes. */
    fun applyScope() {
        if (!_state.value.scopeDirty) return
        _state.update { it.copy(scopeDirty = false) }
        viewModelScope.launch {
            refreshCounts()
            if (app.models.isReady) Work.restartIndexing(app)
        }
    }

    /** Re-embeds every photo, e.g. after changing photo detail. */
    fun reindexAll() {
        viewModelScope.launch {
            Work.stopIndexing(app)
            withContext(Dispatchers.IO) { app.db.clear() }
            refreshCounts()
            Work.restartIndexing(app)
        }
    }

    // ------------------------------------------------------------------ gallery + search

    fun setFolderFilter(id: String?) {
        _state.update { it.copy(folderFilter = id) }
        // Re-rank the current text search inside the new folder
        lastQueryVec?.let { v -> if (_state.value.results != null) viewModelScope.launch { showRanked(v, null, _state.value.resultsTitle) } }
    }

    fun setQuery(q: String) {
        _state.update { it.copy(query = q) }
        if (q.isBlank()) clearResults()
    }

    fun clearResults() {
        searchJob?.cancel()
        lastQueryVec = null
        _state.update { it.copy(results = null, resultsTitle = "", searching = false, query = "") }
    }

    fun search(text: String = _state.value.query) {
        val q = text.trim()
        if (q.isEmpty()) return
        if (!app.models.isReady) { toast("Download the search model first."); return }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.update { it.copy(searching = true, query = q) }
            try {
                val t0 = System.currentTimeMillis()
                val qv = withContext(Dispatchers.Default) { app.models.embedder().embedQuery(q) }
                lastQueryVec = qv
                showRanked(qv, null, "“$q”")
                _state.update { it.copy(searchMillis = System.currentTimeMillis() - t0) }
            } catch (e: Exception) {
                _state.update { it.copy(searching = false) }
                toast("Search failed: ${e.message}")
            }
        }
    }

    fun findSimilar(photo: Photo) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val vec = withContext(Dispatchers.IO) { app.db.index().get(photo.id) }
            if (vec == null) { toast("This photo isn't indexed yet."); return@launch }
            lastQueryVec = vec
            val t0 = System.currentTimeMillis()
            showRanked(vec, photo.id, "Similar to this photo")
            _state.update { it.copy(viewer = null, query = "", searchMillis = System.currentTimeMillis() - t0) }
        }
    }

    private suspend fun showRanked(q: FloatArray, exclude: Long?, title: String) {
        val s = _state.value
        val byId = s.photos.associateBy { it.id }
        val folder = s.folderFilter
        val hits = withContext(Dispatchers.Default) {
            app.db.index().search(q, 300) { id ->
                id != exclude && byId[id]?.let { folder == null || it.bucketId == folder } == true
            }.mapNotNull { (id, score) -> byId[id]?.let { Hit(it, score) } }
        }
        _state.update { it.copy(results = hits, resultsTitle = title, searching = false) }
    }

    fun warmUp() {
        if (!app.models.isReady) return
        viewModelScope.launch(Dispatchers.Default) {
            runCatching { app.models.embedder().warmUpText() }
            runCatching { app.db.index() }
        }
    }

    fun openViewer(list: List<Photo>, index: Int) = _state.update { it.copy(viewer = ViewerState(list, index)) }
    fun closeViewer() = _state.update { it.copy(viewer = null) }

    fun toast(msg: String) = _state.update { it.copy(message = msg) }
    fun messageShown() = _state.update { it.copy(message = null) }

    override fun onCleared() {
        app.contentResolver.unregisterContentObserver(observer)
    }
}
