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
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _archetypesForClass = MutableStateFlow<List<Archetype>>(emptyList())
    val archetypesForClass: StateFlow<List<Archetype>> = _archetypesForClass.asStateFlow()

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

    /** Applica un archetipo scelto dal picker: imposta il codice avversario e lo importa. */
    fun applyOpponentArchetype(archetype: Archetype) {
        importOpponent(archetype.deckCode)
    }

    fun clearOpponent() {
        prefs.edit().remove(KEY_OPPONENT_CODE).apply()
        _state.value = _state.value.copy(opponent = DeckSideUiState())
        GameSession.clearOpponent()
    }

    /** Popola GameSession con i deck attuali. Il player è obbligatorio. */
    fun startGameSession(): Boolean {
        val playerDeck = _state.value.player.deck ?: return false
        GameSession.startPlayer(GameState.fromDeck(playerDeck, cards))
        _state.value.opponent.deck?.let { opp ->
            GameSession.startOpponent(GameState.fromDeck(opp, cards))
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
