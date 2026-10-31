package com.gabrieleforghieri.peak.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Feedback tattile, tenuto in un punto solo per non spargere il vibratore nelle viste (`Utils/Haptics.swift`).
 * Dove il telefono ha il feedback di sistema (tocco, conferma) si usa quello; il doppio colpo dei record è una forma d'onda.
 */
class Haptics(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private fun play(effect: Int, fallbackMs: Long) {
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return
        v.vibrate(if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(effect) else VibrationEffect.createOneShot(fallbackMs, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    /** Un tocco leggero (stepper, scelta). */
    fun tap(view: View? = null) {
        if (view != null) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) else play(VibrationEffect.EFFECT_TICK, 10)
    }

    /** Una conferma (salva una serie). */
    fun commit() = play(VibrationEffect.EFFECT_CLICK, 20)

    fun success() = play(VibrationEffect.EFFECT_DOUBLE_CLICK, 40)

    fun warning() = play(VibrationEffect.EFFECT_HEAVY_CLICK, 50)

    /** Riservato ai nuovi record: due colpi ravvicinati, il secondo più leggero. */
    fun personalRecord() {
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 80, 30), intArrayOf(0, 255, 0, 180), -1))
    }
}
