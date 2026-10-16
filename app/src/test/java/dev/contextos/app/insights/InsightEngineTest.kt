package dev.contextos.app.insights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightEngineTest {
    @Test fun optOutProducesNoInsights() {
        val insights = InsightEngine.recommendations(
            InsightInput(enabled = false, sensing = true, ambientLux = 20f, appUseMinutes = 300)
        )
        assertTrue(insights.isEmpty())
    }

    @Test fun lowLightAndUsageHaveExplainableSources() {
        val insights = InsightEngine.recommendations(
            InsightInput(enabled = true, sensing = true, ambientLux = 18f, appUseMinutes = 220)
        )
        assertEquals("light", insights.first().id)
        assertTrue(insights.any { it.id == "focus" })
        assertTrue(insights.all { it.basedOn.isNotBlank() })
    }

    @Test fun negativeOrUnknownReadingsDoNotTriggerAdvice() {
        val insights = InsightEngine.recommendations(
            InsightInput(enabled = true, sensing = false, ambientLux = 15f, appUseMinutes = null)
        )
        assertFalse(insights.any { it.id == "light" || it.id == "focus" })
        assertEquals("check-in", insights.single().id)
    }

    @Test fun recommendationsAreLimitedAndDeterministic() {
        val input = InsightInput(enabled = true, sensing = true, ambientLux = 20f,
            motionLabel = "Low motion", stepsSinceOpen = 1234,
            appUseMinutes = 250, hasSavedSnapshot = true)
        assertEquals(3, InsightEngine.recommendations(input).size)
        assertEquals(InsightEngine.recommendations(input), InsightEngine.recommendations(input))
    }
}
