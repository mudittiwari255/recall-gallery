@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.recall

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import java.text.NumberFormat

private val count: NumberFormat = NumberFormat.getIntegerInstance()

/** The five-step welcome: what Recall does, privacy, permissions, which photos, and the one download. */
@Composable
fun Onboarding(s: UiState, vm: MainViewModel, modifier: Modifier, askPermission: () -> Unit) {
    val step = s.onboardingStep ?: return
    Column(modifier.fillMaxSize()) {
        // Progress dots and back
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(72.dp)) {
                if (step > 0) TextButton(onClick = vm::backStep) { Text("Back") }
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                repeat(5) { i ->
                    Box(
                        Modifier.padding(horizontal = 4.dp).size(if (i == step) 10.dp else 7.dp).clip(CircleShape)
                            .background(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
            Spacer(Modifier.width(72.dp))
        }
        Box(Modifier.weight(1f)) {
            when (step) {
                0 -> WelcomeStep(vm)
                1 -> PrivacyStep(vm)
                2 -> PermissionStep(s, askPermission)
                3 -> FoldersStep(s, vm)
                else -> DownloadStep(s, vm)
            }
        }
    }
}

@Composable
private fun StepPage(
    title: String,
    subtitle: String?,
    primary: String,
    onPrimary: () -> Unit,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(16.dp))
            Text(title, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black)
            if (subtitle != null) {
                Spacer(Modifier.height(10.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
            content()
            Spacer(Modifier.height(24.dp))
        }
        Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text(primary, fontSize = 16.sp) }
        if (secondary != null) {
            TextButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth()) { Text(secondary) }
        } else {
            Spacer(Modifier.height(16.dp))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun WelcomeStep(vm: MainViewModel) {
    StepPage(
        title = "Find any photo by describing it",
        subtitle = "Type what you remember, the way you'd say it. Recall finds the photo, even if it has no name or tags.",
        primary = "Get started",
        onPrimary = vm::nextStep,
    ) {
        Text("Try searches like", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        listOf(
            "bill from the restaurant",
            "dog on the beach 2023",
            "whiteboard notes last month",
            "my passport",
            "शादी की तस्वीरें",
        ).forEach { example ->
            Surface(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(example, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Works in English, Hindi and 100+ other languages.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun PromiseRow(icon: ImageVector, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PrivacyPromises() {
    PromiseRow(Icons.Filled.Lock, "Your photos never leave this phone", "Recall looks at your photos right here, on the phone. Nothing is uploaded. No account, no cloud.")
    PromiseRow(Icons.Filled.Info, "Internet is used for one thing only", "Downloading Recall's search engine once (about 285 MB). After that, search works even in airplane mode.")
    PromiseRow(Icons.Filled.Done, "No ads, no tracking", "Recall doesn't collect usage data and doesn't share anything with anyone.")
    PromiseRow(Icons.Filled.Delete, "You're in control", "Remove the search engine and everything Recall learned with one tap in Settings. Your photos are never changed.")
}

@Composable
private fun PrivacyStep(vm: MainViewModel) {
    StepPage(
        title = "Private by design",
        subtitle = "Most photo search sends your pictures to a server. Recall does all the work on your phone instead.",
        primary = "Sounds good",
        onPrimary = vm::nextStep,
    ) { PrivacyPromises() }
}

@Composable
fun PermissionCard(icon: ImageVector, title: String, tag: String, body: String) {
    Surface(Modifier.fillMaxWidth().padding(vertical = 6.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(tag, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun PermissionList() {
    PermissionCard(
        Icons.Filled.Search, "Photos", "Needed",
        "To show your gallery and understand what's in each photo so you can search it. Recall can only look: it can't edit, delete or upload your photos.",
    )
    PermissionCard(
        Icons.Filled.Notifications, "Notifications", "Optional",
        "To show progress while your photos are being prepared in the background. It's fine to say no.",
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Recall also uses the internet (only to download the search engine) and keeps working in the background " +
            "(so preparing photos continues when the app is closed). Android doesn't ask you about these.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PermissionStep(s: UiState, askPermission: () -> Unit) {
    val context = LocalContext.current
    StepPage(
        title = "What Recall needs, and why",
        subtitle = "Android will ask you next. Here's what each permission is for.",
        primary = if (s.permissionDenied) "Try again" else "Allow access",
        onPrimary = askPermission,
        secondary = if (s.permissionDenied) "Open phone settings" else null,
        onSecondary = {
            context.startActivity(
                Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
    ) {
        PermissionList()
        if (s.permissionDenied) {
            Spacer(Modifier.height(16.dp))
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer) {
                Text(
                    "Recall can't work without access to your photos. If Android doesn't ask again, open phone settings → Permissions → Photos and videos.",
                    modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** "about 25 minutes", "about 3 hours". */
fun friendlyDuration(seconds: Long): String = when {
    seconds < 90 -> "about a minute"
    seconds < 3600 -> "about ${(seconds + 30) / 60} minutes"
    seconds < 2 * 3600 -> "about an hour"
    else -> "about ${(seconds + 1800) / 3600} hours"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoldersStep(s: UiState, vm: MainViewModel) {
    val all = s.scopeFolders == null
    val perPhotoMs = if (s.msPerPhoto > 0) s.msPerPhoto else 1500L
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 16.dp)) {
            item {
                Text("Which photos should Recall search?", fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Fewer photos means they're ready sooner. You can change this anytime in Settings.",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.padding(16.dp)) {
                        Text("${count.format(s.selectedCount)} photos selected", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            "Ready in ${friendlyDuration(s.selectedCount * perPhotoMs / 1000)}, roughly. It keeps going in the background, and search works on photos as soon as they're ready.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text("How far back", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                val ranges = listOf(DateRange.ALL, DateRange.Y5, DateRange.Y3, DateRange.Y1)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ranges.forEachIndexed { i, r ->
                        SegmentedButton(
                            selected = s.dateRange == r, onClick = { vm.setDateRange(r) },
                            shape = SegmentedButtonDefaults.itemShape(i, ranges.size),
                        ) { Text(if (r == DateRange.ALL) "All" else r.label, maxLines = 1) }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("All folders", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Switch(checked = all, onCheckedChange = vm::setAllFolders)
                }
                Spacer(Modifier.height(4.dp))
            }
            items(s.folders, key = { it.id }) { f -> FolderRow(f, all || s.scopeFolders?.contains(f.id) == true) { vm.toggleFolder(f.id) } }
        }
        Button(
            onClick = vm::nextStep, enabled = s.selectedCount > 0,
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) { Text("Continue", fontSize = 16.sp) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun FolderRow(f: Folder, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onToggle).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = f.cover, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text("${count.format(f.count)} photos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun DownloadStep(s: UiState, vm: MainViewModel) {
    StepPage(
        title = "One last download",
        subtitle = "Recall's search engine is an AI model made by Google (EmbeddingGemma 2). It runs entirely on your phone.",
        primary = if (s.modelReady) "Start using Recall" else "Download now",
        onPrimary = { vm.finishOnboarding(download = true) },
        secondary = if (s.modelReady) null else "Later",
        onSecondary = { vm.finishOnboarding(download = false) },
    ) {
        PromiseRow(Icons.Filled.Info, "About 285 MB, once", "Use Wi-Fi if you can. It downloads in the background, so you can keep using your phone.")
        PromiseRow(Icons.Filled.Search, "Then Recall prepares your photos", "It looks at each selected photo once. Search works on photos as soon as they're ready.")
        PromiseRow(Icons.Filled.Lock, "Still private", "The download is the only time Recall uses the internet. Nothing about your photos is sent.")
        Spacer(Modifier.height(8.dp))
        Text(
            "Tip: plug in your charger for the first run. It's faster and easier on the battery.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
