package com.amvarpvtltd.swiftNote.search

import com.amvarpvtltd.swiftNote.Note
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for SearchAndSortManager covering:
 * - BUG-020: Sort options produce correct order
 * - BUG-033: Lifecycle-aware scope handling
 * - Search relevance scoring
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class SearchAndSortManagerTest {

    private lateinit var scope: CoroutineScope
    private lateinit var manager: SearchAndSortManager
    private lateinit var collectorJob: Job

    private val sampleNotes = listOf(
        Note(title = "Alpha Meeting", description = "Discuss project", id = "1", timestamp = 1000L),
        Note(title = "Beta Testing", description = "Run tests", id = "2", timestamp = 2000L),
        Note(title = "Charlie Sprint", description = "Plan iteration", id = "3", timestamp = 3000L),
        Note(title = "Delta Review", description = "Code review session", id = "4", timestamp = 4000L),
        Note(title = "Alpha Report", description = "Monthly meeting notes", id = "5", timestamp = 5000L)
    )

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        manager = SearchAndSortManager(scope)
        // Subscribe to trigger WhileSubscribed upstream collection
        collectorJob = scope.launch {
            manager.searchAndSortState.collect { /* keep subscription alive */ }
        }
    }

    @After
    fun tearDown() {
        collectorJob.cancel()
        scope.cancel()
    }

    private fun waitForDebounce() {
        // The debounce is 300ms + combine emission + stateIn propagation
        Thread.sleep(800)
    }

    // ============================================================
    // Sort Tests (sorted notes via StateFlow after debounce)
    // ============================================================

    @Test
    fun `sort by DATE_CREATED_DESC returns newest first`() {
        manager.updateNotes(sampleNotes)
        manager.updateSortOption(SortOption.DATE_CREATED_DESC)
        waitForDebounce()
        val notes = manager.searchAndSortState.value.filteredNotes
        assertTrue("Should have notes", notes.isNotEmpty())
        assertEquals("5", notes.first().id)
        assertEquals("1", notes.last().id)
    }

    @Test
    fun `sort by DATE_CREATED_ASC returns oldest first`() {
        manager.updateNotes(sampleNotes)
        manager.updateSortOption(SortOption.DATE_CREATED_ASC)
        waitForDebounce()
        val notes = manager.searchAndSortState.value.filteredNotes
        assertTrue(notes.isNotEmpty())
        assertEquals("1", notes.first().id)
        assertEquals("5", notes.last().id)
    }

    @Test
    fun `sort by TITLE_ASC returns alphabetical order`() {
        manager.updateNotes(sampleNotes)
        manager.updateSortOption(SortOption.TITLE_ASC)
        waitForDebounce()
        val titles = manager.searchAndSortState.value.filteredNotes.map { it.title }
        assertTrue(titles.size == 5)
        assertEquals("Alpha Meeting", titles[0])
        assertEquals("Alpha Report", titles[1])
        assertEquals("Delta Review", titles.last())
    }

    @Test
    fun `sort by TITLE_DESC returns reverse alphabetical`() {
        manager.updateNotes(sampleNotes)
        manager.updateSortOption(SortOption.TITLE_DESC)
        waitForDebounce()
        val titles = manager.searchAndSortState.value.filteredNotes.map { it.title }
        assertTrue(titles.size == 5)
        assertEquals("Delta Review", titles[0])
        assertEquals("Alpha Meeting", titles.last())
    }

    @Test
    fun `sort by CONTENT_LENGTH_DESC returns longest first`() {
        val notes = listOf(
            Note(title = "A", description = "B", id = "1", timestamp = 1000L),
            Note(title = "Medium Title", description = "Medium desc", id = "2", timestamp = 2000L),
            Note(title = "Very Long Title Here", description = "Much longer description text", id = "3", timestamp = 3000L)
        )
        manager.updateNotes(notes)
        manager.updateSortOption(SortOption.CONTENT_LENGTH_DESC)
        waitForDebounce()
        val result = manager.searchAndSortState.value.filteredNotes
        assertTrue(result.size == 3)
        assertEquals("3", result.first().id)
        assertEquals("1", result.last().id)
    }

    // ============================================================
    // Search Tests
    // ============================================================

    @Test
    fun `search filters notes by title`() {
        manager.updateNotes(sampleNotes)
        waitForDebounce()
        manager.updateSearchQuery("Alpha")
        waitForDebounce()
        val state = manager.searchAndSortState.value
        assertEquals(2, state.filteredNotes.size)
        assertTrue(state.filteredNotes.all { it.title.contains("Alpha") })
    }

    @Test
    fun `search filters notes by description`() {
        manager.updateNotes(sampleNotes)
        waitForDebounce()
        manager.updateSearchQuery("code review")
        waitForDebounce()
        val state = manager.searchAndSortState.value
        assertEquals(1, state.filteredNotes.size)
        assertEquals("4", state.filteredNotes.first().id)
    }

    @Test
    fun `search is case insensitive`() {
        manager.updateNotes(sampleNotes)
        waitForDebounce()
        manager.updateSearchQuery("ALPHA")
        waitForDebounce()
        assertEquals(2, manager.searchAndSortState.value.filteredNotes.size)
    }

    @Test
    fun `empty search returns all notes`() {
        manager.updateNotes(sampleNotes)
        manager.updateSearchQuery("")
        waitForDebounce()
        assertEquals(sampleNotes.size, manager.searchAndSortState.value.filteredNotes.size)
    }

    @Test
    fun `search with no matches returns empty list`() {
        manager.updateNotes(sampleNotes)
        waitForDebounce()
        manager.updateSearchQuery("xyz123nonexistent")
        waitForDebounce()
        assertEquals(0, manager.searchAndSortState.value.filteredNotes.size)
    }

    @Test
    fun `clearSearch resets and shows all notes`() {
        manager.updateNotes(sampleNotes)
        waitForDebounce()
        manager.updateSearchQuery("Alpha")
        waitForDebounce()
        assertEquals(2, manager.searchAndSortState.value.filteredNotes.size)

        manager.clearSearch()
        waitForDebounce()
        assertEquals(sampleNotes.size, manager.searchAndSortState.value.filteredNotes.size)
    }

    @Test
    fun `isSearchActive is true when query non-blank`() {
        manager.updateNotes(sampleNotes)
        waitForDebounce()
        manager.updateSearchQuery("test")
        waitForDebounce()
        assertTrue(manager.searchAndSortState.value.isSearchActive)
    }

    @Test
    fun `isSearchActive is false when query blank`() {
        manager.updateNotes(sampleNotes)
        manager.updateSearchQuery("")
        waitForDebounce()
        assertFalse(manager.searchAndSortState.value.isSearchActive)
    }

    @Test
    fun `title match ranks higher than description match`() {
        val notes = listOf(
            Note(title = "Shopping List", description = "Buy meeting snacks", id = "1", timestamp = 1000L),
            Note(title = "Meeting Notes", description = "Regular sync", id = "2", timestamp = 2000L)
        )
        manager.updateNotes(notes)
        waitForDebounce()
        manager.updateSearchQuery("meeting")
        waitForDebounce()
        val state = manager.searchAndSortState.value
        assertEquals(2, state.filteredNotes.size)
        // Title match ("Meeting Notes") should rank higher
        assertEquals("2", state.filteredNotes.first().id)
    }

    @Test
    fun `sort empty list returns empty`() {
        manager.updateNotes(emptyList())
        manager.updateSortOption(SortOption.TITLE_ASC)
        waitForDebounce()
        assertTrue(manager.searchAndSortState.value.filteredNotes.isEmpty())
    }

    // ============================================================
    // Companion Object Tests (no async)
    // ============================================================

    @Test
    fun `getSortOptionLabel returns correct labels`() {
        assertEquals("Newest First", SearchAndSortManager.getSortOptionLabel(SortOption.DATE_CREATED_DESC))
        assertEquals("Oldest First", SearchAndSortManager.getSortOptionLabel(SortOption.DATE_CREATED_ASC))
        assertEquals("Title A-Z", SearchAndSortManager.getSortOptionLabel(SortOption.TITLE_ASC))
        assertEquals("Title Z-A", SearchAndSortManager.getSortOptionLabel(SortOption.TITLE_DESC))
        assertEquals("Longest First", SearchAndSortManager.getSortOptionLabel(SortOption.CONTENT_LENGTH_DESC))
        assertEquals("Shortest First", SearchAndSortManager.getSortOptionLabel(SortOption.CONTENT_LENGTH_ASC))
    }
}





