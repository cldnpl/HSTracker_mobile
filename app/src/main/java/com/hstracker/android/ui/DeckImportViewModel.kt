package com.hstracker.android.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hstracker.android.data.Archetype
import com.hstracker.android.data.ArchetypeRepository
import com.hstracker.android.data.Card
import com.hstracker.android.data.CardRepository
import com.hstracker.android.data.HeroClass
import com.hstracker.android.deck.Deck
import com.hstracker.android.deck.DeckCode
import com.hstracker.android.game.GameSession
import com.hstracker.android.game.GameState
import com.hstracker.android.recognition.DHash
import com.hstracker.android.recognition.HashIndexer
import com.hstracker.android.recognition.RecognitionState
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ResolvedCard(val card: Card?, val dbfId: Int, val count: Int)

/** Stato di un singolo lato (player o opponent). */
data class DeckSideUiState(
    val code: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val deck: Deck? = null,
    val resolved: List<ResolvedCard> = emptyList(),
)

data class DeckUiState(
    val player: DeckSideUiState = DeckSideUiState(),
    val opponent: DeckSideUiState = DeckSideUiState(),
)

class DeckImportViewModel(app: Application) : AndroidViewModel(app) {

    private val cards = CardRepository()
    private val archetypes = ArchetypeRepository(app)
    private val indexer = HashIndexer()
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _archetypesForClass = MutableStateFlow<List<Archetype>>(emptyList())
    val archetypesForClass: StateFlow<List<Archetype>> = _archetypesForClass.asStateFlow()

    private val _opponentQuery = MutableStateFlow("")
    val opponentQuery: StateFlow<String> = _opponentQuery.asStateFlow()

    private val _opponentSearchResults = MutableStateFlow<List<Card>>(emptyList())
    val opponentSearchResults: StateFlow<List<Card>> = _opponentSearchResults.asStateFlow()

    /** Carte aggiunte a mano come "l'avversario le ha nel deck" (fallback manuale). */
    private val _manualOpponent = MutableStateFlow<List<Card>>(emptyList())
    val manualOpponent: StateFlow<List<Card>> = _manualOpponent.asStateFlow()

    private val _state = MutableStateFlow(
        DeckUiState(
            player = DeckSideUiState(code = prefs.getString(KEY_PLAYER_CODE, "") ?: ""),
            opponent = DeckSideUiState(code = prefs.getString(KEY_OPPONENT_CODE, "") ?: ""),
        )
    )
    val state: StateFlow<DeckUiState> = _state.asStateFlow()

    init {
        _state.value.player.code.takeIf { it.isNotBlank() }?.let { importPlayer(it) }
        _state.value.opponent.code.takeIf { it.isNotBlank() }?.let { importOpponent(it) }
    }

    fun updatePlayerCode(code: String) {
        _state.value = _state.value.copy(player = _state.value.player.copy(code = code))
    }

    fun updateOpponentCode(code: String) {
        _state.value = _state.value.copy(opponent = _state.value.opponent.copy(code = code))
    }

    fun importPlayer(code: String) = importSide(code, KEY_PLAYER_CODE, isPlayer = true)
    fun importOpponent(code: String) = importSide(code, KEY_OPPONENT_CODE, isPlayer = false)

    private fun importSide(code: String, prefsKey: String, isPlayer: Boolean) {
        setSide(isPlayer) { DeckSideUiState(code = code, loading = true) }
        viewModelScope.launch {
            try {
                val deck = DeckCode.decode(code)
                cards.ensureLoaded()
                val resolved = deck.cards
                    .map { ResolvedCard(cards.lookup(it.dbfId), it.dbfId, it.count) }
                    .sortedWith(
                        compareBy(
                            { it.card?.cost ?: Int.MAX_VALUE },
                            { it.card?.name ?: it.dbfId.toString() },
                        )
                    )
                prefs.edit().putString(prefsKey, code).apply()
                setSide(isPlayer) { DeckSideUiState(code = code, deck = deck, resolved = resolved) }
            } catch (t: Throwable) {
                setSide(isPlayer) {
                    DeckSideUiState(code = code, error = t.message ?: t::class.simpleName)
                }
            }
        }
    }

    private inline fun setSide(isPlayer: Boolean, block: () -> DeckSideUiState) {
        _state.value = if (isPlayer) _state.value.copy(player = block())
        else _state.value.copy(opponent = block())
    }

