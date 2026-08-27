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
}
