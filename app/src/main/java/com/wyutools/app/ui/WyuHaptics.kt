package com.wyutools.app.ui

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalView

/**
 * Tap feedback for the whole app.
 *
 * Every control funnels through here so a tap always answers with the same short
 * system tick: buttons, icon buttons, dialog rows, cards, switch rows and bare
 * [androidx.compose.foundation.clickable] areas. Going through the platform
 * (instead of a fixed vibration length) keeps the phone's own haptic strength,
 * respects the system "touch feedback" setting, and needs no VIBRATE permission.
 */
internal object WyuHaptics {
    /** Short, neutral tick: the default answer to a normal tap. */
    fun tap(view: View?) {
        view?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
}

/**
 * Returns [action] with a tap tick played just before it. Nothing is played when
 * [enabled] is false, so a disabled button stays silent.
 *
 * Miuix's own Switch / Checkbox / RadioButton / Slider already tick themselves;
 * wrapping those would double up, so they are deliberately left alone.
 */
@Composable
internal fun hapticClick(
    enabled: Boolean = true,
    action: () -> Unit,
): () -> Unit {
    val view = LocalView.current
    val current = rememberUpdatedState(action)
    return remember(view, enabled) {
        {
            if (enabled) WyuHaptics.tap(view)
            current.value()
        }
    }
}
