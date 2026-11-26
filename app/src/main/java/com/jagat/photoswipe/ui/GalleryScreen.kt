package com.jagat.photoswipe.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jagat.photoswipe.Category
import com.jagat.photoswipe.MediaItem
import com.jagat.photoswipe.SortOrder
import com.jagat.photoswipe.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    state: UiState,
    onCategory: (Category) -> Unit,
    onSort: (SortOrder) -> Unit,
    onMarkDelete: (Collection<Long>) -> Unit,
    onKeep: (Collection<Long>) -> Unit,
    onClear: (Collection<Long>) -> Unit,
    onOpenVideo: (MediaItem) -> Unit,
) {
    val context = LocalContext.current
    val items = state.galleryItems
    var selection by remember { mutableStateOf(setOf<Long>()) }
    var viewerIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    val openIndex = viewerIndex
    if (openIndex != null && items.isNotEmpty()) {
        BackHandler { viewerIndex = null }
        MediaViewer(
            items = items,
            startIndex = openIndex.coerceIn(0, items.lastIndex),
            state = state,
            onClose = { viewerIndex = null },
            onMarkDelete = onMarkDelete,
            onKeep = onKeep,
            onClear = onClear,
            onOpenVideo = onOpenVideo,
        )
        return
    }

    BackHandler(enabled = selection.isNotEmpty()) { selection = emptySet() }

    Scaffold(
        topBar = {
            if (selection.isEmpty()) {
                TopAppBar(
                    title = { Text("Gallery (${items.size})") },
                    actions = { SortButton(state.sort, onSort) },
                )
            } else {
                TopAppBar(
                    title = { Text("${selection.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { selection = emptySet() }) {
                            Icon(Icons.Filled.Close, "Cancel selection")
                        }
                    },
                    actions = {
                        TextButton(onClick = { selection = items.map { it.id }.toSet() }) { Text("All") }
                        TextButton(onClick = { onKeep(selection); selection = emptySet() }) {
                            Text("Keep", color = KeepGreen)
                        }
                        TextButton(onClick = { onMarkDelete(selection); selection = emptySet() }) {
                            Text("Delete", color = DeleteRed)
                        }
                    },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            CategoryChips(state, onCategory)
            Text(
                "${sizeText(context, items.sumOf { it.size })} · tap to view, long-press to select several",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(100.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp),
            ) {
                items(items.size, key = { items[it].id }) { i ->
                    val item = items[i]
                    Thumb(
                        item = item,
                        mark = state.markOf(item.id),
                        selected = if (selection.isEmpty()) null else item.id in selection,
                        onClick = {
                            if (selection.isEmpty()) viewerIndex = i
                            else selection = selection.toggle(item.id)
                        },
                        onLongClick = { selection = selection.toggle(item.id) },
                    )
                }
            }
        }
    }
}

/** Full-screen viewer: swipe sideways between photos, mark keep/delete. */
@Composable
private fun MediaViewer(
    items: List<MediaItem>,
    startIndex: Int,
    state: UiState,
    onClose: () -> Unit,
    onMarkDelete: (Collection<Long>) -> Unit,
    onKeep: (Collection<Long>) -> Unit,
    onClear: (Collection<Long>) -> Unit,
    onOpenVideo: (MediaItem) -> Unit,
) {
    val context = LocalContext.current
    val pager = rememberPagerState(initialPage = startIndex) { items.size }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            key = { items[it].id },
        ) { page ->
            val item = items[page]
            Box(
                Modifier.fillMaxSize().clickable(enabled = item.isVideo) { onOpenVideo(item) },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = item.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                if (item.isVideo) {
                    Box(
                        Modifier.size(72.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(48.dp))
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close", tint = Color.White) }
            Text("${pager.currentPage + 1} / ${items.size}", color = Color.White)
        }

        val current = items.getOrNull(pager.currentPage)
        if (current != null) {
            val mark = state.markOf(current.id)
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .navigationBarsPadding()
                    .padding(16.dp),
            ) {
                Text(
                    "${current.folder} · ${dateText(current.dateAdded)} · ${sizeText(context, current.size)}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (mark == Mark.PENDING) {
                        OutlinedButton(onClick = { onClear(listOf(current.id)) }, modifier = Modifier.weight(1f)) {
                            Text("Undo delete", color = Color.White)
                        }
                    } else {
                        Button(
                            onClick = { onMarkDelete(listOf(current.id)) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = DeleteRed),
                        ) { Text("Delete") }
                    }
                    if (mark == Mark.KEPT) {
                        OutlinedButton(onClick = { onClear(listOf(current.id)) }, modifier = Modifier.weight(1f)) {
                            Text("Kept ✓", color = Color.White)
                        }
                    } else {
                        Button(
                            onClick = { onKeep(listOf(current.id)) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = KeepGreen),
                        ) { Text("Keep") }
                    }
                }
            }
        }
    }
}
