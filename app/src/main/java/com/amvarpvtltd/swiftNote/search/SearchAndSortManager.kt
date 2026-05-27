package com.amvarpvtltd.swiftNote.search

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.Subject
import androidx.compose.material.icons.outlined.Event
import androidx.compose.runtime.*
import com.amvarpvtltd.swiftNote.dataclass
import com.amvarpvtltd.swiftNote.richtext.RichTextRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import com.amvarpvtltd.swiftNote.utils.Constants
import java.text.Collator
import java.util.*

enum class SortOption {
    DATE_CREATED_DESC,
    DATE_CREATED_ASC,
    DATE_MODIFIED_DESC,
    DATE_MODIFIED_ASC,
    TITLE_ASC,
    TITLE_DESC,
    CONTENT_LENGTH_DESC,
    CONTENT_LENGTH_ASC
}

data class SearchAndSortState(
    val searchQuery: String = "",
    val sortOption: SortOption = SortOption.DATE_CREATED_DESC,
    val filteredNotes: List<dataclass> = emptyList(),
    val isSearchActive: Boolean = false,
    val categoryFilter: String = "" // Empty = "All"
)

/**
 * Manages search and sort state for the notes list.
 *
 * [initialNotes] pre-seeds the manager with already-cached notes so there is no
 * empty-state flash when navigating back to the home screen.
 */
