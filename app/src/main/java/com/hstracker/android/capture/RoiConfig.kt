package com.hstracker.android.capture

import android.graphics.Rect

/**
 * Region of interest da cui estrarre la "carta appena pescata".
 * Espressa in percentuali del frame catturato, così è indipendente
 * dalla risoluzione e dal downscale.
 *
 * Prima stima (Hearthstone mobile in landscape): la carta ingrandita
 * quando peschi appare centrata in orizzontale, occupando circa il
 * 40% della larghezza, e verticalmente sta tra il 25% (top) e il
 * 75% (bottom) del frame.
 *
 * Da rifinire quando confronto con screenshot reali del gioco.
 */
data class RoiConfig(
    val leftPct: Float = 0.30f,
    val topPct: Float = 0.20f,
    val rightPct: Float = 0.70f,
    val bottomPct: Float = 0.80f,
) {
    fun toRect(frameWidth: Int, frameHeight: Int): Rect {
        val left = (leftPct * frameWidth).toInt().coerceAtLeast(0)
        val top = (topPct * frameHeight).toInt().coerceAtLeast(0)
        val right = (rightPct * frameWidth).toInt().coerceAtMost(frameWidth)
        val bottom = (bottomPct * frameHeight).toInt().coerceAtMost(frameHeight)
        return Rect(left, top, right, bottom)
    }

    companion object {
        val DEFAULT = RoiConfig()
    }
}
