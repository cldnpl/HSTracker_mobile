package com.hstracker.android.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DHashTest {

    private fun gradientHorizontal(): IntArray {
        // 9x8: ogni riga cresce da 0 a 255 → tutti i confronti sinistra vs destra
        // sono "sinistro < destro" → hash = 0
        val g = IntArray(9 * 8)
        for (y in 0 until 8) {
            for (x in 0 until 9) g[y * 9 + x] = (x * 255) / 8
        }
        return g
    }

    private fun gradientHorizontalReversed(): IntArray {
        val g = IntArray(9 * 8)
        for (y in 0 until 8) {
            for (x in 0 until 9) g[y * 9 + x] = 255 - (x * 255) / 8
        }
        return g
    }

    @Test
    fun `hash identico per la stessa immagine`() {
        val img = gradientHorizontal()
        assertEquals(DHash.computeFromGrayscale(img), DHash.computeFromGrayscale(img))
    }

    @Test
    fun `distanza zero fra hash uguali`() {
        val h = DHash.computeFromGrayscale(gradientHorizontal())
        assertEquals(0, DHash.distance(h, h))
    }

    @Test
    fun `gradienti opposti danno hash con distanza massima`() {
        val a = DHash.computeFromGrayscale(gradientHorizontal())
        val b = DHash.computeFromGrayscale(gradientHorizontalReversed())
        // Tutti i 64 bit dovrebbero essere invertiti
        assertEquals(64, DHash.distance(a, b))
    }

    @Test
    fun `piccola perturbazione produce distanza bassa`() {
        val base = gradientHorizontal()
        // Aggiungo rumore ±3 solo in alcuni pixel: la maggior parte dei
        // confronti sinistra/destra resta consistente.
        val perturbed = base.copyOf()
        perturbed[10] = (perturbed[10] + 3).coerceAtMost(255)
        perturbed[30] = (perturbed[30] - 2).coerceAtLeast(0)
        val d = DHash.distance(
            DHash.computeFromGrayscale(base),
            DHash.computeFromGrayscale(perturbed),
        )
        assertTrue("distanza attesa piccola, era $d", d <= 4)
    }
}
