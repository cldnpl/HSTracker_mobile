package com.hstracker.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Scarica il database carte da HearthstoneJSON (community-maintained, aggiornato ad ogni patch)
 * e lo tiene in memoria. Lookup per dbfId.
 *
 * URL: https://api.hearthstonejson.com/v1/latest/{locale}/cards.collectible.json
 */
class CardRepository(
    private val locale: String = "enUS",
    private val client: OkHttpClient = defaultClient,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val mutex = Mutex()
    @Volatile private var byDbfId: Map<Int, Card> = emptyMap()

    val isLoaded: Boolean get() = byDbfId.isNotEmpty()

    suspend fun ensureLoaded() {
        if (isLoaded) return
        mutex.withLock {
            if (isLoaded) return
            val cards = withContext(Dispatchers.IO) { fetch() }
            byDbfId = cards.associateBy { it.dbfId }
        }
    }

    fun lookup(dbfId: Int): Card? = byDbfId[dbfId]

    /**
     * Ricerca case-insensitive nel nome della carta. Ritorna al più [limit] risultati,
     * ordinati per costo poi nome. Se [query] è vuota ritorna lista vuota (non spammiamo).
     */
    fun searchByName(query: String, limit: Int = 20): List<Card> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val needle = q.lowercase()
        return byDbfId.values.asSequence()
            .filter { it.name.isNotBlank() && needle in it.name.lowercase() }
            .sortedWith(compareBy({ it.cost ?: Int.MAX_VALUE }, { it.name }))
            .take(limit)
            .toList()
    }

    private fun fetch(): List<Card> {
        val url = "https://api.hearthstonejson.com/v1/latest/$locale/cards.collectible.json"
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HearthstoneJSON HTTP ${response.code}")
            }
            val body = response.body?.string() ?: error("Empty body")
            return json.decodeFromString(body)
        }
    }

    companion object {
        private val defaultClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
