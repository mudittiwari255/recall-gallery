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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    "Receipts and bills", "Screenshots of chats", "Documents and IDs", "Food", "Sunset", "Selfies",
    "Pets", "Whiteboard notes", "Mountains", "Beach", "Birthday cake", "Cars",
)

// ---------------------------------------------------------------------------- root

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecallScreen(vm: MainViewModel) {
    val s by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showSettings by remember { mutableStateOf(false) }

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
    BackHandler(enabled = s.results != null && s.viewer == null) { vm.clearResults() }
    BackHandler(enabled = s.viewer != null) { vm.closeViewer() }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = MaterialTheme.colorScheme.background,
        ) { pad ->
            if (!s.hasPermission) {
                Onboarding(Modifier.padding(pad), ::askPermission)
                return@Scaffold
            }
            Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
                SearchHeader(s, vm, onSettings = { showSettings = true })
                ChipRows(s, vm)
                StatusStrip(s, vm, ::askPermission)
                PhotoGrid(s, vm, Modifier.weight(1f))
            }
        }
        s.viewer?.let { Viewer(it, s, vm) }
    }

    if (showSettings) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSettings = false; vm.applyScope() },
            sheetState = sheet,
        ) { SettingsSheet(s, vm) }
    }
}

@Composable
private fun Onboarding(modifier: Modifier, onAllow: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Recall", fontSize = 44.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(
            "Find any photo by describing it: “receipt from Croma”, “dog on the beach”, “whiteboard notes”.",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Everything runs on this phone. Your photos are never uploaded anywhere. The only download is the search model (about 285 MB, once).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onAllow, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Allow photo access") }
    }
}

