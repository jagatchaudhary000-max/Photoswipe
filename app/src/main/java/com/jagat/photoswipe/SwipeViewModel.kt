package com.jagat.photoswipe

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The category chips shown on the swipe and gallery screens. */
sealed interface Category {
    val label: String
    fun matches(item: MediaItem): Boolean = when (this) {
        All -> true
        Photos -> !item.isVideo
        Videos -> item.isVideo
        is Folder -> item.folder == name
    }

    fun encode(): String = when (this) {
        All -> "all"
        Photos -> "photos"
        Videos -> "videos"
        is Folder -> "folder:$name"
    }

    data object All : Category { override val label = "All" }
    data object Photos : Category { override val label = "Photos" }
    data object Videos : Category { override val label = "Videos" }
    data class Folder(val name: String) : Category { override val label get() = name }

    companion object {
        fun decode(s: String?): Category = when {
            s == "photos" -> Photos
            s == "videos" -> Videos
            s != null && s.startsWith("folder:") -> Folder(s.removePrefix("folder:"))
            else -> All
        }
    }
}

enum class SortOrder(val label: String) {
    NEWEST("Newest"), OLDEST("Oldest"), LARGEST("Largest");

    companion object {
        fun decode(s: String?) = entries.firstOrNull { it.name == s } ?: NEWEST
    }
}

enum class Choice { KEEP, DELETE }
data class Decision(val id: Long, val choice: Choice)

data class ScanState(
    val running: Boolean = false,
    val finished: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val duplicates: List<List<Long>> = emptyList(),
    val similar: List<List<Long>> = emptyList(),
)

data class UiState(
    val loading: Boolean = true,
    val all: List<MediaItem> = emptyList(),
    val kept: Set<Long> = emptySet(),
    val pending: Set<Long> = emptySet(),   // marked for delete, waiting for final confirm
    val category: Category = Category.All,
    val sort: SortOrder = SortOrder.NEWEST,
    val history: List<Decision> = emptyList(),
    val scan: ScanState = ScanState(),
) {
    val byId: Map<Long, MediaItem> by lazy { all.associateBy { it.id } }

    val categories: List<Category> by lazy {
        val folders = all.groupingBy { it.folder }.eachCount()
            .entries.sortedByDescending { it.value }
            .map { Category.Folder(it.key) }
        listOf(Category.All, Category.Photos, Category.Videos) + folders
    }

    /** Everything in the selected category, in the selected order (for the Gallery). */
    val galleryItems: List<MediaItem> by lazy {
        val list = all.filter { category.matches(it) }
        when (sort) {
            SortOrder.NEWEST -> list
            SortOrder.OLDEST -> list.reversed()
            SortOrder.LARGEST -> list.sortedByDescending { it.size }
        }
    }

    /** Items not yet swiped. Because decisions are saved, this continues where you left off. */
    val queue: List<MediaItem> by lazy {
        galleryItems.filter { it.id !in kept && it.id !in pending }
    }

    val pendingItems: List<MediaItem> by lazy { all.filter { it.id in pending } }
    val pendingBytes: Long by lazy { pendingItems.sumOf { it.size } }

    val duplicateGroups: List<List<MediaItem>> by lazy { visible(scan.duplicates) }
    val similarGroups: List<List<MediaItem>> by lazy { visible(scan.similar) }

    private fun visible(groups: List<List<Long>>): List<List<MediaItem>> =
        groups.map { g -> g.mapNotNull { byId[it] }.filter { it.id !in pending } }
            .filter { it.size > 1 }
}

class SwipeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = MediaRepository(app)
    private val store = DecisionStore(app)

    private val _state = MutableStateFlow(
        UiState(
            category = Category.decode(store.category()),
            sort = SortOrder.decode(store.sort()),
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var scanJob: Job? = null

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
            val scan = _state.value.scan
            if (!scan.running && !scan.finished) startScan()
        }
    }

    // ---- Swipe ----

    fun keep(item: MediaItem) = decide(item, Choice.KEEP)
    fun markForDelete(item: MediaItem) = decide(item, Choice.DELETE)

    private fun decide(item: MediaItem, choice: Choice) {
        _state.update { s ->
            s.copy(
                kept = if (choice == Choice.KEEP) s.kept + item.id else s.kept - item.id,
                pending = if (choice == Choice.DELETE) s.pending + item.id else s.pending - item.id,
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

    // ---- Bulk actions (gallery, clean up, review) ----

    fun markManyForDelete(ids: Collection<Long>) {
        val set = ids.toSet()
        _state.update { s -> s.copy(pending = s.pending + set, kept = s.kept - set) }
        persist()
    }

    fun keepMany(ids: Collection<Long>) {
        val set = ids.toSet()
        _state.update { s -> s.copy(kept = s.kept + set, pending = s.pending - set) }
        persist()
    }

    fun clearMany(ids: Collection<Long>) {
        val set = ids.toSet()
        _state.update { s -> s.copy(kept = s.kept - set, pending = s.pending - set) }
        persist()
    }

    fun rescue(item: MediaItem) = keepMany(listOf(item.id))

    fun setCategory(category: Category) {
        _state.update { it.copy(category = category) }
        persistView()
    }

    fun setSort(sort: SortOrder) {
        _state.update { it.copy(sort = sort) }
        persistView()
    }

    fun resetKept() {
        _state.update { it.copy(kept = emptySet(), history = emptyList()) }
        persist()
    }

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

    // ---- Duplicate / similar scan ----

    fun startScan() {
        if (scanJob?.isActive == true) return
        val items = _state.value.all
        if (items.isEmpty()) return
        val app = getApplication<Application>()

        scanJob = viewModelScope.launch {
            val photos = items.filter { !it.isVideo }
            _state.update { it.copy(scan = ScanState(running = true, total = photos.size)) }

            val hashes = withContext(Dispatchers.IO) {
                val cache = HashCache(app)
                val saved = cache.load()
                val out = HashMap<Long, Long>()
                photos.forEachIndexed { i, item ->
                    val key = "${item.id}:${item.dateModified}"
                    val h = saved[key] ?: Analyzer.thumbnailHash(app, item)?.also { saved[key] = it }
                    if (h != null) out[item.id] = h
                    if (i % 20 == 0) _state.update { s -> s.copy(scan = s.scan.copy(done = i)) }
                }
                cache.save(saved)
                out
            }

            val duplicates = withContext(Dispatchers.IO) { Analyzer.findDuplicates(app, items) }
            val extraCopies = duplicates.flatMap { it.drop(1) }.toSet()
            val similar = withContext(Dispatchers.Default) {
                Analyzer.groupSimilar(
                    photos.filter { it.id in hashes && it.id !in extraCopies }
                        .map { it.id to hashes.getValue(it.id) }
                )
            }

            _state.update {
                it.copy(
                    scan = ScanState(
                        finished = true,
                        done = photos.size,
                        total = photos.size,
                        duplicates = duplicates,
                        similar = similar,
                    )
                )
            }
        }
    }

    private fun persist() {
        val s = _state.value
        store.save(s.kept, s.pending)
    }

    private fun persistView() {
        val s = _state.value
        store.saveView(s.category.encode(), s.sort.name)
    }
}
