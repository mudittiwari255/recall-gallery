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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import coil3.compose.AsyncImage
import java.text.NumberFormat

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { RecallTheme { RecallScreen(vm) } }
    }
}

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
    outline = Color(0xFF30344A), outlineVariant = Color(0xFF30344A),
)

private val LightColors: ColorScheme = lightColorScheme(
    primary = AmberDeep, onPrimary = Color.White,
    primaryContainer = Color(0xFFF6E7C8), onPrimaryContainer = Color(0xFF3A2A06),
    secondaryContainer = Color(0xFFF6E7C8), onSecondaryContainer = Color(0xFF3A2A06),
    background = Color(0xFFF1F2F7), onBackground = Color(0xFF1B1E2E),
    surface = Color(0xFFF1F2F7), onSurface = Color(0xFF1B1E2E),
    surfaceVariant = Color(0xFFE6E8F0), onSurfaceVariant = Color(0xFF5B6077),
    surfaceContainer = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFFFFFFF),
    outline = Color(0xFFD6D9E4), outlineVariant = Color(0xFFD6D9E4),
)

@Composable
fun RecallTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}

private val nf: NumberFormat = NumberFormat.getIntegerInstance()

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
    BackHandler(enabled = s.results != null && s.viewing == null) { vm.clearResults() }

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
            StatusStrip(s, vm, ::askPermission)
            PhotoGrid(s, vm, Modifier.weight(1f))
        }
    }

    s.viewing?.let { Viewer(it, s, vm) }
    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }) { SettingsSheet(s, vm) }
    }
}

@Composable
private fun Onboarding(modifier: Modifier, onAllow: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Recall", fontSize = 40.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
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

@Composable
private fun SearchHeader(s: UiState, vm: MainViewModel, onSettings: () -> Unit) {
    val focus = LocalFocusManager.current
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
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
            ),
        )
        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
    }
}

@Composable
private fun StatusStrip(s: UiState, vm: MainViewModel, askPermission: () -> Unit) {
    val modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp)
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
                    Text("Indexing photos", fontWeight = FontWeight.Bold)
                    Text(
                        if (s.indexTotal > 0) "${nf.format(s.indexDone)} of ${nf.format(s.indexTotal)} · search works on what's done"
                        else "Preparing…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = vm::stopIndexing) { Text("Pause") }
            }
            Spacer(Modifier.height(6.dp))
            if (s.indexTotal > 0) LinearProgressIndicator(progress = { s.indexDone.toFloat() / s.indexTotal }, modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        else -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            val pending = s.photos.size - s.indexedCount
            Text(
                "${nf.format(s.indexedCount)} of ${nf.format(s.photos.size)} photos searchable",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            if (pending > 0) TextButton(onClick = vm::startIndexing) { Text("Index now") }
        }
    }
    if (!s.fullAccess) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Recall can only see the photos you selected.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
            )
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

@Composable
private fun PhotoGrid(s: UiState, vm: MainViewModel, modifier: Modifier) {
    val results = s.results
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        modifier = modifier,
        contentPadding = PaddingValues(start = 2.dp, end = 2.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (results != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        s.resultsTitle, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (s.searchMillis > 0) {
                        Text("${s.searchMillis} ms", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (results.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        if (s.indexedCount == 0) "No photos are indexed yet. Indexing runs in the background; try again in a bit."
                        else "No matches.",
                        modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(results, key = { it.photo.id }) { hit -> Thumb(hit.photo, hit.score) { vm.view(hit.photo) } }
        } else {
            if (s.searching) item(span = { GridItemSpan(maxLineSpan) }) { LinearProgressIndicator(Modifier.fillMaxWidth().padding(8.dp)) }
            items(s.photos, key = { it.id }) { p -> Thumb(p, null) { vm.view(p) } }
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

@Composable
private fun Viewer(photo: Photo, s: UiState, vm: MainViewModel) {
    val context = LocalContext.current
    Dialog(onDismissRequest = { vm.view(null) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(model = photo.uri, contentDescription = photo.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xCC000000))
                    .navigationBarsPadding().padding(16.dp),
            ) {
                Text(photo.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                Text(
                    DateUtils.formatDateTime(context, photo.dateTaken, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_SHOW_TIME) +
                        if (photo.bucket.isNotEmpty()) " · ${photo.bucket}" else "",
                    color = Color(0xFFBBBBBB), style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.findSimilar(photo) }, enabled = s.modelReady) { Text("Find similar") }
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
            IconButton(
                onClick = { vm.view(null) },
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)
                    .clip(RoundedCornerShape(50)).background(Color(0x66000000)),
            ) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(s: UiState, vm: MainViewModel) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
        Text("Photo detail", fontWeight = FontWeight.Bold)
        Text(
            "More detail finds small things (text, objects in the background) but indexes slower.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
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
        Spacer(Modifier.height(8.dp))
        Text(
            "Applies to photos indexed from now on. Re-index to apply it to everything.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = vm::reindexAll, enabled = s.modelReady) { Text("Re-index all photos") }
        Spacer(Modifier.height(20.dp))
        Text(
            "Model: EmbeddingGemma 2 (4-bit), running on this phone. ${nf.format(s.indexedCount)} photos indexed.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
