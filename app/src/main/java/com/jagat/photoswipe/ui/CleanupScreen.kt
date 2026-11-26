package com.jagat.photoswipe.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jagat.photoswipe.MediaItem
import com.jagat.photoswipe.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanupScreen(
    state: UiState,
    onRescan: () -> Unit,
    onMarkDelete: (Collection<Long>) -> Unit,
) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val scan = state.scan

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Clean up") },
                actions = {
                    if (!scan.running) TextButton(onClick = onRescan) { Text("Rescan") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                listOf(
                    "Duplicates (${state.duplicateGroups.size})",
                    "Similar (${state.similarGroups.size})",
                    "Large files",
                ).forEachIndexed { i, title ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title, maxLines = 1) })
                }
            }

            if (scan.running) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        "Scanning photos… ${scan.done} of ${scan.total}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LinearProgressIndicator(
                        progress = { if (scan.total > 0) scan.done / scan.total.toFloat() else 0f },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Text(
                        "The first scan takes a few minutes. Later scans are much faster.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            when (tab) {
                0 -> GroupList(
                    groups = state.duplicateGroups,
                    scanFinished = scan.finished,
                    emptyText = "No duplicate copies found.",
                    hint = "Exact copies. The oldest copy is kept; the rest are pre-selected.",
                    defaultSelection = { g -> g.drop(1).map { it.id } },
                    title = { g -> "${g.size} copies · ${sizeText(context, g.first().size)} each" },
                    onMarkDelete = onMarkDelete,
                )
                1 -> GroupList(
                    groups = state.similarGroups,
                    scanFinished = scan.finished,
                    emptyText = "No similar photos found.",
                    hint = "Look-alike photos. The largest (usually sharpest) is kept; tap to change.",
                    defaultSelection = { g ->
                        val best = g.maxBy { it.size }
                        g.filter { it.id != best.id }.map { it.id }
                    },
                    title = { g -> "${g.size} similar photos · ${dateText(g.first().dateAdded)}" },
                    onMarkDelete = onMarkDelete,
                )
                else -> LargeFiles(state, onMarkDelete)
            }
        }
    }
}

@Composable
private fun ColumnScope.GroupList(
    groups: List<List<MediaItem>>,
    scanFinished: Boolean,
    emptyText: String,
    hint: String,
    defaultSelection: (List<MediaItem>) -> List<Long>,
    title: (List<MediaItem>) -> String,
    onMarkDelete: (Collection<Long>) -> Unit,
) {
    var selected by remember(groups) { mutableStateOf(groups.flatMap(defaultSelection).toSet()) }

    if (groups.isEmpty()) {
        Box(Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                if (scanFinished) emptyText else "Results will appear here when the scan finishes.",
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    Text(
        hint,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    LazyColumn(Modifier.weight(1f)) {
        items(groups, key = { it.first().id }) { group ->
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(title(group), style = MaterialTheme.typography.titleSmall)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp)) {
                    group.forEach { item ->
                        Thumb(
                            item = item,
                            mark = null,
                            selected = item.id in selected,
                            onClick = { selected = selected.toggle(item.id) },
                            modifier = Modifier.size(110.dp),
                            showSize = true,
                        )
                    }
                }
            }
            HorizontalDivider()
        }
    }
    val chosen = groups.flatten().filter { it.id in selected }
    MarkBar(count = chosen.size, bytes = chosen.sumOf { it.size }) { onMarkDelete(selected) }
}

@Composable
private fun ColumnScope.LargeFiles(state: UiState, onMarkDelete: (Collection<Long>) -> Unit) {
    val context = LocalContext.current
    var type by rememberSaveable { mutableIntStateOf(0) }     // 0 = all, 1 = photos, 2 = videos
    var minMb by rememberSaveable { mutableIntStateOf(10) }

    val list = remember(state.all, state.pending, type, minMb) {
        state.all.filter {
            it.id !in state.pending &&
                it.size >= minMb * 1024L * 1024L &&
                when (type) {
                    1 -> !it.isVideo
                    2 -> it.isVideo
                    else -> true
                }
        }.sortedByDescending { it.size }
    }
    var selected by remember(list) { mutableStateOf(setOf<Long>()) }

    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf("Photos and videos", "Photos", "Videos").forEachIndexed { i, label ->
            FilterChip(selected = type == i, onClick = { type = i }, label = { Text(label) })
        }
    }
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(2, 5, 10, 50, 100).forEach { mb ->
            FilterChip(selected = minMb == mb, onClick = { minMb = mb }, label = { Text("$mb MB+") })
        }
    }
    Text(
        "${list.size} files · ${sizeText(context, list.sumOf { it.size })} · biggest first. Tap to select.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )

    if (list.isEmpty()) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("No files bigger than $minMb MB here.")
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(110.dp),
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
        ) {
            items(list.size, key = { list[it].id }) { i ->
                val item = list[i]
                Thumb(
                    item = item,
                    mark = state.markOf(item.id),
                    selected = item.id in selected,
                    onClick = { selected = selected.toggle(item.id) },
                    showSize = true,
                )
            }
        }
    }
    val chosen = list.filter { it.id in selected }
    MarkBar(count = chosen.size, bytes = chosen.sumOf { it.size }) { onMarkDelete(selected) }
}
