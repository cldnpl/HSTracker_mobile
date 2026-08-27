package com.hstracker.android.recognition

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Difference Hash (dHash) — perceptual hash a 64 bit.
 *
 * Algoritmo:
 * 1. Rescale a 9×8 grayscale.
 * 2. Per ogni riga confronta pixel[x] con pixel[x+1]: bit=1 se il sinistro è più chiaro.
 * 3. 8 righe × 8 confronti = 64 bit.
 *
 * Robusto contro compressione JPEG, piccoli scaling e leggeri shift di colore.
 * Distanza tra due hash = Hamming distance = numero di bit diversi (XOR + popcount).
 */
object DHash {

    private const val W = 9
    private const val H = 8

    fun compute(bitmap: Bitmap): Long {
        val scaled = Bitmap.createScaledBitmap(bitmap, W, H, true)
        val gray = IntArray(W * H)
        for (y in 0 until H) {
            for (x in 0 until W) {
                val argb = scaled.getPixel(x, y)
                val r = Color.red(argb)
                val g = Color.green(argb)
                val b = Color.blue(argb)
                gray[y * W + x] = luma(r, g, b)
            }
        }
        if (scaled !== bitmap) scaled.recycle()
        return computeFromGrayscale(gray)
    }

    /**
     * Variante testabile senza SDK Android: prende direttamente l'array
     * dei valori di luminanza già rescalato a 9×8.
     */
    fun computeFromGrayscale(gray: IntArray): Long {
        require(gray.size == W * H) { "atteso array ${W * H}, ricevuto ${gray.size}" }
        var hash = 0L
        for (y in 0 until H) {
            for (x in 0 until 8) {
                val left = gray[y * W + x]
                val right = gray[y * W + x + 1]
                if (left > right) hash = hash or (1L shl (y * 8 + x))
            }
        }
        return hash
    }

    /** Rec 601 luma. */
    fun luma(r: Int, g: Int, b: Int): Int = (r * 299 + g * 587 + b * 114) / 1000

    /** Hamming distance su 64 bit. Range 0 (identici) → 64 (opposti). */
    fun distance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)
}
