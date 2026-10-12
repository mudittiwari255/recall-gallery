@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.recall

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import coil3.compose.AsyncImage
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { RecallTheme { RecallScreen(vm) } }
    }
}

// ---------------------------------------------------------------------------- theme

private val Amber = Color(0xFFF2B33D)
private val AmberDeep = Color(0xFF9A6410)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Amber, onPrimary = Color(0xFF1B1406),
    primaryContainer = Color(0xFF3A2F17), onPrimaryContainer = Color(0xFFFFE2A8),
    secondaryContainer = Color(0xFF3A2F17), onSecondaryContainer = Color(0xFFFFE2A8),
    background = Color(0xFF12141D), onBackground = Color(0xFFE9EAF2),
    surface = Color(0xFF12141D), onSurface = Color(0xFFE9EAF2),
    surfaceVariant = Color(0xFF252938), onSurfaceVariant = Color(0xFF9A9EB4),
    surfaceContainer = Color(0xFF1B1E2B), surfaceContainerHigh = Color(0xFF222636),
    surfaceContainerLow = Color(0xFF171A25),
    outline = Color(0xFF3A3F57), outlineVariant = Color(0xFF30344A),
)

private val LightColors: ColorScheme = lightColorScheme(
    primary = AmberDeep, onPrimary = Color.White,
    primaryContainer = Color(0xFFF6E7C8), onPrimaryContainer = Color(0xFF3A2A06),
    secondaryContainer = Color(0xFFF6E7C8), onSecondaryContainer = Color(0xFF3A2A06),
    background = Color(0xFFF1F2F7), onBackground = Color(0xFF1B1E2E),
    surface = Color(0xFFF1F2F7), onSurface = Color(0xFF1B1E2E),
    surfaceVariant = Color(0xFFE6E8F0), onSurfaceVariant = Color(0xFF5B6077),
    surfaceContainer = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F8FB),
    outline = Color(0xFFC9CDDB), outlineVariant = Color(0xFFD6D9E4),
)

@Composable
fun RecallTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}

private val nf: NumberFormat = NumberFormat.getIntegerInstance()
private val monthFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

private val suggestions = listOf(
    "Bills and receipts", "Screenshots of chats", "ID cards and documents", "Food", "Sunset", "Selfies",
    "Pets", "Handwritten notes", "Mountains", "Beach", "Birthday cake", "Last month",
)

// ---------------------------------------------------------------------------- root

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecallScreen(vm: MainViewModel) {
    val s by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showSettings by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.onPermissionResult()
    }
    fun askPermission() {
        val perms = RecallApp.photoPermissions().toMutableList()
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        permissionLauncher.launch(perms.toTypedArray())
    }

    LifecycleResumeEffect(Unit) {
        vm.refresh()
        vm.warmUp()
        onPauseOrDispose { }
    }
    LaunchedEffect(s.message) {
        s.message?.let { snackbar.showSnackbar(it); vm.messageShown() }
    }
    BackHandler(enabled = s.onboardingStep != null && s.onboardingStep!! > 0) { vm.backStep() }
    BackHandler(enabled = s.results != null && s.viewer == null) { vm.clearResults() }
    BackHandler(enabled = s.viewer != null) { vm.closeViewer() }
    BackHandler(enabled = s.speedUp) { vm.setSpeedUp(false) }

    Box(Modifier.fillMaxSize()) {
        val main = s.onboardingStep == null && s.hasPermission
        // Filtering 20k photos is cheap but not free: only redo it when its inputs change, not on every progress tick
        val searchable = remember(s.photos, s.indexedIds, s.filters) { s.searchablePhotos }
        val allVisible = remember(s.photos, s.filters) { s.visiblePhotos }
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (main) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        NavigationBarItem(
                            selected = s.tab == Tab.SEARCH, onClick = { vm.setTab(Tab.SEARCH) },
                            icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            label = { Text("Search") },
                        )
                        NavigationBarItem(
                            selected = s.tab == Tab.ALL, onClick = { vm.setTab(Tab.ALL) },
                            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                            label = { Text("All photos") },
                        )
                    }
                }
            },
        ) { pad ->
            when {
                s.onboardingStep != null -> Onboarding(s, vm, Modifier.padding(pad), ::askPermission)
                !s.hasPermission -> NeedPermission(Modifier.padding(pad), ::askPermission)
                s.tab == Tab.SEARCH -> Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding())) {
                    SearchHeader(s, vm, onSettings = { showSettings = true })
                    FilterRow(s, vm, onOpenFilters = { showFilters = true })
                    IdleChips(s, vm)
                    StatusStrip(s, vm, ::askPermission)
                    PhotoGrid(s, vm, searchable, markUnready = false, Modifier.weight(1f))
                }
                else -> Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding())) {
                    AllPhotosHeader(s, onSettings = { showSettings = true })
                    FilterRow(s, vm, onOpenFilters = { showFilters = true })
                    PhotoGrid(s, vm, allVisible, markUnready = true, Modifier.weight(1f))
                }
            }
        }
        s.viewer?.let { Viewer(it, s, vm) }
        if (s.speedUp) SpeedUpScreen(s, vm)
    }

    if (showSettings) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showSettings = false; vm.applyScope() }, sheetState = sheet) { SettingsSheet(s, vm) }
    }
    if (showFilters) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showFilters = false }, sheetState = sheet) { FiltersSheet(s, vm) }
    }
}

