package com.jagat.photoswipe.ui

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jagat.photoswipe.Category
import com.jagat.photoswipe.MediaItem
import com.jagat.photoswipe.SortOrder
import com.jagat.photoswipe.UiState
import java.text.DateFormat
import java.util.Date

enum class Mark { KEPT, PENDING }

fun UiState.markOf(id: Long): Mark? = when (id) {
    in pending -> Mark.PENDING
    in kept -> Mark.KEPT
    else -> null
}

fun sizeText(context: Context, bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

fun dateText(seconds: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(seconds * 1000))

fun Set<Long>.toggle(id: Long): Set<Long> = if (id in this) this - id else this + id

@Composable
fun CategoryChips(state: UiState, onCategory: (Category) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.categories, key = { it.encode() }) { c ->
            FilterChip(
                selected = c == state.category,
                onClick = { onCategory(c) },
                label = { Text(c.label) },
            )
        }
    }
}

@Composable
fun SortButton(sort: SortOrder, onSort: (SortOrder) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text(sort.label)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Sort")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SortOrder.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSort(option)
                        open = false
                    },
                )
            }
        }
    }
}

/**
 * A square thumbnail. Shows a red/green badge for items marked delete/keep,
 * a tick circle when selection mode is on, and optionally the file size.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Thumb(
    item: MediaItem,
    mark: Mark?,
    selected: Boolean?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    showSize: Boolean = false,
) {
    val context = LocalContext.current
    Box(
        modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(6.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (mark == Mark.PENDING) {
            Box(Modifier.fillMaxSize().background(DeleteRed.copy(alpha = 0.35f)))
        }
        if (selected == true) {
            Box(
                Modifier
                    .fillMaxSize()
                    .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
            )
        }
        if (item.isVideo) {
            Icon(
                Icons.Filled.PlayArrow, "Video",
                tint = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).size(20.dp),
            )
        }
        when (mark) {
            Mark.PENDING -> Badge(Icons.Filled.Delete, DeleteRed, Modifier.align(Alignment.TopEnd))
            Mark.KEPT -> Badge(Icons.Filled.Favorite, KeepGreen, Modifier.align(Alignment.TopEnd))
            null -> Unit
        }
        if (selected != null) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(22.dp)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.3f),
                        CircleShape,
                    )
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        if (showSize) {
            Text(
                sizeText(context, item.size),
                color = Color.White,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun Badge(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier) {
    Box(
        modifier.padding(4.dp).size(20.dp).background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(12.dp))
    }
}

/** Bottom bar: "3 selected · 12 MB   [Mark for delete]". */
@Composable
fun MarkBar(count: Int, bytes: Long, onMark: () -> Unit) {
    val context = LocalContext.current
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$count selected · ${sizeText(context, bytes)}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onMark,
                enabled = count > 0,
                colors = ButtonDefaults.buttonColors(containerColor = DeleteRed),
            ) { Text("Mark for delete") }
        }
    }
}