// ---------------------------------------------------------------------------- header + chips

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
            placeholder = { Text("Describe a photo…") },
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
private fun ChipRows(s: UiState, vm: MainViewModel) {
    val focus = LocalFocusManager.current
    if (s.results == null && s.query.isEmpty() && s.modelReady && s.indexedCount > 0) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(suggestions) { text ->
                SuggestionChip(onClick = { focus.clearFocus(); vm.search(text) }, label = { Text(text) })
            }
        }
    }
    if (s.folders.size > 1) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(selected = s.folderFilter == null, onClick = { vm.setFolderFilter(null) }, label = { Text("All photos") })
            }
            items(s.folders, key = { it.id }) { f ->
                FilterChip(
                    selected = s.folderFilter == f.id,
                    onClick = { vm.setFolderFilter(if (s.folderFilter == f.id) null else f.id) },
                    label = { Text("${f.name} · ${nf.format(f.count)}") },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------- status

@Composable
private fun StatusStrip(s: UiState, vm: MainViewModel, askPermission: () -> Unit) {
    val modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
    when {
        !s.modelReady && s.downloading -> StatusCard(modifier) {
            val pct = if (s.downloadTotal > 0) s.downloadDone.toFloat() / s.downloadTotal else 0f
            Text("Downloading search model", fontWeight = FontWeight.Bold)
            Text(
                if (s.downloadTotal > 0) "${s.downloadDone / 1_000_000} of ${s.downloadTotal / 1_000_000} MB" else "Starting…",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            if (s.downloadTotal > 0) LinearProgressIndicator(progress = { pct }, modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        !s.modelReady -> StatusCard(modifier) {
            Text("Turn on search", fontWeight = FontWeight.Bold)
            Text(
                if (s.downloadFailed) "The download stopped. Check your connection and try again."
                else "Downloads the search model once (about 285 MB). Wi-Fi recommended.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = vm::downloadModel) { Text(if (s.downloadFailed) "Retry download" else "Download model") }
        }
        s.indexing -> StatusCard(modifier) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (s.indexTotal > 0) "Indexing ${nf.format(s.indexDone)} of ${nf.format(s.indexTotal)}" else "Indexing photos",
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        when {
                            s.msPerPhoto <= 0 -> "Measuring speed…"
                            else -> "%.1f s per photo · %s left".format(s.msPerPhoto / 1000.0, formatEta(s.etaSec))
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = vm::stopIndexing) { Text("Pause") }
            }
            Spacer(Modifier.height(6.dp))
            if (s.indexTotal > 0) LinearProgressIndicator(progress = { s.indexDone.toFloat() / s.indexTotal }, modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Text(
                "Search already works on indexed photos. Keep the phone charging for the fastest progress.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall,
            )
        }
        else -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            val pending = s.selectedCount - s.selectedIndexed
            Text(
                if (pending > 0) "${nf.format(s.selectedIndexed)} of ${nf.format(s.selectedCount)} selected photos searchable"
                else "${nf.format(s.indexedCount)} photos searchable",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            if (pending > 0) TextButton(onClick = vm::startIndexing) { Text("Index now") }
        }
    }
    if (!s.fullAccess) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Text("Recall can only see the photos you selected.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
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
private data class Cell(val photo: Photo, val index: Int, val score: Float?) : GridEntry

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
        out.add(Cell(p, i, null))
        count++
    }
    if (headerAt >= 0) out[headerAt] = (out[headerAt] as Header).copy(count = count)
    return out
}

@Composable
private fun PhotoGrid(s: UiState, vm: MainViewModel, modifier: Modifier) {
    val results = s.results
    val visible = s.visiblePhotos
    val entries: List<GridEntry> = remember(results, visible) {
        results?.mapIndexed { i, h -> Cell(h.photo, i, h.score) } ?: sections(visible)
    }
    val viewList: List<Photo> = remember(results, visible) { results?.map { it.photo } ?: visible }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(100.dp),
        modifier = modifier,
        contentPadding = PaddingValues(start = 2.dp, end = 2.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (s.searching) {
            item(span = { GridItemSpan(maxLineSpan) }) { LinearProgressIndicator(Modifier.fillMaxWidth().padding(8.dp)) }
        }
        if (results != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
                    Text(
                        s.resultsTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    Text(
                        "best ${results.size} · ${s.searchMillis} ms",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (results.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        if (s.indexedCount == 0) "No photos are indexed yet. Indexing runs in the background; try again in a bit."
                        else "No matches in this folder.",
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
                is Cell -> Thumb(e.photo, e.score) { vm.openViewer(viewList, e.index) }
            }
        }
    }
}

@Composable
private fun Thumb(photo: Photo, score: Float?, onClick: () -> Unit) {
    Box(Modifier.aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick)) {
        AsyncImage(model = photo.uri, contentDescription = photo.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (score != null) {
            Text(
                "%.2f".format(score),
                fontSize = 10.sp,
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                    .background(Color(0x99000000), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp),
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
            // Top bar: date + folder, close. Padded below the status bar.
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

            // Bottom actions, padded above the navigation bar so they are always reachable.
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(onClick = { vm.findSimilar(photo) }, enabled = s.modelReady, modifier = Modifier.weight(1f)) {
                    Text("Find similar")
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
    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
    ) {
        item {
            Text("What to index", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "${nf.format(s.selectedCount)} photos selected · ${nf.format(s.selectedIndexed)} indexed",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Text("Time range", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val ranges = DateRange.entries
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ranges.forEachIndexed { i, r ->
                    SegmentedButton(
                        selected = s.dateRange == r,
                        onClick = { vm.setDateRange(r) },
                        shape = SegmentedButtonDefaults.itemShape(i, ranges.size),
                    ) { Text(r.label, maxLines = 1) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Oldest photos first", fontWeight = FontWeight.Bold)
                    Text(
                        "Off: your newest photos become searchable first.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = s.oldestFirst, onCheckedChange = vm::setOldestFirst)
            }
            Spacer(Modifier.height(20.dp))
            Text("Photo detail", fontWeight = FontWeight.Bold)
            Text(
                "Detailed finds small things like text and background objects, but takes about 4× longer per photo than Fast.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            val options = listOf(70 to "Fast", 140 to "Balanced", 280 to "Detailed")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { i, (value, label) ->
                    SegmentedButton(
                        selected = s.photoDetail == value,
                        onClick = { vm.setPhotoDetail(value) },
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
            val checked = allFolders || s.scopeFolders?.contains(f.id) == true
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .clickable { vm.toggleFolder(f.id) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = f.cover, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${nf.format(f.count)} photos",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Checkbox(checked = checked, onCheckedChange = { vm.toggleFolder(f.id) })
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Text(
                "Changes apply when you close this panel. Photos you deselect stay searchable until you re-index.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = vm::reindexAll, enabled = s.modelReady) { Text("Re-index everything") }
            Spacer(Modifier.height(16.dp))
            Text(
                "Model: EmbeddingGemma 2 (4-bit) on this phone, using ${RecallApp.performanceCores()} fast CPU cores. " +
                    "${nf.format(s.indexedCount)} photos indexed in total.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
