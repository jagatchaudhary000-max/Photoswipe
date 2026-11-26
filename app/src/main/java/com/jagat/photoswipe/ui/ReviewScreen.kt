package com.jagat.photoswipe.ui

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jagat.photoswipe.MediaItem
import com.jagat.photoswipe.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    state: UiState,
    onBack: () -> Unit,
    onRescue: (MediaItem) -> Unit,
    onMoveToTrash: () -> Unit,
    onDeleteForever: () -> Unit,
) {
    val context = LocalContext.current
    val marked = state.pendingItems

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("To delete (${marked.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
        bottomBar = {
            if (marked.isNotEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            "Frees about ${Formatter.formatShortFileSize(context, state.pendingBytes)}. " +
                                "Tap a photo to keep it instead.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Button(
                                onClick = onMoveToTrash,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = DeleteRed),
                            ) { Text("Move to trash") }
                            OutlinedButton(
                                onClick = onDeleteForever,
                                modifier = Modifier.weight(1f),
                            ) { Text("Delete forever") }
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (marked.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Nothing marked for deletion yet.\nSwipe left on a photo to add it here.")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                contentPadding = padding,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(marked, key = { it.id }) { item ->
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .padding(2.dp)
                            .clickable { onRescue(item) },
                    ) {
                        AsyncImage(
                            model = item.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (item.isVideo) {
                            Icon(
                                Icons.Filled.PlayArrow, "Video",
                                tint = Color.White,
                                modifier = Modifier.align(Alignment.Center).size(32.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