@OptIn(FlowPreview::class)
class SearchAndSortManager(
    private val scope: CoroutineScope,
    private val initialNotes: List<dataclass> = emptyList()
) {
    private val _searchQuery = MutableStateFlow("")
    private val _sortOption = MutableStateFlow(SortOption.DATE_CREATED_DESC)
    private val _allNotes = MutableStateFlow<List<dataclass>>(initialNotes)
    private val _categoryFilter = MutableStateFlow("")

    private val debouncedSearchQuery: Flow<String> = _searchQuery
        .debounce(Constants.SEARCH_DEBOUNCE_DELAY)
        .distinctUntilChanged()

    val searchAndSortState: StateFlow<SearchAndSortState> = combine(
        debouncedSearchQuery,
        _sortOption,
        _allNotes,
        _categoryFilter
    ) { query: String, sort: SortOption, notes: List<dataclass>, category: String ->
        // Apply category filter before search/sort so category chips behave predictably.
        val categoryFiltered = if (category.isBlank()) notes
                               else notes.filter { it.category.equals(category, ignoreCase = true) }
        val filtered: List<dataclass> = if (query.isBlank()) categoryFiltered
                                        else searchNotes(categoryFiltered, query)
        val sorted: List<dataclass> = sortNotes(filtered, sort)
        SearchAndSortState(
            searchQuery = query,
            sortOption = sort,
            filteredNotes = sorted,
            isSearchActive = query.isNotBlank(),
            categoryFilter = category
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = SearchAndSortState(filteredNotes = initialNotes)
    )

    /** Get distinct categories that have at least one note */
    val availableCategories: StateFlow<List<String>> = _allNotes
        .map { notes -> notes.mapNotNull { it.category.ifBlank { null } }.distinct().sorted() }
        .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = emptyList())

    fun updateSearchQuery(query: String) { _searchQuery.value = query }
    fun updateSortOption(sortOption: SortOption) { _sortOption.value = sortOption }
    fun updateNotes(notes: List<dataclass>) { _allNotes.value = notes }
    fun clearSearch() { _searchQuery.value = "" }
    fun updateCategoryFilter(category: String) { _categoryFilter.value = category }

    private fun searchNotes(notes: List<dataclass>, query: String): List<dataclass> {
        if (query.isBlank()) return notes
        val searchTerms = query.lowercase().split(" ").filter { it.isNotBlank() }
        return notes.filter { note ->
            val combined = "${note.title.lowercase()} ${RichTextRenderer.stripHtmlToPlainText(note.description).lowercase()}"
            searchTerms.all { term -> combined.contains(term) }
        }.sortedByDescending { note -> calculateRelevanceScore(note, searchTerms) }
    }

    private fun calculateRelevanceScore(note: dataclass, searchTerms: List<String>): Int {
        var score = 0
        val titleLower = note.title.lowercase()
        val descriptionLower = RichTextRenderer.stripHtmlToPlainText(note.description).lowercase()
        searchTerms.forEach { term ->
            when {
                titleLower.startsWith(term) -> score += 10
                titleLower.contains(term) -> score += 5
                descriptionLower.contains(term) -> score += 1
            }
        }
        return score
    }

    private fun sortNotes(notes: List<dataclass>, sortOption: SortOption): List<dataclass> {
        val collator = Collator.getInstance(Locale.getDefault()).apply {
            strength = Collator.SECONDARY
        }
        // Always keep pinned notes at the top regardless of sort
        val (pinned, unpinned) = notes.partition { it.isPinned }
        val sortedUnpinned = when (sortOption) {
            SortOption.DATE_CREATED_DESC  -> unpinned.sortedByDescending { it.timestamp }
            SortOption.DATE_CREATED_ASC   -> unpinned.sortedBy { it.timestamp }
            SortOption.DATE_MODIFIED_DESC -> unpinned.sortedByDescending { it.updatedAt }
            SortOption.DATE_MODIFIED_ASC  -> unpinned.sortedBy { it.updatedAt }
            SortOption.TITLE_ASC          -> unpinned.sortedWith { a, b -> collator.compare(a.title.trim(), b.title.trim()) }
            SortOption.TITLE_DESC         -> unpinned.sortedWith { a, b -> collator.compare(b.title.trim(), a.title.trim()) }
            SortOption.CONTENT_LENGTH_DESC -> unpinned.sortedByDescending { it.title.length + it.description.length }
            SortOption.CONTENT_LENGTH_ASC  -> unpinned.sortedBy { it.title.length + it.description.length }
        }
        val sortedPinned = when (sortOption) {
            SortOption.DATE_CREATED_DESC  -> pinned.sortedByDescending { it.timestamp }
            SortOption.DATE_CREATED_ASC   -> pinned.sortedBy { it.timestamp }
            SortOption.DATE_MODIFIED_DESC -> pinned.sortedByDescending { it.updatedAt }
            SortOption.DATE_MODIFIED_ASC  -> pinned.sortedBy { it.updatedAt }
            SortOption.TITLE_ASC          -> pinned.sortedWith { a, b -> collator.compare(a.title.trim(), b.title.trim()) }
            SortOption.TITLE_DESC         -> pinned.sortedWith { a, b -> collator.compare(b.title.trim(), a.title.trim()) }
            SortOption.CONTENT_LENGTH_DESC -> pinned.sortedByDescending { it.title.length + it.description.length }
            SortOption.CONTENT_LENGTH_ASC  -> pinned.sortedBy { it.title.length + it.description.length }
        }
        return sortedPinned + sortedUnpinned
    }

    companion object {
        fun getSortOptionLabel(sortOption: SortOption): String = when (sortOption) {
            SortOption.DATE_CREATED_DESC  -> "Newest First"
            SortOption.DATE_CREATED_ASC   -> "Oldest First"
            SortOption.DATE_MODIFIED_DESC -> "Recently Modified"
            SortOption.DATE_MODIFIED_ASC  -> "Least Recently Modified"
            SortOption.TITLE_ASC          -> "Title A-Z"
            SortOption.TITLE_DESC         -> "Title Z-A"
            SortOption.CONTENT_LENGTH_DESC -> "Longest First"
            SortOption.CONTENT_LENGTH_ASC  -> "Shortest First"
        }

        fun getSortIcon(sortOption: SortOption): androidx.compose.ui.graphics.vector.ImageVector =
            when (sortOption) {
                SortOption.DATE_CREATED_DESC, SortOption.DATE_MODIFIED_DESC,
                SortOption.DATE_CREATED_ASC,  SortOption.DATE_MODIFIED_ASC  -> Icons.Outlined.Event
                SortOption.TITLE_ASC, SortOption.TITLE_DESC                  -> Icons.AutoMirrored.Outlined.Sort
                SortOption.CONTENT_LENGTH_DESC, SortOption.CONTENT_LENGTH_ASC -> Icons.AutoMirrored.Outlined.Subject
            }
    }
}

@Composable
fun rememberSearchAndSortManager(initialNotes: List<dataclass> = emptyList()): SearchAndSortManager {
    val scope = rememberCoroutineScope()
    return remember { SearchAndSortManager(scope, initialNotes) }
}
