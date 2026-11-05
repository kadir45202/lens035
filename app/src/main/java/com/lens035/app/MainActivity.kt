package com.lens035.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.lens035.app.ui.camera.PoseCameraScreen
import com.lens035.app.ui.editor.PhotoEditorScreen
import com.lens035.app.ui.home.HomeScreen
import com.lens035.app.ui.nav.Navigator
import com.lens035.app.ui.nav.Screen
import com.lens035.app.ui.theme.Lens035Theme
import com.lens035.app.ui.theme.LensColors
import com.lens035.app.ui.video.VideoEditorScreen

class MainActivity : ComponentActivity() {
    private val navigator = Navigator()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The UI is always dark: keep light system-bar icons regardless of the system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            Lens035Theme {
                AppRoot(navigator)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Opens images/videos shared or sent for editing from other apps. */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            Intent.ACTION_EDIT -> intent.data
            else -> null
        }
        if (uri != null) {
            val type = intent.type ?: contentResolver.getType(uri) ?: ""
            navigator.home()
            navigator.push(if (type.startsWith("video")) Screen.VideoEditor(uri) else Screen.PhotoEditor(uri))
        }
    }
}

@Composable
private fun AppRoot(navigator: Navigator) {
    BackHandler(enabled = navigator.canGoBack) { navigator.pop() }
    Box(Modifier.fillMaxSize().background(LensColors.Ground)) {
        AnimatedContent(
            targetState = navigator.current,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "screen",
        ) { screen ->
            val key = remember(screen) { screen }
            when (key) {
                Screen.Home -> HomeScreen(navigator)
                is Screen.PhotoEditor -> PhotoEditorScreen(key, navigator)
                is Screen.VideoEditor -> VideoEditorScreen(key, navigator)
                Screen.PoseCamera -> PoseCameraScreen(navigator)
            }
        }
    }
}