@Composable
private fun NeedPermission(modifier: Modifier, onAllow: () -> Unit) {
    Column(modifier.fillMaxSize().padding(horizontal = 28.dp), verticalArrangement = Arrangement.Center) {
        Text("Recall needs your photos", fontSize = 28.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(12.dp))
        Text(
            "Photo access was turned off. Recall only looks at your photos on this phone and never uploads them.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAllow, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Allow photo access") }
    }
}

// ---------------------------------------------------------------------------- header, filters, chips

@Composable
private fun SearchHeader(s: UiState, vm: MainViewModel, onSettings: () -> Unit) {
    val focus = LocalFocusManager.current
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = s.query,
            onValueChange = vm::setQuery,
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            placeholder = { Text("Describe a photo, e.g. “beach 2023”") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (s.query.isNotEmpty() || s.results != null) {
                    IconButton(onClick = { vm.clearResults(); focus.clearFocus() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); vm.search() }),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )
        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
    }
}

@Composable
private fun AllPhotosHeader(s: UiState, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("All photos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            val notReady = s.photos.size - s.photos.count { it.id in s.indexedIds }
            Text(
                "${nf.format(s.photos.size)} photos · ${nf.format(s.indexedCount)} searchable" +
                    if (notReady > 0) " · photos with a dot aren't searchable yet" else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
    }
}

/** "Filters" plus one removable chip per active filter; quick folder chips when nothing is filtered. */
@Composable
private fun FilterRow(s: UiState, vm: MainViewModel, onOpenFilters: () -> Unit) {
    val f = s.filters
    val folderName = f.folder?.let { id -> s.folders.firstOrNull { it.id == id }?.name }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 2.dp),
    ) {
        item {
            FilterChip(
                selected = f.activeCount > 0,
                onClick = onOpenFilters,
                label = { Text(if (f.activeCount > 0) "Filters · ${f.activeCount}" else "Filters") },
                leadingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
        if (f.dateLabel != null) item { RemovableChip(f.dateLabel) { vm.clearDate() } }
        if (folderName != null) item { RemovableChip(folderName) { vm.setFilters(f.copy(folder = null)) } }
        if (f.kind != Kind.ANY) item { RemovableChip(f.kind.label) { vm.setFilters(f.copy(kind = Kind.ANY)) } }
        if (f.favoritesOnly) item { RemovableChip("Favourites") { vm.setFilters(f.copy(favoritesOnly = false)) } }
        if (f.shape != Shape.ANY) item { RemovableChip(f.shape.label) { vm.setFilters(f.copy(shape = Shape.ANY)) } }
        if (f.activeCount == 0) {
            items(s.folders.take(6), key = { it.id }) { folder ->
                FilterChip(selected = false, onClick = { vm.setFilters(f.copy(folder = folder.id)) }, label = { Text(folder.name) })
            }
        }
    }
}

@Composable
private fun RemovableChip(label: String, onRemove: () -> Unit) {
    InputChip(
        selected = true,
        onClick = onRemove,
        label = { Text(label) },
        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove $label", modifier = Modifier.size(16.dp)) },
    )
}

/** Recent searches and suggestions, shown while nothing is being searched. */
@Composable
private fun IdleChips(s: UiState, vm: MainViewModel) {
    if (s.results != null || s.query.isNotEmpty() || !s.modelReady || s.indexedCount == 0) return
    val focus = LocalFocusManager.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 2.dp),
    ) {
        items(s.recent, key = { "r:$it" }) { q ->
            AssistChip(
                onClick = { focus.clearFocus(); vm.search(q) },
                label = { Text(q, maxLines = 1) },
                leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
        }
        items(suggestions.filterNot { sug -> s.recent.any { it.equals(sug, ignoreCase = true) } }, key = { "s:$it" }) { text ->
            SuggestionChip(onClick = { focus.clearFocus(); vm.search(text) }, label = { Text(text) })
        }
    }
}

