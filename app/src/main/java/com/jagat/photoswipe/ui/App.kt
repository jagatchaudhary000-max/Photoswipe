package com.jagat.photoswipe.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jagat.photoswipe.MediaItem
import com.jagat.photoswipe.SwipeViewModel

private fun requiredPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
    )
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun hasMediaAccess(context: Context): Boolean =
    requiredPermissions().any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun App(vm: SwipeViewModel) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    var granted by remember { mutableStateOf(hasMediaAccess(context)) }
    var tab by rememberSaveable { mutableStateOf(AppTab.SWIPE) }

    // Re-check access and refresh the gallery every time the app comes to the front.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = hasMediaAccess(context)
        if (granted) vm.load()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted = hasMediaAccess(context) }

    // Android shows its own "Allow Photo Swipe to delete N items?" dialog.
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            vm.onDeleted()
            tab = AppTab.SWIPE
            Toast.makeText(context, "Done — space freed", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestRemoval(permanently: Boolean) {
        val uris = state.pendingItems.map { it.uri }
        if (uris.isEmpty()) return
        val pendingIntent = if (permanently) {
            MediaStore.createDeleteRequest(context.contentResolver, uris)
        } else {
            MediaStore.createTrashRequest(context.contentResolver, uris, true)
        }
        deleteLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
    }

    fun openVideo(item: MediaItem) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(item.uri, "video/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No video player found", Toast.LENGTH_SHORT).show()
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (!granted) {
            PermissionScreen(
                onAllow = { permissionLauncher.launch(requiredPermissions()) },
                onOpenSettings = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        )
                    )
                },
            )
            return@Surface
        }

        BackHandler(enabled = tab != AppTab.SWIPE) { tab = AppTab.SWIPE }

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                NavigationBar {
                    AppTab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = {
                                if (t == AppTab.REVIEW && state.pending.isNotEmpty()) {
                                    BadgedBox(badge = { Badge { Text("${state.pending.size}") } }) {
                                        Icon(t.icon, null)
                                    }
                                } else {
                                    Icon(t.icon, null)
                                }
                            },
                            label = { Text(t.label) },
                        )
                    }
                }
            },
        ) { inner ->
            Box(Modifier.padding(inner).consumeWindowInsets(inner)) {
                when (tab) {
                    AppTab.SWIPE -> SwipeScreen(
                        state = state,
                        onKeep = vm::keep,
                        onDelete = vm::markForDelete,
                        onUndo = vm::undo,
                        onCategory = vm::setCategory,
                        onOpenReview = { tab = AppTab.REVIEW },
                        onOpenVideo = ::openVideo,
                        onResetKept = vm::resetKept,
                        onSort = vm::setSort,
                    )
                    AppTab.GALLERY -> GalleryScreen(
                        state = state,
                        onCategory = vm::setCategory,
                        onSort = vm::setSort,
                        onMarkDelete = vm::markManyForDelete,
                        onKeep = vm::keepMany,
                        onClear = vm::clearMany,
                        onOpenVideo = ::openVideo,
                    )
                    AppTab.CLEANUP -> CleanupScreen(
                        state = state,
                        onRescan = vm::startScan,
                        onMarkDelete = vm::markManyForDelete,
                    )
                    AppTab.REVIEW -> ReviewScreen(
                        state = state,
                        onBack = { tab = AppTab.SWIPE },
                        onRescue = vm::rescue,
                        onMoveToTrash = { requestRemoval(permanently = false) },
                        onDeleteForever = { requestRemoval(permanently = true) },
                    )
                }
            }
        }
    }
}

enum class AppTab(val label: String, val icon: ImageVector) {
    SWIPE("Swipe", Icons.Filled.ThumbUp),
    GALLERY("Gallery", Icons.Filled.Home),
    CLEANUP("Clean up", Icons.Filled.Search),
    REVIEW("Review", Icons.Filled.Delete),
}

@Composable
private fun PermissionScreen(onAllow: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Photo Swipe", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            "To help you clean up, the app needs to see your photos and videos. " +
                "Nothing leaves your phone.",
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAllow) { Text("Allow access") }
        TextButton(onClick = onOpenSettings) { Text("Open app settings") }
    }
}
