package com.hstracker.android.recognition

import android.graphics.Bitmap
import java.util.concurrent.ConcurrentHashMap

/**
 * Manteniamo un dizionario dbfId → hash 64-bit delle carte di interesse
 * (solo quelle presenti nei deck importati per questa sessione, così
 * teniamo il matching veloce e riduciamo i falsi positivi).
 */
class CardRecognizer(
    /** Soglia di distanza sotto la quale consideriamo il match valido. */
    var maxDistance: Int = 12,
) {
    private val hashes = ConcurrentHashMap<Int, Long>()

    val size: Int get() = hashes.size

    fun put(dbfId: Int, hash: Long) { hashes[dbfId] = hash }
    fun remove(dbfId: Int) { hashes.remove(dbfId) }
    fun clear() { hashes.clear() }

    /**
     * Cerca il miglior match. Ritorna null se il migliore ha distanza > maxDistance
     * o se il dizionario è vuoto.
     */
    fun recognize(bitmap: Bitmap): Recognition? {
        if (hashes.isEmpty()) return null
        val target = DHash.compute(bitmap)
        return recognize(target)
    }

    fun recognize(targetHash: Long): Recognition? {
        var bestDbfId = -1
        var bestDistance = Int.MAX_VALUE
        var secondBest = Int.MAX_VALUE
        for ((dbfId, hash) in hashes) {
            val d = DHash.distance(targetHash, hash)
            when {
                d < bestDistance -> {
                    secondBest = bestDistance
                    bestDistance = d
                    bestDbfId = dbfId
                }
                d < secondBest -> secondBest = d
            }
        }
        if (bestDbfId == -1 || bestDistance > maxDistance) return null
        return Recognition(
            dbfId = bestDbfId,
            distance = bestDistance,
            marginOverSecond = (secondBest - bestDistance).coerceAtLeast(0),
        )
    }
}

data class Recognition(
    val dbfId: Int,
    val distance: Int,
    /** Distanza del secondo miglior candidato − distanza del migliore. Alto = match più sicuro. */
    val marginOverSecond: Int,
)
