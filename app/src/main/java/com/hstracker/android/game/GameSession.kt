package com.hstracker.android.game

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton in-process che tiene lo stato della partita corrente.
 * Due deck separati: quello del giocatore e quello (probabile) dell'avversario.
 *
 * Nota MVP: se il processo viene killato lo stato si perde. Ok per Fase 1 —
 * l'utente riavvia dagli ultimi deck code salvati.
 */
object GameSession {
    private val _player = MutableStateFlow<GameState?>(null)
    val player: StateFlow<GameState?> = _player.asStateFlow()

    private val _opponent = MutableStateFlow<GameState?>(null)
    val opponent: StateFlow<GameState?> = _opponent.asStateFlow()

    fun startPlayer(initial: GameState) { _player.value = initial }
    fun startOpponent(initial: GameState) { _opponent.value = initial }

    fun clearAll() {
        _player.value = null
        _opponent.value = null
    }
    fun clearOpponent() { _opponent.value = null }

    fun onPlayerCardDrawn(dbfId: Int) { _player.value = _player.value?.decrement(dbfId) }
    fun onPlayerUndoDraw(dbfId: Int) { _player.value = _player.value?.increment(dbfId) }

    fun onOpponentCardSeen(dbfId: Int) { _opponent.value = _opponent.value?.decrement(dbfId) }
    fun onOpponentUndoSeen(dbfId: Int) { _opponent.value = _opponent.value?.increment(dbfId) }

    /**
     * Chiamata dalla pipeline di riconoscimento visivo: decrementa il tracker
     * del giocatore se la carta esiste nel suo mazzo con copie residue > 0.
     * Ritorna true se ha applicato la modifica.
     */
    fun tryPlayerRecognized(dbfId: Int): Boolean {
        val p = _player.value ?: return false
        val hasRemaining = p.cards.any { it.dbfId == dbfId && it.remaining > 0 }
        if (!hasRemaining) return false
        _player.value = p.decrement(dbfId)
        return true
    }
}
