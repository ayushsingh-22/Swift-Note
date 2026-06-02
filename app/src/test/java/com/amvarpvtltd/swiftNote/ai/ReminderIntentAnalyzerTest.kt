package com.amvarpvtltd.swiftNote.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderIntentAnalyzerTest {

    @Test
    fun `standalone time does not trigger reminder intent`() {
        assertFalse(ReminderIntentAnalyzer.hasReminderIntent(noteBody = "5pm"))
    }

    @Test
    fun `meeting tomorrow at time triggers reminder intent`() {
        assertTrue(
            ReminderIntentAnalyzer.hasReminderIntent(
                noteBody = "Meeting with Rahul tomorrow at 5pm"
            )
        )
    }

    @Test
    fun `action sentence plus next sentence time still triggers`() {
        val analysisText = ReminderIntentAnalyzer.buildAnalysisText(
            noteBody = "Doctor appointment. Tomorrow 5pm."
        )

        assertNotNull(analysisText)
        assertTrue(analysisText!!.contains("Doctor appointment", ignoreCase = true))
        assertTrue(analysisText.contains("Tomorrow 5pm", ignoreCase = true))
    }

    @Test
    fun `informational prose with ratio does not trigger reminder intent`() {
        val note = """
            "N1 material as per IS 15997" on your water bottle means the bottle is made using a specific grade of stainless steel.
            This marking generally indicates corrosion resistance and safer material quality.
            For water bottles, 304 stainless steel is usually considered premium.
            N1 material is a lower-nickel alternative for normal daily water use.
            You can also check whether the bottle has "18/8 stainless steel".
        """.trimIndent()

        assertFalse(ReminderIntentAnalyzer.hasReminderIntent(noteBody = note))
        assertTrue(ReminderIntentAnalyzer.buildAnalysisText(noteBody = note).isNullOrBlank())
    }

    @Test
    fun `numeric date with action is still allowed`() {
        assertTrue(
            ReminderIntentAnalyzer.hasReminderIntent(
                noteBody = "Dentist appointment 05/09 at 5pm"
            )
        )
    }
}
