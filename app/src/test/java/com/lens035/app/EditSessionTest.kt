package com.lens035.app

import androidx.compose.ui.graphics.ImageBitmap
import com.lens035.app.ui.editor.EditSession
import com.lens035.core.model.EditState
import com.lens035.core.model.EffectType
import com.lens035.core.model.HistoryLabels
import com.lens035.core.model.LightSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditSessionTest {
    private class Session : EditSession(EditState()) {
        override val frameWidth = 100
        override val frameHeight = 100
        override suspend fun renderThumbnail(s: EditState): ImageBitmap? = null
    }

    private fun exposure(v: Float) = EditState(light = LightSettings(exposure = v))

    @Test
    fun stepsAreNamedAndUndoRedoWalkThem() {
        val s = Session()
        assertFalse(s.canUndo)
        s.apply(exposure(0.5f))
        s.update { it.withEffect(EffectType.GRAIN.defaultInstance()) }
        assertEquals(listOf(HistoryLabels.ORIGINAL, "Pozlama +0.50", EffectType.GRAIN.title + " eklendi"), s.history.map { it.label })
        assertEquals(2, s.historyIndex)

        s.undo()
        assertEquals(exposure(0.5f), s.state)
        assertTrue(s.canRedo)
        s.redo()
        assertTrue(s.state.hasEffect(EffectType.GRAIN))
        assertFalse(s.canRedo)
    }

    @Test
    fun jumpingBackKeepsLaterStepsUntilANewChange() {
        val s = Session()
        s.apply(exposure(0.2f))
        s.apply(exposure(0.4f))
        s.apply(exposure(0.6f))
        s.jumpTo(1)
        assertEquals(exposure(0.2f), s.state)
        assertEquals(4, s.history.size)
        assertTrue(s.canRedo)
        // A new change from step 1 replaces steps 2 and 3.
        s.apply(exposure(-0.3f))
        assertEquals(3, s.history.size)
        assertEquals("Pozlama -0.30", s.history.last().label)
        assertFalse(s.canRedo)
    }

    @Test
    fun aSliderDragIsOneStep() {
        val s = Session()
        s.beginGesture()
        for (i in 1..10) s.preview(exposure(i / 10f))
        s.endGesture()
        assertEquals(2, s.history.size)
        assertEquals("Pozlama +1.00", s.history.last().label)
        s.undo()
        assertEquals(EditState(), s.state)
    }

    @Test
    fun historyIsBoundedButKeepsTheOpeningState() {
        val s = Session()
        for (i in 1..120) s.apply(exposure(i / 100f))
        assertEquals(81, s.history.size)
        assertEquals(HistoryLabels.ORIGINAL, s.history.first().label)
        assertEquals(EditState(), s.history.first().state)
        s.jumpTo(0)
        assertEquals(EditState(), s.state)
    }
}
