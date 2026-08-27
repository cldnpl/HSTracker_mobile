package com.hstracker.android.deck

enum class DeckFormat(val id: Int) {
    UNKNOWN(0), WILD(1), STANDARD(2), CLASSIC(3), TWIST(4);

    companion object {
        fun fromId(id: Int): DeckFormat = entries.firstOrNull { it.id == id } ?: UNKNOWN
    }
}

data class DeckCard(val dbfId: Int, val count: Int)

data class Deck(
    val format: DeckFormat,
    val heroDbfIds: List<Int>,
    val cards: List<DeckCard>,
) {
    val totalCards: Int get() = cards.sumOf { it.count }
}
