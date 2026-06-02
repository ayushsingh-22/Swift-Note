package com.amvarpvtltd.swiftNote.search

import com.amvarpvtltd.swiftNote.Note
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class SearchAndSortPhase4Test {

    private val pinnedNote = Note(
        title = "Pinned task",
        description = "Important",
        isPinned = true,
        timestamp = 1000L,
        updatedAt = 1000L
    )
    private val workNote = Note(
        title = "Work meeting",
        description = "Agenda",
        category = "Work",
        timestamp = 2000L,
        updatedAt = 2000L
    )
    private val personalNote = Note(
        title = "Groceries",
        description = "Buy milk",
        category = "Personal",
        timestamp = 3000L,
        updatedAt = 3000L
    )
    private val uncategorized = Note(
        title = "Random",
        description = "Stuff",
        timestamp = 4000L,
        updatedAt = 4000L
    )

    private fun createManager(scope: CoroutineScope) = SearchAndSortManager(
        scope = scope,
        initialNotes = listOf(pinnedNote, workNote, personalNote, uncategorized)
    )

    @Test
    fun pinnedNotesAppearFirstInSort() = runTest {
        val managerScope = CoroutineScope(coroutineContext + Job())
        val manager = createManager(managerScope)

        advanceUntilIdle()

        val state = manager.searchAndSortState.value
        assertEquals(pinnedNote.title, state.filteredNotes.first().title)
        managerScope.cancel()
    }

    @Test
    fun categoryFilterShowsOnlyMatchingNotes() = runTest {
        val managerScope = CoroutineScope(coroutineContext + Job())
        val manager = createManager(managerScope)

        manager.updateCategoryFilter("Work")
        advanceUntilIdle()

        val state = manager.searchAndSortState.value
        assertEquals(1, state.filteredNotes.size)
        assertEquals("Work meeting", state.filteredNotes[0].title)
        managerScope.cancel()
    }

    @Test
    fun emptyCategoryFilterShowsAllNotes() = runTest {
        val managerScope = CoroutineScope(coroutineContext + Job())
        val manager = createManager(managerScope)

        manager.updateCategoryFilter("")
        advanceUntilIdle()

        val state = manager.searchAndSortState.value
        assertEquals(4, state.filteredNotes.size)
        managerScope.cancel()
    }

    @Test
    fun availableCategoriesReturnsDistinctNonEmptyCategories() = runTest {
        val managerScope = CoroutineScope(coroutineContext + Job())
        val manager = createManager(managerScope)

        advanceUntilIdle()

        val cats = manager.availableCategories.value
        assertTrue(cats.contains("Work"))
        assertTrue(cats.contains("Personal"))
        assertEquals(2, cats.size)
        managerScope.cancel()
    }

    @Test
    fun pinnedNotesStayFirstEvenWithTitleSort() = runTest {
        val managerScope = CoroutineScope(coroutineContext + Job())
        val manager = createManager(managerScope)

        manager.updateSortOption(SortOption.TITLE_ASC)
        advanceUntilIdle()

        val state = manager.searchAndSortState.value
        assertTrue(state.filteredNotes.first().isPinned)
        managerScope.cancel()
    }
}
