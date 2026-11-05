package com.lens035.app.ui.editor

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.lens035.core.analysis.ImageStats
import com.lens035.core.model.EditState
import com.lens035.core.model.EffectType
import com.lens035.core.model.HistoryLabels

/** One step of the edit history: the edit after it, and what it changed. */
@Immutable
data class HistoryEntry(val state: EditState, val label: String)

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

    /**
     * Linear history like Lightroom's: entry 0 is the photo as opened, [historyIndex] is where
     * the edit is now. Undo/redo and [jumpTo] move the index; a new change drops the steps after it.
     */
    private val entries = mutableStateListOf(HistoryEntry(initial, HistoryLabels.ORIGINAL))
    val history: List<HistoryEntry> get() = entries
    var historyIndex by mutableIntStateOf(0)
        private set
    private var gestureStart: EditState? = null

    /** A discrete change (tap): recorded in history. */
    fun apply(newState: EditState) {
        if (newState == state) return
        record(state, newState)
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
            record(start, state)
            changed()
        }
    }

    fun undo() = jumpTo(historyIndex - 1)

    fun redo() = jumpTo(historyIndex + 1)

    /** Shows the edit as it was after history step [index]; later steps stay until a new change. */
    fun jumpTo(index: Int) {
        if (index !in entries.indices || index == historyIndex) return
        gestureStart = null
        historyIndex = index
        state = entries[index].state
        syncHistory()
        changed()
    }

    fun resetAll() = apply(EditState())

    /** Sets the starting edit without a history step (e.g. the film picked on the home screen). */
    protected fun startWith(initial: EditState) {
        gestureStart = null
        entries.clear()
        entries.add(HistoryEntry(initial, HistoryLabels.ORIGINAL))
        historyIndex = 0
        state = initial
        syncHistory()
    }

    private fun record(before: EditState, after: EditState) {
        while (entries.size > historyIndex + 1) entries.removeAt(entries.size - 1)
        entries.add(HistoryEntry(after, HistoryLabels.describe(before, after)))
        // Keep the opening state; forget the oldest steps after it.
        while (entries.size > MAX_HISTORY + 1) entries.removeAt(1)
        historyIndex = entries.size - 1
        syncHistory()
    }

    private fun syncHistory() {
        canUndo = historyIndex > 0
        canRedo = historyIndex < entries.size - 1
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
