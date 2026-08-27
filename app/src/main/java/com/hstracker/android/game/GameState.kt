package com.hstracker.android.game

import com.hstracker.android.data.Card
import com.hstracker.android.data.CardRepository
import com.hstracker.android.deck.Deck

data class TrackedCard(
    val dbfId: Int,
    val name: String,
    val cost: Int?,
    val initial: Int,
    val remaining: Int,
) {
    val played: Int get() = initial - remaining
}

data class GameState(
    val cards: List<TrackedCard>,
) {
    val remainingTotal: Int get() = cards.sumOf { it.remaining }
    val playedTotal: Int get() = cards.sumOf { it.played }

    fun decrement(dbfId: Int): GameState = update(dbfId) { it.remaining - 1 }
    fun increment(dbfId: Int): GameState = update(dbfId) { it.remaining + 1 }
    fun reset(dbfId: Int): GameState = update(dbfId) { it.initial }

    private fun update(dbfId: Int, newRemaining: (TrackedCard) -> Int): GameState {
        val updated = cards.map { c ->
            if (c.dbfId != dbfId) c
            else c.copy(remaining = newRemaining(c).coerceIn(0, c.initial))
        }
        return copy(cards = updated)
    }

    companion object {
        fun fromDeck(deck: Deck, repo: CardRepository): GameState {
            val tracked = deck.cards.map { dc ->
                val card = repo.lookup(dc.dbfId)
                TrackedCard(
                    dbfId = dc.dbfId,
                    name = card?.name ?: "dbfId ${dc.dbfId}",
                    cost = card?.cost,
                    initial = dc.count,
                    remaining = dc.count,
                )
            }.sortedWith(
                compareBy({ it.cost ?: Int.MAX_VALUE }, { it.name })
            )
            return GameState(tracked)
        }
    }
}
