package com.example.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.example.model.AppThemeStyle
import com.example.ui.theme.LocalAppThemeStyle

val LocalHapticsEnabled = staticCompositionLocalOf { true }

/**
 * Centralized tactile feedback engine across Agenda+.
 * Respects user toggle ("Haptische feedback") and adapts subtle timing per theme.
 */
@Stable
class AgendaHaptics(
    private val view: View,
    private val context: Context,
    private val enabled: Boolean,
    private val themeStyle: AppThemeStyle
) {
    /**
     * Subtle tick for horizontal day/week pager snaps, slider steps, and tab switches.
     */
    fun tick() {
        if (!enabled) return
        val constant = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 -> HapticFeedbackConstants.TEXT_HANDLE_MOVE
            else -> HapticFeedbackConstants.CLOCK_TICK
        }
        if (!view.performHapticFeedback(constant)) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    /**
     * Crisp confirm for checking off tasks, copying AXOLA codes, and toggling automation switches.
     */
    fun confirm() {
        if (!enabled) return
        val constant = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> HapticFeedbackConstants.CONFIRM
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> HapticFeedbackConstants.CONTEXT_CLICK
            else -> HapticFeedbackConstants.VIRTUAL_KEY
        }
        val handled = view.performHapticFeedback(constant)
        if (!handled || themeStyle == AppThemeStyle.WINDOWS_PHONE) {
            triggerOneShot(if (themeStyle == AppThemeStyle.WINDOWS_PHONE) 14L else 18L, 120)
        }
    }

    /**
     * Celebration / alert double-pulse for >= 8.0 grade unboxing or Uitval / Roosterwijziging alert.
     */
    fun celebrationOrAlert() {
        if (!enabled) return
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        triggerWaveform(longArrayOf(0L, 28L, 48L, 54L), intArrayOf(0, 175, 0, 235))
    }

    private fun resolveVibrator(): Vibrator? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val mgr = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                mgr?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }.getOrNull()?.takeIf { runCatching { it.hasVibrator() }.getOrDefault(false) }
    }

    private fun triggerOneShot(durationMs: Long, amplitude: Int) {
        val vibrator = resolveVibrator() ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255)))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        }
    }

    private fun triggerWaveform(timings: LongArray, amplitudes: IntArray) {
        val vibrator = resolveVibrator() ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = if (vibrator.hasAmplitudeControl()) {
                    VibrationEffect.createWaveform(timings, amplitudes, -1)
                } else {
                    VibrationEffect.createWaveform(timings, -1)
                }
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(timings, -1)
            }
        }
    }
}

@Composable
fun rememberAgendaHaptics(): AgendaHaptics {
    val view = LocalView.current
    val context = LocalContext.current
    val enabled = LocalHapticsEnabled.current
    val themeStyle = LocalAppThemeStyle.current
    return remember(view, context, enabled, themeStyle) {
        AgendaHaptics(view = view, context = context, enabled = enabled, themeStyle = themeStyle)
    }
}
