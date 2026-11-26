package com.jagat.photoswipe.ui

import android.text.format.Formatter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jagat.photoswipe.Category
import com.jagat.photoswipe.MediaItem
import com.jagat.photoswipe.SortOrder
import com.jagat.photoswipe.UiState
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeScreen(
    state: UiState,
    onKeep: (MediaItem) -> Unit,
    onDelete: (MediaItem) -> Unit,
    onUndo: () -> Unit,
    onCategory: (Category) -> Unit,
    onOpenReview: () -> Unit,
    onOpenVideo: (MediaItem) -> Unit,
    onResetKept: () -> Unit,
    onSort: (SortOrder) -> Unit,
) {
    val context = LocalContext.current
    val queue = state.queue

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Photo Swipe") },
                actions = { SortButton(state.sort, onSort) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            CategoryChips(state, onCategory)

            Text(
                "${queue.size} left  ·  ${state.kept.size} kept  ·  " +
                    "${state.pending.size} to delete " +
                    "(${Formatter.formatShortFileSize(context, state.pendingBytes)})",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            val sorted = state.kept.size + state.pending.size
            if (sorted > 0 && queue.isNotEmpty()) {
                Text(
                    "Continuing where you left off · $sorted already sorted",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            Box(
                Modifier.weight(1f).fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    state.loading -> CircularProgressIndicator()
                    queue.isEmpty() -> EmptyState(state, onOpenReview, onResetKept)
                    else -> {
                        queue.getOrNull(1)?.let { next ->
                            MediaCard(next, Modifier.fillMaxSize().scale(0.94f))
                        }
                        val top = queue.first()
                        key(top.id) {
                            SwipeableCard(
                                item = top,
                                onSwipedLeft = { onDelete(top) },
                                onSwipedRight = { onKeep(top) },
                                onTap = { if (top.isVideo) onOpenVideo(top) },
                            )
                        }
                    }
                }
            }

            // Buttons for people who prefer tapping over swiping
            Row(
                Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledIconButton(
                    onClick = { queue.firstOrNull()?.let(onDelete) },
                    enabled = queue.isNotEmpty(),
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = DeleteRed),
                ) { Icon(Icons.Filled.Close, "Delete", tint = Color.White) }

                OutlinedIconButton(
                    onClick = onUndo,
                    enabled = state.history.isNotEmpty(),
                    modifier = Modifier.size(48.dp),
                ) { Icon(Icons.Filled.Refresh, "Undo") }

                FilledIconButton(
                    onClick = { queue.firstOrNull()?.let(onKeep) },
                    enabled = queue.isNotEmpty(),
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = KeepGreen),
                ) { Icon(Icons.Filled.Favorite, "Keep", tint = Color.White) }
            }
        }
    }
}

@Composable
private fun SwipeableCard(
    item: MediaItem,
    onSwipedLeft: () -> Unit,
    onSwipedRight: () -> Unit,
    onTap: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val threshold = width * 0.3f

        fun settle() = scope.launch {
            when {
                offsetX.value > threshold -> {
                    offsetX.animateTo(width * 1.5f, tween(200))
                    onSwipedRight()
                }
                offsetX.value < -threshold -> {
                    offsetX.animateTo(-width * 1.5f, tween(200))
                    onSwipedLeft()
                }
                else -> {
                    launch { offsetX.animateTo(0f, spring()) }
                    offsetY.animateTo(0f, spring())
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = offsetX.value
                    translationY = offsetY.value
                    rotationZ = offsetX.value / width * 15f
                }
                .pointerInput(item.id) {
                    detectDragGestures(
                        onDragEnd = { settle() },
                        onDragCancel = { settle() },
                        onDrag = { change, drag ->
                            change.consume()
                            scope.launch {
                                offsetX.snapTo(offsetX.value + drag.x)
                                offsetY.snapTo(offsetY.value + drag.y)
                            }
                        },
                    )
                }
                .pointerInput(item.id) { detectTapGestures(onTap = { onTap() }) },
        ) {
            MediaCard(item, Modifier.fillMaxSize())

            val progress = (offsetX.value / threshold).coerceIn(-1f, 1f)
            if (progress > 0f) {
                SwipeLabel("KEEP", KeepGreen, -15f, Modifier.align(Alignment.TopStart).alpha(progress))
            } else if (progress < 0f) {
                SwipeLabel("DELETE", DeleteRed, 15f, Modifier.align(Alignment.TopEnd).alpha(-progress))
            }
        }
    }
}

@Composable
private fun SwipeLabel(text: String, color: Color, angle: Float, modifier: Modifier) {
    Text(
        text,
        color = color,
        fontSize = 32.sp,
        fontWeight = FontWeight.Black,
        modifier = modifier
            .padding(24.dp)
            .rotate(angle)
            .border(BorderStroke(4.dp, color), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
fun MediaCard(item: MediaItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Card(
        modifier,
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = item.uri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )

            if (item.isVideo) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(72.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.PlayArrow, "Play video", tint = Color.White, modifier = Modifier.size(48.dp))
                }
            }

            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                    .padding(16.dp),
            ) {
                Text(item.folder, color = Color.White, fontWeight = FontWeight.SemiBold)
                val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(item.dateAdded * 1000))
                val size = Formatter.formatShortFileSize(context, item.size)
                val extra = if (item.isVideo) "  ·  ${formatDuration(item.durationMs)}" else ""
                Text("$date  ·  $size$extra", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun EmptyState(state: UiState, onOpenReview: () -> Unit, onResetKept: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("All sorted here!", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        if (state.pending.isNotEmpty()) {
            TextButton(onClick = onOpenReview) {
                Text("Review ${state.pending.size} items to delete")
            }
        }
        if (state.kept.isNotEmpty()) {
            TextButton(onClick = onResetKept) { Text("Start over (show kept photos again)") }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}