// ---------------------------------------------------------------------------- status

@Composable
private fun StatusStrip(s: UiState, vm: MainViewModel, askPermission: () -> Unit) {
    val modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
    val pending = s.selectedCount - s.selectedIndexed
    when {
        !s.modelReady && s.downloading -> StatusCard(modifier) {
            val pct = if (s.downloadTotal > 0) s.downloadDone.toFloat() / s.downloadTotal else 0f
            Text("Downloading the search engine", fontWeight = FontWeight.Bold)
            Text(
                if (s.downloadTotal > 0) "${s.downloadDone / 1_000_000} of ${s.downloadTotal / 1_000_000} MB · you can leave the app" else "Starting…",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            if (s.downloadTotal > 0) LinearProgressIndicator(progress = { pct }, modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        !s.modelReady -> StatusCard(modifier) {
            Text("Turn on search", fontWeight = FontWeight.Bold)
            Text(
                if (s.downloadFailed) "The download stopped. Check your internet connection and try again."
                else "Download Recall's search engine once (about 285 MB). It's the only thing Recall ever downloads.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = vm::downloadModel) { Text(if (s.downloadFailed) "Try again" else "Download") }
        }
        s.indexing -> StatusCard(modifier) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("One-time setup", fontWeight = FontWeight.Bold)
                    Text(
                        buildString {
                            if (s.indexTotal > 0) append("${nf.format(s.indexDone)} of ${nf.format(s.indexTotal)} photos")
                            if (s.msPerPhoto > 0) append(" · ${friendlyDuration(s.etaSec)} left")
                        }.ifEmpty { "Starting…" },
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = vm::stopIndexing) { Text("Pause") }
                FilledTonalButton(onClick = { vm.setSpeedUp(true) }) { Text("Speed up") }
            }
            Spacer(Modifier.height(6.dp))
            if (s.indexTotal > 0) LinearProgressIndicator(progress = { s.indexDone.toFloat() / s.indexTotal }, modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Text(
                "Only needed once. New photos are added automatically.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall,
            )
        }
        pending > 0 && s.chargingOnly -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${nf.format(pending)} photos left · continues when charging",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
            )
        }
        pending > 0 -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Setup paused · ${nf.format(pending)} photos left",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = vm::startIndexing) { Text("Continue") }
        }
        else -> Text(
            "All set · new photos are added automatically",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
            modifier = modifier,
        )
    }
    if (!s.fullAccess) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Text("Recall can only see the photos you picked.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = askPermission) { Text("Allow all") }
        }
    }
}

@Composable
private fun StatusCard(modifier: Modifier, content: @Composable () -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(14.dp)) { content() }
    }
}

// ---------------------------------------------------------------------------- grid

