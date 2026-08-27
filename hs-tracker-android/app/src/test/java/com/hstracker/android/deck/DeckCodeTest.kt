package com.hstracker.android.deck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64

class DeckCodeTest {

    @Test
    fun `decodifica un deck code hand-crafted`() {
        val bytes = encodeDeck(
            format = 2,
            heroes = listOf(7),
            singles = listOf(100, 200),
            doubles = listOf(300),
            nCopies = emptyList(),
        )
        val code = Base64.getEncoder().encodeToString(bytes)

        val deck = DeckCode.decode(code)

        assertEquals(DeckFormat.STANDARD, deck.format)
        assertEquals(listOf(7), deck.heroDbfIds)
        assertEquals(
            listOf(
                DeckCard(100, 1),
                DeckCard(200, 1),
                DeckCard(300, 2),
            ),
            deck.cards,
        )
        assertEquals(4, deck.totalCards)
    }

    @Test
    fun `decodifica n-copies`() {
        val bytes = encodeDeck(
            format = 1,
            heroes = listOf(31),
            singles = emptyList(),
            doubles = emptyList(),
            nCopies = listOf(500 to 3, 600 to 5),
        )
        val code = Base64.getEncoder().encodeToString(bytes)

        val deck = DeckCode.decode(code)

        assertEquals(DeckFormat.WILD, deck.format)
        assertEquals(
            listOf(DeckCard(500, 3), DeckCard(600, 5)),
            deck.cards,
        )
    }

    @Test
    fun `base64 non valido lancia InvalidDeckCodeException`() {
        assertThrows(DeckCode.InvalidDeckCodeException::class.java) {
            DeckCode.decode("!!!not-base64!!!")
        }
    }

    private fun encodeDeck(
        format: Int,
        heroes: List<Int>,
        singles: List<Int>,
        doubles: List<Int>,
        nCopies: List<Pair<Int, Int>>,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0)                                // reserved
        writeVarint(out, 1)                         // version
        writeVarint(out, format)
        writeVarint(out, heroes.size); heroes.forEach { writeVarint(out, it) }
        writeVarint(out, singles.size); singles.forEach { writeVarint(out, it) }
        writeVarint(out, doubles.size); doubles.forEach { writeVarint(out, it) }
        writeVarint(out, nCopies.size); nCopies.forEach { (id, c) ->
            writeVarint(out, id); writeVarint(out, c)
        }
        return out.toByteArray()
    }

    private fun writeVarint(out: ByteArrayOutputStream, value: Int) {
        var v = value
        while (v and 0x7F.inv() != 0) {
            out.write((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        out.write(v and 0x7F)
    }
}
