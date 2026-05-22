package com.amvarpvtltd.swiftNote.reminders

import java.util.Calendar

/**
 * Pure-function calculator for recurring reminder next-occurrence.
 * Handles: DAILY, WEEKLY, MONTHLY, YEARLY with intervals and day-of-week selection.
 *
 * Risk mitigation:
 * - Uses Calendar.getInstance() for timezone-aware calculations (fires at local time after TZ change)
 * - Returns null if recurrence has ended (respects endDate)
 * - Guards against alarm storm: never schedules past occurrences
 */
object RecurrenceCalculator {

    /**
     * Calculates the next occurrence for a recurring reminder.
     *
     * @param currentReminderTime The time the current reminder was scheduled for
     * @param recurrenceType One of NONE, DAILY, WEEKLY, MONTHLY, YEARLY
     * @param recurrenceInterval How many units between occurrences (default 1)
     * @param recurrenceDaysOfWeek CSV of Calendar day constants (e.g., "2,4,6" for Mon/Wed/Fri)
     * @param recurrenceEndDate Optional end timestamp — returns null if next occurrence is after this
     * @param after Calculate next occurrence after this timestamp (default: now). Prevents scheduling in the past.
     * @return The next occurrence timestamp, or null if recurrence has ended
     */
    fun getNextOccurrence(
        currentReminderTime: Long,
        recurrenceType: String,
        recurrenceInterval: Int = 1,
        recurrenceDaysOfWeek: String? = null,
        recurrenceEndDate: Long? = null,
        after: Long = System.currentTimeMillis()
    ): Long? {
        if (recurrenceType == RecurrenceType.NONE) return null

        val interval = recurrenceInterval.coerceAtLeast(1)

        // Start from currentReminderTime and find the next valid time after 'after'
        val calendar = Calendar.getInstance().apply {
            timeInMillis = currentReminderTime
        }

        // Preserve hour/minute/second from original
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val second = calendar.get(Calendar.SECOND)

        val nextTime = when (recurrenceType) {
            RecurrenceType.DAILY -> calculateNextDaily(calendar, interval, after)
            RecurrenceType.WEEKLY -> {
                if (!recurrenceDaysOfWeek.isNullOrBlank()) {
                    calculateNextWeekday(calendar, interval, recurrenceDaysOfWeek, after)
                } else {
                    calculateNextWeekly(calendar, interval, after)
                }
            }
            RecurrenceType.MONTHLY -> calculateNextMonthly(calendar, interval, after)
            RecurrenceType.YEARLY -> calculateNextYearly(calendar, interval, after)
            else -> null
        } ?: return null

        // Check end date
        if (recurrenceEndDate != null && nextTime > recurrenceEndDate) {
            return null
        }

        return nextTime
    }

    private fun calculateNextDaily(cal: Calendar, interval: Int, after: Long): Long {
        while (cal.timeInMillis <= after) {
            cal.add(Calendar.DAY_OF_MONTH, interval)
        }
        return cal.timeInMillis
    }

    private fun calculateNextWeekly(cal: Calendar, interval: Int, after: Long): Long {
        while (cal.timeInMillis <= after) {
            cal.add(Calendar.WEEK_OF_YEAR, interval)
        }
        return cal.timeInMillis
    }

    private fun calculateNextWeekday(
        cal: Calendar,
        interval: Int,
        daysOfWeekCsv: String,
        after: Long
    ): Long? {
        val targetDays = daysOfWeekCsv.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in Calendar.SUNDAY..Calendar.SATURDAY }
            .sorted()

        if (targetDays.isEmpty()) return null

        // Move forward from current time until we find a matching day after 'after'
        val startCal = Calendar.getInstance().apply {
            timeInMillis = cal.timeInMillis
        }

        // Preserve time of day
        val hour = startCal.get(Calendar.HOUR_OF_DAY)
        val minute = startCal.get(Calendar.MINUTE)

        // Move to next day after 'after'
        val searchCal = Calendar.getInstance().apply {
            timeInMillis = maxOf(cal.timeInMillis, after)
            // Move to tomorrow if current time has passed
            if (timeInMillis <= after) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Search up to 8 weeks (with interval) to prevent infinite loop
        val maxSearch = 7 * 8 * interval
        repeat(maxSearch) {
            val dayOfWeek = searchCal.get(Calendar.DAY_OF_WEEK)
            if (dayOfWeek in targetDays && searchCal.timeInMillis > after) {
                return searchCal.timeInMillis
            }
            searchCal.add(Calendar.DAY_OF_MONTH, 1)
        }

        return null
    }

    private fun calculateNextMonthly(cal: Calendar, interval: Int, after: Long): Long {
        val targetDay = cal.get(Calendar.DAY_OF_MONTH)

        while (cal.timeInMillis <= after) {
            cal.add(Calendar.MONTH, interval)
            // Handle month-end overflow: Jan 31 → Feb 28/29
            val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            cal.set(Calendar.DAY_OF_MONTH, minOf(targetDay, maxDay))
        }
        return cal.timeInMillis
    }

    private fun calculateNextYearly(cal: Calendar, interval: Int, after: Long): Long {
        val targetMonth = cal.get(Calendar.MONTH)
        val targetDay = cal.get(Calendar.DAY_OF_MONTH)

        while (cal.timeInMillis <= after) {
            cal.add(Calendar.YEAR, interval)
            // Handle Feb 29 in non-leap years → Feb 28
            cal.set(Calendar.MONTH, targetMonth)
            val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            cal.set(Calendar.DAY_OF_MONTH, minOf(targetDay, maxDay))
        }
        return cal.timeInMillis
    }
}

/**
 * Recurrence type constants.
 */
object RecurrenceType {
    const val NONE = "NONE"
    const val DAILY = "DAILY"
    const val WEEKLY = "WEEKLY"
    const val MONTHLY = "MONTHLY"
    const val YEARLY = "YEARLY"
}