private sealed interface GridEntry
private data class Header(val key: String, val title: String, val count: Int) : GridEntry
private data class Cell(val photo: Photo, val index: Int) : GridEntry

/** Splits photos (already newest first) into month sections. */
private fun sections(photos: List<Photo>): List<GridEntry> {
    val out = ArrayList<GridEntry>(photos.size + 64)
    var current = ""
    var headerAt = -1
    var count = 0
    val zone = ZoneId.systemDefault()
    photos.forEachIndexed { i, p ->
        val month = monthFormat.format(Instant.ofEpochMilli(p.dateTaken).atZone(zone))
        if (month != current) {
            if (headerAt >= 0) out[headerAt] = (out[headerAt] as Header).copy(count = count)
            current = month; count = 0
            headerAt = out.size
            out.add(Header("h$i", month, 0))
        }
        out.add(Cell(p, i))
        count++
    }
    if (headerAt >= 0) out[headerAt] = (out[headerAt] as Header).copy(count = count)
    return out
}

@Composable
private fun PhotoGrid(s: UiState, vm: MainViewModel, base: List<Photo>, markUnready: Boolean, modifier: Modifier) {
    // Search results live on the Search tab; All photos always shows the gallery
    val results = if (markUnready) null else s.results
    val visible = base
    val shown: List<Photo>? = remember(results, s.shownCount) { results?.take(s.shownCount)?.map { it.photo } }
    val entries: List<GridEntry> = remember(shown, visible, s.resultsByDate) {
        when {
            shown == null -> sections(visible)
            s.resultsByDate -> sections(shown)
            else -> shown.mapIndexed { i, p -> Cell(p, i) }
        }
    }
    val viewList: List<Photo> = shown ?: visible

    LazyVerticalGrid(
        columns = GridCells.Adaptive(100.dp),
        modifier = modifier,
        contentPadding = PaddingValues(start = 2.dp, end = 2.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (s.searching || s.preparingPhoto) {
            item(span = { GridItemSpan(maxLineSpan) }) { LinearProgressIndicator(Modifier.fillMaxWidth().padding(8.dp)) }
        }
        if (results == null && !markUnready) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    if (visible.isEmpty()) {
                        if (s.indexedCount == 0) "No photos are searchable yet. As Recall gets them ready, they show up here."
                        else "No searchable photos match these filters."
                    } else "${nf.format(visible.size)} searchable photos",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp),
                )
            }
        }
        if (results != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(
                        if (s.resultsByDate) s.resultsTitle else "Best matches for ${s.resultsTitle}",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        when {
                            results.isEmpty() -> ""
                            s.resultsByDate -> "${nf.format(results.size)} photos"
                            else -> "Showing the ${s.shownCount} closest" + if (s.searchMillis > 0) " · found in ${s.searchMillis} ms" else ""
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (results.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        when {
                            s.indexedCount == 0 -> "No photos are ready yet. Recall is still preparing them; try again in a little while."
                            s.filters.activeCount > 0 -> "Nothing matches with these filters. Try removing one."
                            else -> "No matches."
                        },
                        modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        itemsIndexed(
            entries,
            key = { _, e -> when (e) { is Header -> e.key; is Cell -> e.photo.id } },
            span = { _, e -> if (e is Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
            contentType = { _, e -> if (e is Header) 0 else 1 },
        ) { _, e ->
            when (e) {
                is Header -> Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
                    Text(e.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(nf.format(e.count), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is Cell -> Thumb(
                    e.photo,
                    best = results != null && !s.resultsByDate && e.index == 0,
                    unready = markUnready && e.photo.id !in s.indexedIds,
                ) { vm.openViewer(viewList, e.index) }
            }
        }
        if (results != null && s.shownCount < results.size) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedButton(onClick = vm::showMore, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(if (s.resultsByDate) "Show more photos" else "Show more results")
                }
            }
        }
    }
}

@Composable
private fun Thumb(photo: Photo, best: Boolean, unready: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick)) {
        AsyncImage(model = photo.uri, contentDescription = photo.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (best) {
            Text(
                "Best match",
                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        if (unready) {
            Box(
                Modifier.align(Alignment.TopStart).padding(5.dp).size(9.dp).clip(CircleShape)
                    .background(Color(0xCCFFFFFF)).padding(1.5.dp).clip(CircleShape).background(Color(0xFF8A8FA8)),
            )
        }
        if (photo.favorite) {
            Icon(
                Icons.Filled.Favorite, contentDescription = "Favourite", tint = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(14.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------- filters panel

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FiltersSheet(s: UiState, vm: MainViewModel) {
    val f = s.filters
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filters", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (f.activeCount > 0) TextButton(onClick = vm::clearFilters) { Text("Clear all") }
            }
            Text(
                "Filters apply to your gallery and to search results.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            // When
            Text("When", fontWeight = FontWeight.Bold)
            if (s.maxYear > s.minYear) {
                val fromYear = f.from?.year?.coerceIn(s.minYear, s.maxYear) ?: s.minYear
                val toYear = f.until?.minusDays(1)?.year?.coerceIn(s.minYear, s.maxYear) ?: s.maxYear
                var range by remember(fromYear, toYear) { mutableStateOf(fromYear.toFloat()..toYear.toFloat()) }
                val a = range.start.roundToInt(); val b = range.endInclusive.roundToInt()
                Text(
                    if (a <= s.minYear && b >= s.maxYear) "Any time" else if (a == b) "$a" else "$a – $b",
                    style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary,
                )
                RangeSlider(
                    value = range,
                    onValueChange = { range = it },
                    valueRange = s.minYear.toFloat()..s.maxYear.toFloat(),
                    steps = (s.maxYear - s.minYear - 1).coerceAtLeast(0),
                    onValueChangeFinished = { vm.setYears(range.start.roundToInt(), range.endInclusive.roundToInt()) },
                )
                Row {
                    Text("${s.minYear}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                    Text("${s.maxYear}", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(8.dp))
            val today = LocalDate.now()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = f.from == null, onClick = { vm.setFilters(f.clearDate()) }, label = { Text("Any time") })
                FilterChip(
                    selected = f.dateLabel == "Last 30 days",
                    onClick = { vm.setFilters(f.copy(from = today.minusDays(30), until = today.plusDays(1), dateLabel = "Last 30 days", dateFromQuery = false)) },
                    label = { Text("Last 30 days") },
                )
                FilterChip(
                    selected = f.dateLabel == "${today.year}",
                    onClick = { vm.setYears(today.year, today.year) },
                    label = { Text("This year") },
                )
                FilterChip(
                    selected = f.dateLabel == "${today.year - 1}",
                    onClick = { vm.setYears(today.year - 1, today.year - 1) },
                    label = { Text("Last year") },
                )
            }
            Spacer(Modifier.height(20.dp))

            // Kind
            Text("Type", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val kinds = Kind.entries
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                kinds.forEachIndexed { i, k ->
                    SegmentedButton(
                        selected = f.kind == k, onClick = { vm.setFilters(f.copy(kind = k)) },
                        shape = SegmentedButtonDefaults.itemShape(i, kinds.size),
                    ) { Text(k.label, maxLines = 1) }
                }
            }
            Spacer(Modifier.height(20.dp))

            // Shape
            Text("Shape", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val shapes = Shape.entries
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                shapes.forEachIndexed { i, sh ->
                    SegmentedButton(
                        selected = f.shape == sh, onClick = { vm.setFilters(f.copy(shape = sh)) },
                        shape = SegmentedButtonDefaults.itemShape(i, shapes.size),
                    ) { Text(if (sh == Shape.ANY) "Any" else sh.label, maxLines = 1) }
                }
            }
            Spacer(Modifier.height(16.dp))

            // Favourites
            if (Build.VERSION.SDK_INT >= 30) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Favourites only", fontWeight = FontWeight.Bold)
                        Text(
                            "Photos you starred or hearted in your gallery app.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = f.favoritesOnly, onCheckedChange = { vm.setFilters(f.copy(favoritesOnly = it)) })
                }
                Spacer(Modifier.height(16.dp))
            }

            // Folder
            Text("Folder", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = f.folder == null, onClick = { vm.setFilters(f.copy(folder = null)) }, label = { Text("All folders") })
                s.folders.forEach { folder ->
                    FilterChip(
                        selected = f.folder == folder.id,
                        onClick = { vm.setFilters(f.copy(folder = if (f.folder == folder.id) null else folder.id)) },
                        label = { Text("${folder.name} · ${nf.format(folder.count)}") },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------- speed-up mode

/**
 * Keeps Recall on screen (dimmed almost to black) while the one-time setup runs. Android gives the app in front the
 * fastest CPU cores; with the screen off, background work is moved to the slow, power-saving ones.
 */
@Composable
private fun SpeedUpScreen(s: UiState, vm: MainViewModel) {
    val activity = LocalContext.current as? android.app.Activity
    androidx.compose.runtime.DisposableEffect(Unit) {
        val window = activity?.window
        val before = window?.attributes?.screenBrightness
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window?.attributes = window?.attributes?.apply { screenBrightness = 0.02f }
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.attributes = window?.attributes?.apply {
                screenBrightness = before ?: android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }
    // Leave automatically when there's nothing left to do
    LaunchedEffect(s.indexing) { if (!s.indexing) { kotlinx.coroutines.delay(3000); if (!vm.state.value.indexing) vm.setSpeedUp(false) } }

    Box(
        Modifier.fillMaxSize().background(Color.Black).clickable { vm.setSpeedUp(false) }
            .windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (s.indexTotal > 0) "${(s.indexDone * 100L / s.indexTotal)}%" else "…",
                color = Color(0xFF8A7A55), fontSize = 64.sp, fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (s.indexTotal > 0) "${nf.format(s.indexDone)} of ${nf.format(s.indexTotal)} photos" else "Starting…",
                color = Color(0xFF6E7186), style = MaterialTheme.typography.titleMedium,
            )
            if (s.msPerPhoto > 0) {
                Text("${friendlyDuration(s.etaSec)} left", color = Color(0xFF6E7186), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(32.dp))
            Text(
                "Running at full speed. Leave Recall open, ideally on the charger.\nTap anywhere to go back.",
                color = Color(0xFF4F5266), style = MaterialTheme.typography.bodySmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

// ---------------------------------------------------------------------------- viewer

@Composable
private fun Viewer(v: ViewerState, s: UiState, vm: MainViewModel) {
    val context = LocalContext.current
    val pager = rememberPagerState(initialPage = v.start.coerceIn(0, (v.photos.size - 1).coerceAtLeast(0))) { v.photos.size }
    val photo = v.photos.getOrNull(pager.currentPage) ?: return
    var chrome by remember { mutableStateOf(true) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { v.photos[it].id }) { page ->
            ZoomableImage(v.photos[page], onTap = { chrome = !chrome })
        }

        if (chrome) {
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        DateUtils.formatDateTime(
                            context, photo.dateTaken,
                            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_SHOW_TIME,
                        ),
                        color = Color.White, fontWeight = FontWeight.Bold,
                    )
                    Text(
                        listOf(photo.bucket, "${pager.currentPage + 1} of ${v.photos.size}").filter { it.isNotEmpty() }.joinToString(" · "),
                        color = Color(0xFFBBBBBB), style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(onClick = vm::closeViewer, modifier = Modifier.clip(CircleShape).background(Color(0x55000000))) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(onClick = { vm.findSimilar(photo) }, enabled = s.modelReady && !s.preparingPhoto, modifier = Modifier.weight(1f)) {
                    Text(
                        when {
                            s.preparingPhoto -> "Looking at this photo…"
                            photo.id in s.indexedIds -> "Find similar photos"
                            else -> "Find similar (takes a moment)"
                        },
                    )
                }
                FilledTonalButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "image/*"
                        putExtra(Intent.EXTRA_STREAM, photo.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(send, "Share photo"))
                }) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share")
                }
            }
        }
    }
}

/** Pinch or double-tap to zoom; one-finger swipes pass through to the pager unless zoomed in. */
@Composable
private fun ZoomableImage(photo: Photo, onTap: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(photo.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tap ->
                        if (scale > 1f) { scale = 1f; offset = Offset.Zero }
                        else { scale = 2.5f; offset = (Offset(size.width / 2f, size.height / 2f) - tap) * 1.5f }
                    },
                )
            }
            .pointerInput(photo.id) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val multi = event.changes.count { it.pressed } > 1
                        if (multi || scale > 1f) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 6f)
                            offset = if (scale > 1f) offset + event.calculatePan() else Offset.Zero
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        AsyncImage(
            model = photo.uri,
            contentDescription = photo.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale
                translationX = offset.x; translationY = offset.y
            },
        )
    }
}

// ---------------------------------------------------------------------------- settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(s: UiState, vm: MainViewModel) {
    val allFolders = s.scopeFolders == null
    var confirmDelete by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
        item {
            Text("Which photos to prepare", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "${nf.format(s.selectedCount)} photos selected · ${nf.format(s.selectedIndexed)} ready to search",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Text("How far back", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateRange.choices.forEach { r ->
                        FilterChip(selected = s.dateRange == r, onClick = { vm.setDateRange(r) }, label = { Text(r.label) })
                    }
                }
            Spacer(Modifier.height(16.dp))
            SettingSwitch("Oldest photos first", "Off: your newest photos are ready first.", s.oldestFirst, vm::setOldestFirst)
            SettingSwitch("Only while charging", "Saves battery. Preparing takes longer overall.", s.chargingOnly, vm::setChargingOnly)
            Spacer(Modifier.height(8.dp))
            Text("Detail", fontWeight = FontWeight.Bold)
            Text(
                "Thorough notices small things like text and background objects, but takes about 4× longer per photo than Quick.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            val options = listOf(70 to "Quick", 140 to "Balanced", 280 to "Thorough")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { i, (value, label) ->
                    SegmentedButton(
                        selected = s.photoDetail == value, onClick = { vm.setPhotoDetail(value) },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    ) { Text(label) }
                }
            }
            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Folders", fontWeight = FontWeight.Bold)
                    Text(
                        if (allFolders) "Every folder, including new ones" else "${s.scopeFolders?.size ?: 0} of ${s.folders.size} folders",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("All", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(8.dp))
                Switch(checked = allFolders, onCheckedChange = vm::setAllFolders)
            }
            Spacer(Modifier.height(4.dp))
        }
        items(s.folders, key = { it.id }) { f ->
            FolderRow(f, allFolders || s.scopeFolders?.contains(f.id) == true) { vm.toggleFolder(f.id) }
        }
        item {
            Spacer(Modifier.height(12.dp))
            Text(
                "Changes apply when you close this panel. Photos you remove stay searchable until you start over.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = vm::reindexAll, enabled = s.modelReady) { Text("Start over and prepare again") }
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Text("Privacy & permissions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            PrivacyPromises()
            Spacer(Modifier.height(8.dp))
            PermissionList()
            Spacer(Modifier.height(16.dp))
            if (!confirmDelete) {
                OutlinedButton(onClick = { confirmDelete = true }, enabled = s.modelReady || s.indexedCount > 0) {
                    Text("Remove search engine and index")
                }
            } else {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "This frees about 300 MB. Your photos stay exactly as they are. You can download the engine again later.",
                            color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium,
                        )
                        Row {
                            TextButton(onClick = { confirmDelete = false }) { Text("Keep") }
                            TextButton(onClick = { confirmDelete = false; vm.deleteEverything() }) { Text("Remove") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Search engine: EmbeddingGemma 2 by Google, running on this phone with ${RecallApp.performanceCores()} fast CPU cores. " +
                    "${nf.format(s.indexedCount)} photos ready in total.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingSwitch(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
