package com.hstracker.android.recognition

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.hstracker.android.data.Card
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Per ogni carta del deck di sessione: scarica il render "full card" da
 * HearthstoneJSON, calcola il dHash e lo inserisce nel [RecognitionState.recognizer].
 *
 * Endpoint immagini: https://art.hearthstonejson.com/v1/render/latest/{locale}/{size}/{cardId}.png
 * Uso 256x per bilanciare qualità e banda: il dHash rescala a 9×8 comunque.
 */
class HashIndexer(
    private val client: OkHttpClient = defaultClient,
    private val locale: String = "enUS",
) {

    private val semaphore = Semaphore(permits = 4) // fino a 4 download in parallelo

    suspend fun index(cards: List<Card>) {
        val unique = cards.distinctBy { it.dbfId }.filter { it.id.isNotBlank() }
        RecognitionState.startIndexing(unique.size)
        val done = AtomicInteger(0)
        val failed = AtomicInteger(0)

        coroutineScope {
            val jobs = unique.map { card ->
                async {
                    semaphore.withPermit {
                        val ok = runCatching { indexOne(card) }.getOrDefault(false)
                        if (ok) done.incrementAndGet() else failed.incrementAndGet()
                        RecognitionState.updateIndexing(done.get(), failed.get())
                    }
                }
            }
            jobs.awaitAll()
        }

        val d = done.get()
        val f = failed.get()
        val msg = if (f == 0) "Indicizzate $d carte." else "Indicizzate $d, fallite $f."
        RecognitionState.finishIndexing(msg)
    }

    private suspend fun indexOne(card: Card): Boolean {
        val bitmap = fetchArtwork(card.id) ?: return false
        return try {
            RecognitionState.recognizer.put(card.dbfId, DHash.compute(bitmap))
            true
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun fetchArtwork(cardId: String): Bitmap? = withContext(Dispatchers.IO) {
        val url = "https://art.hearthstonejson.com/v1/render/latest/$locale/256x/$cardId.png"
        val request = Request.Builder().url(url).build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bytes = response.body?.bytes() ?: return@withContext null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private companion object {
        val defaultClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
