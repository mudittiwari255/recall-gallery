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
import app.recall.core.DateQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

data class Hit(val photo: Photo, val score: Float)

/** The photo viewer shows one list (gallery or results) and can swipe through it. */
data class ViewerState(val photos: List<Photo>, val start: Int)

enum class Tab { SEARCH, ALL }

data class UiState(
    val tab: Tab = Tab.SEARCH,
    /** Photos that can be found by search right now. */
    val indexedIds: Set<Long> = emptySet(),
    val preparingPhoto: Boolean = false,
    val thermal: Int = 0,
    // welcome flow: 0 welcome, 1 privacy, 2 permissions, 3 folders, 4 download; null when finished
    val onboardingStep: Int? = 0,
    val permissionDenied: Boolean = false,
    val hasPermission: Boolean = false,
    val fullAccess: Boolean = true,
    val photos: List<Photo> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val minYear: Int = 2000,
    val maxYear: Int = 2026,
    val filters: Filters = Filters(),
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
    val shownCount: Int = 0,
    val resultsTitle: String = "",
    val resultsByDate: Boolean = false,
    val searchMillis: Long = 0,
    val recent: List<String> = emptyList(),
    val message: String? = null,
    val viewer: ViewerState? = null,
    val photoDetail: Int = 70,
    val scopeFolders: Set<String>? = null,
    val dateRange: DateRange = DateRange.ALL,
    val oldestFirst: Boolean = false,
    val chargingOnly: Boolean = false,
    val scopeDirty: Boolean = false,
) {
    /** All photos after filters (the All photos tab). */
    val visiblePhotos: List<Photo>
        get() = if (filters.activeCount == 0) photos else filters.matcher().let { m -> photos.filter(m) }

    /** Searchable photos after filters (the Search tab). */
    val searchablePhotos: List<Photo>
        get() = filters.matcher().let { m -> photos.filter { it.id in indexedIds && m(it) } }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as RecallApp
    private val work = WorkManager.getInstance(app)
    private val settings = app.settings
    private val _state = MutableStateFlow(
        UiState(
            onboardingStep = if (settings.onboardingDone || (app.models.isReady && app.hasPhotoPermission())) null else 0,
            photoDetail = settings.photoDetail,
            scopeFolders = settings.folders,
            dateRange = settings.dateRange,
            oldestFirst = settings.oldestFirst,
            chargingOnly = settings.chargingOnly,
            recent = settings.recentSearches,
        ),
    )
    val state: StateFlow<UiState> = _state

    private var searchJob: Job? = null
    private var lastQueryVec: FloatArray? = null
    private var lastExclude: Long? = null
    private var lastRaw: String? = null
    private var lastCountRefresh = 0L
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        private var pending: Job? = null
        override fun onChange(selfChange: Boolean) {
            pending?.cancel()
            pending = viewModelScope.launch { delay(1500); loadPhotos() }
        }
    }

    init {
        if (_state.value.onboardingStep == null) settings.onboardingDone = true
        // Older builds scheduled a second indexing job that could run alongside the main one; replace it once
        if (app.models.isReady && !settings.periodicV2) {
            Work.schedulePeriodic(app, replace = true)
            settings.periodicV2 = true
        }
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
    }

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
                thermal = if (running) app.thermalStatus() else 0,
            )
        }
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
        val zone = ZoneId.systemDefault()
        val years = photos.asSequence().map { Instant.ofEpochMilli(it.dateTaken).atZone(zone).year }.filter { it in 1990..2100 }
        val minYear = years.minOrNull() ?: 2020
        val maxYear = maxOf(years.maxOrNull() ?: minYear, minYear)
        _state.update { s ->
            s.copy(
                photos = photos, folders = folders, minYear = minYear, maxYear = maxYear,
                filters = s.filters.copy(folder = s.filters.folder?.takeIf { f -> folders.any { it.id == f } }),
            )
        }
        refreshCounts()
        if (app.models.isReady && !_state.value.indexing && _state.value.onboardingStep == null) {
            val processed = withContext(Dispatchers.IO) { app.db.processed() }
            if (app.indexSelection(photos).any { processed[it.id] != it.modified }) Work.startIndexing(app)
        }
    }

    private suspend fun refreshCounts() {
        val photos = _state.value.photos
        val ids = withContext(Dispatchers.IO) { app.db.indexedIds() }
        val (selected, selectedIndexed) = withContext(Dispatchers.Default) {
            val sel = app.indexSelection(photos)
            sel.size to sel.count { it.id in ids }
        }
        _state.update { it.copy(indexedIds = ids, indexedCount = ids.size, selectedCount = selected, selectedIndexed = selectedIndexed) }
    }

    // ------------------------------------------------------------------ welcome flow

    fun nextStep() = _state.update { s -> s.copy(onboardingStep = s.onboardingStep?.let { it + 1 }, permissionDenied = false) }
    fun backStep() = _state.update { s -> s.copy(onboardingStep = s.onboardingStep?.let { (it - 1).coerceAtLeast(0) }) }

    fun onPermissionResult() {
        refresh()
        val granted = app.hasPhotoPermission()
        _state.update { s ->
            if (s.onboardingStep == 2) {
                if (granted) s.copy(onboardingStep = 3, permissionDenied = false) else s.copy(permissionDenied = true)
            } else s
        }
    }

    fun finishOnboarding(download: Boolean) {
        settings.onboardingDone = true
        _state.update { it.copy(onboardingStep = null, scopeDirty = false) }
        if (download && !app.models.isReady) downloadModel()
        viewModelScope.launch { refreshCounts() }
    }

    // ------------------------------------------------------------------ model + indexing

    fun downloadModel() {
        _state.update { it.copy(downloadFailed = false, downloading = true) }
        Work.startDownload(app)
    }

    fun startIndexing() = Work.startIndexing(app)
    fun stopIndexing() = Work.stopIndexing(app)

    fun setPhotoDetail(budget: Int) {
        settings.photoDetail = budget
        _state.update { it.copy(photoDetail = budget, scopeDirty = true) }
    }

    fun setDateRange(r: DateRange) {
        settings.dateRange = r
        _state.update { it.copy(dateRange = r, scopeDirty = true) }
        viewModelScope.launch { refreshCounts() }
    }

    fun setOldestFirst(v: Boolean) {
        settings.oldestFirst = v
        _state.update { it.copy(oldestFirst = v, scopeDirty = true) }
    }

    fun setChargingOnly(v: Boolean) {
        settings.chargingOnly = v
        _state.update { it.copy(chargingOnly = v, scopeDirty = true) }
    }

    fun setAllFolders(all: Boolean) {
        val v: Set<String>? = if (all) null else emptySet()
        settings.folders = v
        _state.update { it.copy(scopeFolders = v, scopeDirty = true) }
        viewModelScope.launch { refreshCounts() }
    }

    fun toggleFolder(id: String) {
        val current = _state.value.scopeFolders ?: _state.value.folders.map { it.id }.toSet()
        val next = if (id in current) current - id else current + id
        val v: Set<String>? = if (next.size == _state.value.folders.size) null else next
        settings.folders = v
        _state.update { it.copy(scopeFolders = v, scopeDirty = true) }
        viewModelScope.launch { refreshCounts() }
    }

    /** Applies scope changes when the settings panel closes. */
    fun applyScope() {
        if (!_state.value.scopeDirty) return
        _state.update { it.copy(scopeDirty = false) }
        viewModelScope.launch {
            refreshCounts()
            if (app.models.isReady) Work.restartIndexing(app)
        }
    }

    fun reindexAll() {
        viewModelScope.launch {
            Work.stopIndexing(app)
            withContext(Dispatchers.IO) { app.db.clear() }
            refreshCounts()
            Work.restartIndexing(app)
        }
    }

    fun deleteEverything() {
        viewModelScope.launch {
            Work.stopIndexing(app)
            WorkManager.getInstance(app).cancelUniqueWork(Work.INDEX_PERIODIC)
            withContext(Dispatchers.IO) { app.db.clear(); app.models.delete() }
            clearResults()
            _state.update { it.copy(modelReady = false) }
            refreshCounts()
            toast("Removed the search engine and the index. Your photos were not touched.")
        }
    }

    // ------------------------------------------------------------------ filters

    fun setFilters(f: Filters) {
        _state.update { it.copy(filters = f) }
        rerun()
    }

    fun setYears(fromYear: Int, toYear: Int) {
        val s = _state.value
        setFilters(s.filters.withYears(fromYear, toYear, s.minYear, s.maxYear))
    }

    /** Removing a date that came from the search text searches the full text again, without date parsing. */
    fun clearDate() {
        val f = _state.value.filters
        _state.update { it.copy(filters = f.clearDate()) }
        val raw = lastRaw
        if (f.dateFromQuery && raw != null) search(raw, parseDates = false) else rerun()
    }

    fun clearFilters() {
        _state.update { it.copy(filters = Filters()) }
        rerun()
    }

    private fun rerun() {
        val s = _state.value
        if (s.results == null) return
        val vec = lastQueryVec
        viewModelScope.launch {
            if (vec != null) showRanked(vec, lastExclude, s.resultsTitle) else showByDate(s.resultsTitle)
        }
    }

    // ------------------------------------------------------------------ search

    fun setQuery(q: String) {
        _state.update { it.copy(query = q) }
        if (q.isBlank() && _state.value.results != null) clearResults()
    }

    fun clearResults() {
        searchJob?.cancel()
        lastQueryVec = null; lastExclude = null; lastRaw = null
        _state.update { s ->
            s.copy(
                results = null, resultsTitle = "", searching = false, query = "",
                filters = if (s.filters.dateFromQuery) s.filters.clearDate() else s.filters,
            )
        }
    }

    fun search(text: String = _state.value.query, parseDates: Boolean = true) {
        val raw = text.trim()
        if (raw.isEmpty()) return
        lastRaw = raw
        settings.addRecent(raw)
        val parsed = if (parseDates) DateQuery.parse(raw) else DateQuery.Parsed(raw, null, null, null)
        _state.update { s ->
            val f = if (parsed.hasDate) {
                s.filters.copy(from = parsed.from, until = parsed.until, dateLabel = parsed.label, dateFromQuery = true)
            } else if (s.filters.dateFromQuery) s.filters.clearDate() else s.filters
            s.copy(query = raw, filters = f, recent = settings.recentSearches, tab = Tab.SEARCH)
        }
        searchJob?.cancel()
        if (parsed.text.isEmpty()) {
            // Only a date: show everything from that time, newest first
            lastQueryVec = null; lastExclude = null
            searchJob = viewModelScope.launch { showByDate("Photos from ${parsed.label}") }
            return
        }
        if (!app.models.isReady) { toast("Download the search engine first (see the card at the top)."); return }
        searchJob = viewModelScope.launch {
            _state.update { it.copy(searching = true) }
            try {
                val t0 = System.currentTimeMillis()
                app.searchBusyUntil = System.currentTimeMillis() + 5000
                val qv = try {
                    withContext(Dispatchers.Default) { app.models.embedder().embedQuery(parsed.text) }
                } finally {
                    app.searchBusyUntil = 0L
                }
                lastQueryVec = qv; lastExclude = null
                showRanked(qv, null, "“${parsed.text}”")
                _state.update { it.copy(searchMillis = System.currentTimeMillis() - t0) }
            } catch (e: Exception) {
                _state.update { it.copy(searching = false) }
                toast("Search failed: ${e.message}")
            }
        }
    }

    /** Works for any photo: one that isn't ready yet is prepared on the spot (a second or two) and kept. */
    fun findSimilar(photo: Photo) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            var vec = withContext(Dispatchers.IO) { app.db.index().get(photo.id) }
            if (vec == null) {
                if (!app.models.isReady) { toast("Download the search engine first."); return@launch }
                _state.update { it.copy(preparingPhoto = true) }
                app.searchBusyUntil = System.currentTimeMillis() + 15000
                vec = try {
                    withContext(Dispatchers.Default) {
                        val budget = settings.photoDetail
                        val v = app.models.embedder().embedImage(app.media.decodeForModel(photo.uri, app.media.decodeSideFor(budget)), budget)
                        app.db.put(photo.id, photo.modified, budget, v)
                        v
                    }
                } catch (e: Throwable) {
                    null
                } finally {
                    app.searchBusyUntil = 0L
                    _state.update { it.copy(preparingPhoto = false) }
                }
                if (vec == null) { toast("Couldn't read this photo."); return@launch }
                refreshCounts()
            }
            lastQueryVec = vec; lastExclude = photo.id; lastRaw = null
            val t0 = System.currentTimeMillis()
            showRanked(vec, photo.id, "Looks like this photo")
            _state.update { it.copy(viewer = null, query = "", tab = Tab.SEARCH, searchMillis = System.currentTimeMillis() - t0) }
        }
    }

    fun setTab(t: Tab) = _state.update { it.copy(tab = t, viewer = null) }

    fun showMore() = _state.update { s -> s.copy(shownCount = minOf(s.shownCount + 30, s.results?.size ?: 0)) }

    fun removeRecent(q: String) {
        settings.recentSearches = settings.recentSearches.filterNot { it == q }
        _state.update { it.copy(recent = settings.recentSearches) }
    }

    private suspend fun showRanked(q: FloatArray, exclude: Long?, title: String) {
        val s = _state.value
        val byId = s.photos.associateBy { it.id }
        val match = s.filters.matcher()
        val hits = withContext(Dispatchers.Default) {
            app.db.index().search(q, 400) { id -> id != exclude && byId[id]?.let(match) == true }
                .mapNotNull { (id, score) -> byId[id]?.let { Hit(it, score) } }
        }
        _state.update { it.copy(results = hits, shownCount = initialShown(hits), resultsTitle = title, resultsByDate = false, searching = false) }
    }

    private suspend fun showByDate(title: String) {
        val hits = withContext(Dispatchers.Default) { _state.value.visiblePhotos.map { Hit(it, 0f) } }
        _state.update { it.copy(tab = Tab.SEARCH) }
        _state.update { it.copy(results = hits, shownCount = minOf(hits.size, 60), resultsTitle = title, resultsByDate = true, searching = false, searchMillis = 0) }
    }

    /**
     * How many results to show before "Show more": the clearly strong matches (within a small margin of the best one),
     * at least 6 and at most 18, so a search shows a tidy handful instead of a wall of weak guesses.
     */
    private fun initialShown(hits: List<Hit>): Int {
        if (hits.isEmpty()) return 0
        val top = hits[0].score
        val strong = hits.count { it.score >= top - 0.05f }
        return strong.coerceIn(minOf(6, hits.size), minOf(18, hits.size))
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
