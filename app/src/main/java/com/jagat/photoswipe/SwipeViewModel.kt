package com.jagat.photoswipe

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The category chips at the top of the swipe screen. */
sealed interface Category {
    val label: String
    fun matches(item: MediaItem): Boolean = when (this) {
        All -> true
        Photos -> !item.isVideo
        Videos -> item.isVideo
        is Folder -> item.folder == name
    }

    data object All : Category { override val label = "All" }
    data object Photos : Category { override val label = "Photos" }
    data object Videos : Category { override val label = "Videos" }
    data class Folder(val name: String) : Category { override val label get() = name }
}

enum class Choice { KEEP, DELETE }
data class Decision(val id: Long, val choice: Choice)

data class UiState(
    val loading: Boolean = true,
    val all: List<MediaItem> = emptyList(),
    val kept: Set<Long> = emptySet(),
    val pending: Set<Long> = emptySet(),   // swiped left, waiting for final delete
    val category: Category = Category.All,
    val history: List<Decision> = emptyList(),
) {
    val categories: List<Category> by lazy {
        val folders = all.groupingBy { it.folder }.eachCount()
            .entries.sortedByDescending { it.value }
            .map { Category.Folder(it.key) }
        listOf(Category.All, Category.Photos, Category.Videos) + folders
    }

    /** Items still waiting for a swipe in the selected category. */
    val queue: List<MediaItem> by lazy {
        all.filter { it.id !in kept && it.id !in pending && category.matches(it) }
    }

    val pendingItems: List<MediaItem> by lazy { all.filter { it.id in pending } }
    val pendingBytes: Long by lazy { pendingItems.sumOf { it.size } }
}

class SwipeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = MediaRepository(app)
    private val store = DecisionStore(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            if (_state.value.all.isEmpty()) _state.update { it.copy(loading = true) }
            val items = repo.loadAll()
            val ids = items.map { it.id }.toSet()
            _state.update {
                it.copy(
                    loading = false,
                    all = items,
                    kept = store.kept() intersect ids,
                    pending = store.pending() intersect ids,
                )
            }
        }
    }

    fun keep(item: MediaItem) = decide(item, Choice.KEEP)
    fun markForDelete(item: MediaItem) = decide(item, Choice.DELETE)

    private fun decide(item: MediaItem, choice: Choice) {
        _state.update { s ->
            s.copy(
                kept = if (choice == Choice.KEEP) s.kept + item.id else s.kept,
                pending = if (choice == Choice.DELETE) s.pending + item.id else s.pending,
                history = (s.history + Decision(item.id, choice)).takeLast(100),
            )
        }
        persist()
    }

    fun undo() {
        _state.update { s ->
            val last = s.history.lastOrNull() ?: return@update s
            s.copy(
                kept = s.kept - last.id,
                pending = s.pending - last.id,
                history = s.history.dropLast(1),
            )
        }
        persist()
    }

    /** Review screen: changed your mind, keep this one after all. */
    fun rescue(item: MediaItem) {
        _state.update { s -> s.copy(pending = s.pending - item.id, kept = s.kept + item.id) }
        persist()
    }

    fun setCategory(category: Category) = _state.update { it.copy(category = category) }

    /** Show photos you already kept again, so you can re-sort them. */
    fun resetKept() {
        _state.update { it.copy(kept = emptySet(), history = emptyList()) }
        persist()
    }

    /** Called after Android confirms the delete / trash. */
    fun onDeleted() {
        _state.update { s ->
            s.copy(
                all = s.all.filter { it.id !in s.pending },
                history = s.history.filter { it.id !in s.pending },
                pending = emptySet(),
            )
        }
        persist()
        load()
    }

    private fun persist() {
        val s = _state.value
        store.save(s.kept, s.pending)
    }
}
