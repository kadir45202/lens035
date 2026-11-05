package com.lens035.app.ui.editor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.lens035.core.analysis.ImageStats
import com.lens035.core.model.EditState
import com.lens035.core.model.EffectType

/**
 * What the tool panels need from an edit, shared by photos and videos: the current state,
 * history-aware mutation, thumbnails and the size of the source frame.
 */
@Stable
abstract class EditSession(initial: EditState) {
    var state by mutableStateOf(initial)
        protected set
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    var dirty by mutableStateOf(false)
        private set

    var stats by mutableStateOf(ImageStats.NEUTRAL)
        protected set
    var filmThumbs by mutableStateOf<Map<String, ImageBitmap>>(emptyMap())
        protected set
    var effectThumbs by mutableStateOf<Map<EffectType, ImageBitmap>>(emptyMap())
        protected set
    var lookThumbs by mutableStateOf<Map<String, ImageBitmap>>(emptyMap())
        protected set

    /** Width/height of the frame the pipeline works on (preview bitmap or video frame). */
    abstract val frameWidth: Int
    abstract val frameHeight: Int

    private val undoStack = ArrayDeque<EditState>()
    private val redoStack = ArrayDeque<EditState>()
    private var gestureStart: EditState? = null

    /** A discrete change (tap): recorded in history. */
    fun apply(newState: EditState) {
        if (newState == state) return
        pushUndo(state)
        state = newState
        changed()
    }

    fun update(transform: (EditState) -> EditState) = apply(transform(state))

    /** Continuous change (slider drag): wrap with [beginGesture] / [endGesture]. */
    fun preview(newState: EditState) {
        if (gestureStart == null) gestureStart = state
        state = newState
    }

    fun previewUpdate(transform: (EditState) -> EditState) = preview(transform(state))

    fun beginGesture() {
        if (gestureStart == null) gestureStart = state
    }

    fun endGesture() {
        val start = gestureStart ?: return
        gestureStart = null
        if (start != state) {
            pushUndo(start)
            changed()
        }
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(state)
        state = prev
        syncHistory()
        changed()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(state)
        state = next
        syncHistory()
        changed()
    }

    fun resetAll() = apply(EditState())

    private fun pushUndo(s: EditState) {
        undoStack.addLast(s)
        while (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
        syncHistory()
    }

    private fun syncHistory() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    private fun changed() {
        dirty = true
        onChanged()
    }

    /** Hook for autosave. */
    protected open fun onChanged() {}

    /** Renders a small preview of an arbitrary state (user presets); null if unavailable. */
    abstract suspend fun renderThumbnail(s: EditState): ImageBitmap?

    /** A small packed-ARGB copy of the picture for analysis (auto upright), or null if not ready. */
    open suspend fun analysisPixels(): AnalysisPixels? = null

    companion object {
        private const val MAX_HISTORY = 80
    }
}

/** Packed ARGB pixels of the unedited picture in source orientation. */
class AnalysisPixels(val argb: IntArray, val width: Int, val height: Int)
