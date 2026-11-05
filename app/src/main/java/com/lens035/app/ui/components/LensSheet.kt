package com.lens035.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lens035.app.ui.theme.LensColors
import com.lens035.app.ui.theme.LensShapes

/**
 * A simple modal bottom sheet: scrim + sheet sliding up from the bottom. Dismissed by tapping
 * the scrim or the back button (unless [dismissible] is false, e.g. while saving).
 */
@Composable
fun LensSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    dismissible: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (visible) {
        BackHandler { if (dismissible) onDismiss() }
    }
    AnimatedVisibility(visible, enter = fadeIn(tween(180)), exit = fadeOut(tween(160))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(LensColors.Scrim)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    if (dismissible) onDismiss()
                },
        )
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible,
            enter = slideInVertically(tween(240)) { it },
            exit = slideOutVertically(tween(200)) { it },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(LensColors.Surface, LensShapes.Sheet)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
            ) {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(width = 36.dp, height = 4.dp).background(LensColors.Outline, LensShapes.Pill))
                }
                Spacer(Modifier.height(8.dp))
                content()
            }
        }
    }
}
