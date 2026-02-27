package com.amvarpvtltd.swiftNote.search

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.Subject
import androidx.compose.material.icons.outlined.Event
import androidx.compose.runtime.*
import com.amvarpvtltd.swiftNote.dataclass
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
    val isSearchActive: Boolean = false
)

class SearchAndSortManager {
    private val _searchQuery = MutableStateFlow("")
    private val _sortOption = MutableStateFlow(SortOption.DATE_CREATED_DESC)
    private val _allNotes = MutableStateFlow<List<dataclass>>(emptyList())

    @OptIn(FlowPreview::class)
    private val debouncedSearchQuery = _searchQuery
        .debounce(Constants.SEARCH_DEBOUNCE_DELAY)
        .distinctUntilChanged()

    val searchAndSortState: StateFlow<SearchAndSortState> = combine(
        debouncedSearchQuery,
        _sortOption,
        _allNotes
    ) { query, sort, notes ->
        val filtered = if (query.isBlank()) {
            notes
        } else {
            searchNotes(notes, query)
        }

        val sorted = sortNotes(filtered, sort)

        SearchAndSortState(
            searchQuery = query,
            sortOption = sort,
            filteredNotes = sorted,
            isSearchActive = query.isNotBlank()
        )
    }.stateIn(
        scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default),
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SearchAndSortState()
    )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateSortOption(sortOption: SortOption) {
        _sortOption.value = sortOption
    }

    fun updateNotes(notes: List<dataclass>) {
        _allNotes.value = notes
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    private fun searchNotes(notes: List<dataclass>, query: String): List<dataclass> {
        if (query.isBlank()) return notes

        val searchTerms = query.lowercase().split(" ").filter { it.isNotBlank() }

        return notes.filter { note ->
            val titleLower = note.title.lowercase()
            val descriptionLower = note.description.lowercase()
            val combinedContent = "$titleLower $descriptionLower"

            // Check if all search terms are found
            searchTerms.all { term ->
                combinedContent.contains(term)
            }
        }.sortedByDescending { note ->
            // Calculate relevance score for better search results
            calculateRelevanceScore(note, searchTerms)
        }
    }

    private fun calculateRelevanceScore(note: dataclass, searchTerms: List<String>): Int {
        var score = 0
        val titleLower = note.title.lowercase()
        val descriptionLower = note.description.lowercase()

        searchTerms.forEach { term ->
            // Title matches get higher score
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
            strength = Collator.SECONDARY // Case-insensitive
        }

        return when (sortOption) {
            SortOption.DATE_CREATED_DESC -> notes.sortedByDescending { it.timestamp }
            SortOption.DATE_CREATED_ASC -> notes.sortedBy { it.timestamp }
            SortOption.DATE_MODIFIED_DESC -> notes.sortedByDescending { it.timestamp }
            SortOption.DATE_MODIFIED_ASC -> notes.sortedBy { it.timestamp }
            SortOption.TITLE_ASC -> notes.sortedWith { a, b ->
                collator.compare(a.title.trim(), b.title.trim())
            }
            SortOption.TITLE_DESC -> notes.sortedWith { a, b ->
                collator.compare(b.title.trim(), a.title.trim())
            }
            SortOption.CONTENT_LENGTH_DESC -> notes.sortedByDescending {
                it.title.length + it.description.length
            }
            SortOption.CONTENT_LENGTH_ASC -> notes.sortedBy {
                it.title.length + it.description.length
            }
        }
    }

    companion object {
        fun getSortOptionLabel(sortOption: SortOption): String {
            return when (sortOption) {
                SortOption.DATE_CREATED_DESC -> "Newest First"
                SortOption.DATE_CREATED_ASC -> "Oldest First"
                SortOption.DATE_MODIFIED_DESC -> "Recently Modified"
                SortOption.DATE_MODIFIED_ASC -> "Least Recently Modified"
                SortOption.TITLE_ASC -> "Title A-Z"
                SortOption.TITLE_DESC -> "Title Z-A"
                SortOption.CONTENT_LENGTH_DESC -> "Longest First"
                SortOption.CONTENT_LENGTH_ASC -> "Shortest First"
            }
        }

        fun getSortIcon(sortOption: SortOption): androidx.compose.ui.graphics.vector.ImageVector {
            return when (sortOption) {
                SortOption.DATE_CREATED_DESC, SortOption.DATE_MODIFIED_DESC ->
                    Icons.Outlined.Event
                SortOption.DATE_CREATED_ASC, SortOption.DATE_MODIFIED_ASC ->
                    Icons.Outlined.Event
                SortOption.TITLE_ASC, SortOption.TITLE_DESC ->
                    Icons.AutoMirrored.Outlined.Sort
                SortOption.CONTENT_LENGTH_DESC, SortOption.CONTENT_LENGTH_ASC ->
                    Icons.AutoMirrored.Outlined.Subject
            }
        }
    }
}

@Composable
fun rememberSearchAndSortManager(): SearchAndSortManager {
    return remember { SearchAndSortManager() }
}