    fun loadArchetypesFor(hero: HeroClass) {
        viewModelScope.launch {
            _archetypesForClass.value = archetypes.forClass(hero)
        }
    }

    fun updateOpponentQuery(query: String) {
        _opponentQuery.value = query
        viewModelScope.launch {
            cards.ensureLoaded()
            _opponentSearchResults.value = cards.searchByName(query, limit = 12)
        }
    }

    fun addManualOpponent(card: Card) {
        if (_manualOpponent.value.none { it.dbfId == card.dbfId }) {
            _manualOpponent.value = _manualOpponent.value + card
        }
    }

    fun removeManualOpponent(dbfId: Int) {
        _manualOpponent.value = _manualOpponent.value.filterNot { it.dbfId == dbfId }
    }

    fun clearManualOpponent() { _manualOpponent.value = emptyList() }

    /**
     * Scarica gli artwork del mazzo del giocatore e ne calcola il dHash,
     * popolando [RecognitionState.recognizer]. L'avversario resta escluso
     * perché la ROI attuale inquadra la carta appena pescata da noi.
     */
    fun prepareRecognition() {
        val playerCards = _state.value.player.resolved.mapNotNull { it.card }
        if (playerCards.isEmpty()) {
            RecognitionState.finishIndexing("Nessuna carta: importa prima il tuo mazzo.")
            return
        }
        viewModelScope.launch {
            RecognitionState.recognizer.clear()
            indexer.index(playerCards)
        }
    }

    fun resolveCardName(dbfId: Int): String? = cards.lookup(dbfId)?.name

    /**
     * Modalità test: decodifica un'immagine dall'URI scelto dall'utente e la
     * passa al recognizer. Non entra in gioco MediaProjection: serve a validare
     * il pipeline in assenza di Hearthstone/emulatore.
     */
    fun recognizeFromUri(uri: Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val bmp = getApplication<android.app.Application>().contentResolver
                .openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            if (bmp == null) {
                RecognitionState.finishIndexing("Immagine non leggibile.")
                return@launch
            }
            val hash = DHash.compute(bmp)
            val match = RecognitionState.recognizer.recognize(hash)
            bmp.recycle()
            if (match == null) {
                RecognitionState.finishIndexing(
                    "Nessun match. Indice vuoto o distanza troppo alta.",
                )
            } else {
                // Nome viene risolto dalla UI.
                RecognitionState.onRecognized(match.dbfId, name = "", distance = match.distance)
                RecognitionState.finishIndexing(
                    "Match: dbfId ${match.dbfId} · d=${match.distance} · margine ${match.marginOverSecond}",
                )
            }
        }
    }

    /** Applica un archetipo scelto dal picker: imposta il codice avversario e lo importa. */
    fun applyOpponentArchetype(archetype: Archetype) {
        importOpponent(archetype.deckCode)
    }

    fun clearOpponent() {
        prefs.edit().remove(KEY_OPPONENT_CODE).apply()
        _state.value = _state.value.copy(opponent = DeckSideUiState())
        GameSession.clearOpponent()
    }

    /**
     * Popola GameSession con i deck attuali. Il player è obbligatorio.
     * Per l'avversario, in ordine di preferenza:
     *  1. deck code importato (probabile archetipo)
     *  2. lista manuale di carte viste (fallback quando non sai il mazzo)
     */
    fun startGameSession(): Boolean {
        val playerDeck = _state.value.player.deck ?: return false
        GameSession.startPlayer(GameState.fromDeck(playerDeck, cards))
        val opponentDeck = _state.value.opponent.deck
        val manual = _manualOpponent.value
        when {
            opponentDeck != null -> GameSession.startOpponent(GameState.fromDeck(opponentDeck, cards))
            manual.isNotEmpty() -> GameSession.startOpponent(GameState.fromManualPicks(manual))
            else -> GameSession.clearOpponent()
        }
        return true
    }

    fun stopGameSession() { GameSession.clearAll() }

    private companion object {
        const val PREFS = "hstracker_prefs"
        const val KEY_PLAYER_CODE = "last_deck_code"           // stesso nome del legacy, per compatibilità
        const val KEY_OPPONENT_CODE = "last_opponent_code"
    }
}
