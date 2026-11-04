package com.lens035.app.ui.nav

import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

/** App destinations. Each carries what it needs to start. */
sealed interface Screen {
    data object Home : Screen

    data class PhotoEditor(
        val uri: Uri,
        val projectId: String? = null,
        /** Optional film to pre-select (from the film shelf on the home screen). */
        val filmId: String? = null,
    ) : Screen

    data class VideoEditor(val uri: Uri, val projectId: String? = null) : Screen

    data object PoseCamera : Screen
}

/** A tiny back stack. Screens are pushed and popped; Home is always at the bottom. */
class Navigator {
    val stack: SnapshotStateList<Screen> = mutableStateListOf(Screen.Home)

    val current: Screen get() = stack.last()

    val canGoBack: Boolean get() = stack.size > 1

    fun push(screen: Screen) {
        stack.add(screen)
    }

    /** Replaces the current screen (e.g. camera capture -> editor). */
    fun replace(screen: Screen) {
        if (stack.size <= 1) stack.add(screen) else stack[stack.lastIndex] = screen
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun home() {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
}
