package com.hstracker.android.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Classi di Hearthstone. Aggiornata a fine 2024 (Death Knight introdotto con
 * March of the Lich King). NEUTRAL è usata solo per carte, non per eroi.
 */
enum class HeroClass(val display: String) {
    DEATHKNIGHT("Death Knight"),
    DEMONHUNTER("Demon Hunter"),
    DRUID("Druid"),
    HUNTER("Hunter"),
    MAGE("Mage"),
    PALADIN("Paladin"),
    PRIEST("Priest"),
    ROGUE("Rogue"),
    SHAMAN("Shaman"),
    WARLOCK("Warlock"),
    WARRIOR("Warrior");

    companion object {
        fun fromString(raw: String?): HeroClass? =
            raw?.trim()?.uppercase()?.let { normalized ->
                entries.firstOrNull { it.name == normalized }
            }
    }
}

@Serializable
data class Archetype(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("class") val heroClass: String,
    @SerialName("format") val format: String = "STANDARD",
    @SerialName("deckCode") val deckCode: String,
) {
    val hero: HeroClass? get() = HeroClass.fromString(heroClass)
}

@Serializable
private data class ArchetypesFile(
    val archetypes: List<Archetype> = emptyList(),
)

/**
 * Legge l'elenco archetipi da assets/archetypes.json.
 * Caricamento lazy: primo accesso legge da disco, gli altri ritornano la cache.
 */
class ArchetypeRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    @Volatile private var cached: List<Archetype>? = null

    suspend fun all(): List<Archetype> {
        cached?.let { return it }
        val loaded = withContext(Dispatchers.IO) {
            context.assets.open(FILE_NAME).use { stream ->
                val text = stream.bufferedReader().use { it.readText() }
                json.decodeFromString<ArchetypesFile>(text).archetypes
            }
        }
        cached = loaded
        return loaded
    }

    suspend fun forClass(hero: HeroClass): List<Archetype> =
        all().filter { it.hero == hero }

    private companion object {
        const val FILE_NAME = "archetypes.json"
    }
}
