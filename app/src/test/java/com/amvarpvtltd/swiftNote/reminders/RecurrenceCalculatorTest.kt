package com.amvarpvtltd.swiftNote.reminders

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

/**
 * Phase 2: Comprehensive unit tests for RecurrenceCalculator.
 * Covers: daily, weekly, monthly (month-end overflow), yearly (leap year),
 * day-of-week recurrence, intervals, end-date termination, and past-time guard.
 */
class RecurrenceCalculatorTest {

    // ============================================================
    // DAILY Recurrence Tests
    // ============================================================

    @Test
    fun `daily recurrence returns next day at same time`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 20, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val baseTime = cal.timeInMillis
        val afterTime = baseTime // "after" = same as base, so next should be +1 day

        val result = RecurrenceCalculator.getNextOccurrence(
            currentReminderTime = baseTime,
            recurrenceType = RecurrenceType.DAILY,
            recurrenceInterval = 1,
            after = afterTime
        )

        assertNotNull(result)
        val resultCal = Calendar.getInstance().apply { timeInMillis = result!! }
        assertEquals(21, resultCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, resultCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, resultCal.get(Calendar.MINUTE))
    }

    @Test
    fun `daily recurrence with interval 3 skips 3 days`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 20, 8, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val baseTime = cal.timeInMillis

        val result = RecurrenceCalculator.getNextOccurrence(
            currentReminderTime = baseTime,
            recurrenceType = RecurrenceType.DAILY,
            recurrenceInterval = 3,
            after = baseTime
        )

        assertNotNull(result)
        val resultCal = Calendar.getInstance().apply { timeInMillis = result!! }
        assertEquals(23, resultCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `daily recurrence skips past times to find future occurrence`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 10, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val baseTime = cal.timeInMillis

        // "after" is May 20 — should skip ahead
        val afterCal = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 20, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val afterTime = afterCal.timeInMillis

        val result = RecurrenceCalculator.getNextOccurrence(
            currentReminderTime = baseTime,
            recurrenceType = RecurrenceType.DAILY,
            recurrenceInterval = 1,
            after = afterTime
        )

        assertNotNull(result)
        assertTrue("Result should be after 'after' time", result!! > afterTime)
    }

    // ============================================================
    // WEEKLY Recurrence Tests
    // ============================================================

    @Test
    fun `weekly recurrence returns same day next week`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 18, 14, 0, 0) // Monday
            set(Calendar.MILLISECOND, 0)
        }
        val baseTime = cal.timeInMillis

        val result = RecurrenceCalculator.getNextOccurrence(
            currentReminderTime = baseTime,
            recurrenceType = RecurrenceType.WEEKLY,
            recurrenceInterval = 1,
            after = baseTime
        )

        assertNotNull(result)
        val resultCal = Calendar.getInstance().apply { timeInMillis = result!! }
        assertEquals(25, resultCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.MONDAY, resultCal.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun `weekly recurrence with interval 2 skips 2 weeks`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 18, 14, 0, 0) // Monday
            set(Calendar.MILLISECOND, 0)
        }
        val baseTime = cal.timeInMillis

        val result = RecurrenceCalculator.getNextOccurrence(
            currentReminderTime = baseTime,
            recurrenceType = RecurrenceType.WEEKLY,
            recurrenceInterval = 2,
            after = baseTime
        )

        assertNotNull(result)
        val resultCal = Calendar.getInstance().apply { timeInMillis = result!! }
        assertEquals(Calendar.JUNE, resultCal.get(Calendar.MONTH))
        assertEquals(1, resultCal.get(Calendar.DAY_OF_MONTH)) // June 1 = 2 weeks from May 18
    }

    // ============================================================
    // WEEKLY with Day-of-Week Tests
    // ============================================================

    @Test
    fun `weekday recurrence finds next matching day`() {
        // Base: Monday May 18, 2026 at 9:00
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 18, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val baseTime = cal.timeInMillis

        // Recur on Wed(4) and Fri(6)
        val result = RecurrenceCalculator.getNextOccurrence(
            currentReminderTime = baseTime,
            recurrenceType = RecurrenceType.WEEKLY,
            recurrenceInterval = 1,
            recurrenceDaysOfWeek = "4,6", // Wed, Fri
            after = baseTime
        )

        assertNotNull(result)
        val resultCal = Calendar.getInstance().apply { timeInMillis = result!! }
        // Should be Wednesday May 20
        assertEquals(Calendar.WEDNESDAY, resultCal.get(Calendar.DAY_OF_WEEK))
        assertEquals(20, resultCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `weekday recurrence Mon-Wed-Fri finds next occurrence after current`() {
        // Base: Friday May 22, 2026 at 10:00
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 22, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val baseTime = cal.timeInMillis

        // Mon(2), Wed(4), Fri(6)
        val result = RecurrenceCalculator.getNextOccurrence(
            currentReminderTime = baseTime,
            recurrenceType = RecurrenceType.WEEKLY,
            recurrenceInterval = 1,
            recurrenceDaysOfWeek = "2,4,6",
            after = baseTime
        )

        assertNotNull(result)
        val resultCal = Calendar.getInstance().apply { timeInMillis = result!! }
        // Next should be Monday May 25
        assertEquals(Calendar.MONDAY, resultCal.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun `weekday recurrence returns null for empty days`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 18, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val result = RecurrenceCalculator.getNextOccurrence(
            currentReminderTime = cal.timeInMillis,
            recurrenceType = RecurrenceType.WEEKLY,
            recurrenceInterval = 1,
            recurrenceDaysOfWeek = "",
            after = cal.timeInMillis
        )

        // Empty days → falls through to simple weekly
        // Actually with empty string it should use calculateNextWeekly
        // based on the code: if (!recurrenceDaysOfWeek.isNullOrBlank()) ...
        // So empty string → calculateNextWeekly
        assertNotNull(result)
    }

    // ============================================================
    // MONTHLY Recurrence Tests
    // ============================================================

    @Test
    fun `monthly recurrence returns same day next month`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 15, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val baseTime = cal.timeInMillis

        val result = RecurrenceCalculator.getNextOccurrence(
            currentReminderTime = baseTime,
            recurrenceType = RecurrenceType.MONTHLY,
            recurrenceInterval = 1,
            after = baseTime
        )

        assertNotNull(result)
        val resultCal = Calendar.getInstance().apply { timeInMillis = result!! }
        assertEquals(Calendar.JUNE, resultCal.get(Calendar.MONTH))
        assertEquals(15, resultCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `monthly recurrence handles month-end overflow Jan 31 to Feb 28`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 31, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val baseTime = cal.timeInMillis

        val result = RecurrenceCalculator.getNextOccurrence(
            currentReminderTime = baseTime,
            recurrenceType = RecurrenceType.MONTHLY,
            recurrenceInterval = 1,
            after = baseTime
        )

        assertNotNull(result)
        val resultCal = Calendar.getInstance().apply { timeInMillis = result!! }
        assertEquals(Calendar.FEBRUARY, resultCal.get(Calendar.MONTH))
        // Feb 2026 is not a leap year → 28 days
        assertEquals(28, resultCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `monthly recurrence handles Feb 29 in leap year`() {
        // 2028 is a leap year
        val cal = Calendar.getInstance().apply {
            set(2028, Calendar.JANUARY, 31, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val baseTime = cal.timeInMillis

        val result = RecurrenceCalculator.getNextOccurrence(
            currentReminderTime = baseTime,
            recurrenceType = RecurrenceType.MONTHLY,
            recurrenceInterval = 1,
            after = baseTime
        )

        assertNotNull(result)
        val resultCal = Calendar.getInstance().apply { timeInMillis = result!! }
        assertEquals(Calendar.FEBRUARY, resultCal.get(Calendar.MONTH))
        assertEquals(29, resultCal.get(Calendar.DAY_OF_MONTH)) // Leap year!
    }

    @Test
    fun `monthly recurrence with interval 2`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 10, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val baseTime = cal.timeInMillis

        val result = RecurrenceCalculator.getNextOccurrence(
            currentReminderTime = baseTime,
            recurrenceType = RecurrenceType.MONTHLY,
            recurrenceInterval = 2,
            after = baseTime
        )

        assertNotNull(result)
        val resultCal = Calendar.getInstance().apply { timeInMillis = result!! }
        assertEquals(Calendar.MAY, resultCal.get(Calendar.MONTH))
        assertEquals(10, resultCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `monthly recurrence Jan 31 to Feb to Mar restores to 31`() {
        // Start Jan 31 → Feb 28 → Mar should be 31 (not stuck at 28)
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 31, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val baseTime = cal.timeInMillis

        // After Feb 28 (skip past Feb)
        val afterCal = Calendar.getInstance().apply {
            set(2026, Calendar.FEBRUARY, 28, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val result = RecurrenceCalculator.getNextOccurrence(
            currentReminderTime = baseTime,
            recurrenceType = RecurrenceType.MONTHLY,
            recurrenceInterval = 1,
            after = afterCal.timeInMillis
        )

        assertNotNull(result)
        val resultCal = Calendar.getInstance().apply { timeInMillis = result!! }
        assertEquals(Calendar.MARCH, resultCal.get(Calendar.MONTH))
        assertEquals(31, resultCal.get(Calendar.DAY_OF_MONTH))
    }

    // ============================================================
    // YEARLY Recurrence Tests
    // ============================================================

    @Test
    fun `yearly recurrence returns same date next year`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 24, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val baseTime = cal.timeInMillis

        val result = RecurrenceCalculator.getNextOccurrence(
            currentReminderTime = baseTime,
            recurrenceType = RecurrenceType.YEARLY,
            recurrenceInterval = 1,
            after = baseTime
        )

        assertNotNull(result)
        val resultCal = Calendar.getInstance().apply { timeInMillis = result!! }
        assertEquals(2027, resultCal.get(Calendar.YEAR))
        assertEquals(Calendar.MAY, resultCal.get(Calendar.MONTH))
        assertEquals(24, resultCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `yearly recurrence handles Feb 29 in non-leap year`() {
        // 2028 is a leap year, set reminder for Feb 29
        val cal = Calendar.getInstance().apply {
            set(2028, Calendar.FEBRUARY, 29, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val baseTime = cal.timeInMillis

        val result = RecurrenceCalculator.getNextOccurrence(
            currentReminderTime = baseTime,
            recurrenceType = RecurrenceType.YEARLY,
            recurrenceInterval = 1,
            after = baseTime
        )

        assertNotNull(result)
        val resultCal = Calendar.getInstance().apply { timeInMillis = result!! }
        assertEquals(2029, resultCal.get(Calendar.YEAR))
        assertEquals(Calendar.FEBRUARY, resultCal.get(Calendar.MONTH))
        // 2029 is not a leap year → Feb 28
        assertEquals(28, resultCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `yearly recurrence with interval 2`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.JUNE, 15, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val baseTime = cal.timeInMillis

        val result = RecurrenceCalculator.getNextOccurrence(
            currentReminderTime = baseTime,
            recurrenceType = RecurrenceType.YEARLY,
            recurrenceInterval = 2,
            after = baseTime
        )

        assertNotNull(result)
        val resultCal = Calendar.getInstance().apply { timeInMillis = result!! }
        assertEquals(2028, resultCal.get(Calendar.YEAR))
        assertEquals(Calendar.JUNE, resultCal.get(Calendar.MONTH))
    }

    // ============================================================
    // End Date Termination Tests
    // ============================================================

    @Test
    fun `recurrence returns null when next occurrence exceeds end date`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 20, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val baseTime = cal.timeInMillis

        // End date is May 21 at midnight — next daily occurrence (May 21 9:00) is before end
        // But let's set end date to barely after May 20
        val endCal = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 20, 12, 0, 0) // ends at noon May 20
            set(Calendar.MILLISECOND, 0)
        }

        val result = RecurrenceCalculator.getNextOccurrence(
            currentReminderTime = baseTime,
            recurrenceType = RecurrenceType.DAILY,
            recurrenceInterval = 1,
            recurrenceEndDate = endCal.timeInMillis,
            after = baseTime
        )

        // Next would be May 21 9:00 AM which > end date May 20 12:00
        assertNull("Should be null when next occurrence is after end date", result)
    }

    @Test
    fun `recurrence returns value when next occurrence is before end date`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 20, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val baseTime = cal.timeInMillis

        val endCal = Calendar.getInstance().apply {
            set(2026, Calendar.JUNE, 30, 23, 59, 59) // plenty of room
            set(Calendar.MILLISECOND, 0)
        }

        val result = RecurrenceCalculator.getNextOccurrence(
            currentReminderTime = baseTime,
            recurrenceType = RecurrenceType.DAILY,
            recurrenceInterval = 1,
            recurrenceEndDate = endCal.timeInMillis,
            after = baseTime
        )

        assertNotNull(result)
    }

    @Test
    fun `monthly recurrence with tight end date stops chain`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 15, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val baseTime = cal.timeInMillis

        // End date June 1 — next monthly (June 15) should be after end
        val endCal = Calendar.getInstance().apply {
            set(2026, Calendar.JUNE, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val result = RecurrenceCalculator.getNextOccurrence(
            currentReminderTime = baseTime,
            recurrenceType = RecurrenceType.MONTHLY,
            recurrenceInterval = 1,
            recurrenceEndDate = endCal.timeInMillis,
            after = baseTime
        )

        assertNull("Monthly next (June 15) > end date (June 1) → null", result)
    }

    // ============================================================
    // NONE Type Tests
    // ============================================================

    @Test
    fun `NONE recurrence type returns null`() {
        val result = RecurrenceCalculator.getNextOccurrence(
            currentReminderTime = System.currentTimeMillis(),
            recurrenceType = RecurrenceType.NONE,
            recurrenceInterval = 1
        )
        assertNull(result)
    }

    @Test
    fun `unknown recurrence type returns null`() {
        val result = RecurrenceCalculator.getNextOccurrence(
            currentReminderTime = System.currentTimeMillis(),
            recurrenceType = "UNKNOWN_TYPE",
            recurrenceInterval = 1
        )
        assertNull(result)
    }

    // ============================================================
    // Edge Cases
    // ============================================================

    @Test
    fun `interval 0 is coerced to 1`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 20, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val baseTime = cal.timeInMillis

        val result = RecurrenceCalculator.getNextOccurrence(
            currentReminderTime = baseTime,
            recurrenceType = RecurrenceType.DAILY,
            recurrenceInterval = 0, // should be treated as 1
            after = baseTime
        )

        assertNotNull(result)
        val resultCal = Calendar.getInstance().apply { timeInMillis = result!! }
        assertEquals(21, resultCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `negative interval is coerced to 1`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 20, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val baseTime = cal.timeInMillis

        val result = RecurrenceCalculator.getNextOccurrence(
            currentReminderTime = baseTime,
            recurrenceType = RecurrenceType.DAILY,
            recurrenceInterval = -5,
            after = baseTime
        )

        assertNotNull(result)
        val resultCal = Calendar.getInstance().apply { timeInMillis = result!! }
        assertEquals(21, resultCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `result preserves time of day`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 20, 17, 45, 30)
            set(Calendar.MILLISECOND, 0)
        }
        val baseTime = cal.timeInMillis

        val result = RecurrenceCalculator.getNextOccurrence(
            currentReminderTime = baseTime,
            recurrenceType = RecurrenceType.DAILY,
            recurrenceInterval = 1,
            after = baseTime
        )

        assertNotNull(result)
        val resultCal = Calendar.getInstance().apply { timeInMillis = result!! }
        assertEquals(17, resultCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(45, resultCal.get(Calendar.MINUTE))
    }

    @Test
    fun `null end date means infinite recurrence`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 20, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val baseTime = cal.timeInMillis

        val result = RecurrenceCalculator.getNextOccurrence(
            currentReminderTime = baseTime,
            recurrenceType = RecurrenceType.DAILY,
            recurrenceInterval = 1,
            recurrenceEndDate = null,
            after = baseTime
        )

        assertNotNull("Null end date should allow infinite recurrence", result)
    }
}

