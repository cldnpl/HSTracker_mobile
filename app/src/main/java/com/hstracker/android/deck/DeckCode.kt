package com.hstracker.android.deck

import java.util.Base64

/**
 * Parser per il formato deck code di Blizzard (Hearthstone).
 *
 * Layout binario dopo la decodifica base64:
 *   byte 0                 : riservato (0x00)
 *   varint version         : es. 1
 *   varint format          : 1=wild, 2=standard, 3=classic, 4=twist
 *   varint numHeroes       : normalmente 1
 *     varint heroDbfId     : ripetuto numHeroes volte
 *   varint numCards1       : n. carte con count=1
 *     varint dbfId         : ripetuto
 *   varint numCards2       : n. carte con count=2
 *     varint dbfId         : ripetuto
 *   varint numCardsN       : n. carte con count>2
 *     varint dbfId
 *     varint count         : coppia ripetuta
 *
 * Riferimento: https://hearthsim.info/docs/deckstrings/
 */
object DeckCode {

    class InvalidDeckCodeException(message: String, cause: Throwable? = null) :
        IllegalArgumentException(message, cause)

    fun decode(code: String): Deck {
        val cleaned = extractBase64(code)
        if (cleaned.isEmpty()) throw InvalidDeckCodeException("Nessuna stringa base64 trovata")
        val bytes = try {
            Base64.getDecoder().decode(cleaned)
        } catch (e: IllegalArgumentException) {
            throw InvalidDeckCodeException("Base64 non valido", e)
        }
        return try {
            parseBytes(bytes)
        } catch (e: IndexOutOfBoundsException) {
            throw InvalidDeckCodeException("Deck code troncato", e)
        }
    }

    /**
     * Ripulisce l'input estraendo la sola stringa base64 del deck code.
     * Tollera i blocchi copia-incollati da HSReplay / client Hearthstone che
     * contengono righe di commento (che iniziano con "#"), righe vuote, URL,
     * e spazi/newline in mezzo al base64.
     */
    private fun extractBase64(raw: String): String {
        val meaningful = raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("http") }
            .joinToString("")
        return buildString(meaningful.length) {
            for (c in meaningful) {
                if (c.isBase64Char()) append(c)
            }
        }
    }

    private fun Char.isBase64Char(): Boolean =
        this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' ||
            this == '+' || this == '/' || this == '='

    private fun parseBytes(bytes: ByteArray): Deck {
        val reader = VarintReader(bytes)
        val reserved = reader.readByte()
        if (reserved != 0) throw InvalidDeckCodeException("Byte riservato atteso 0, ricevuto $reserved")

        reader.readVarint()                      // version
        val format = DeckFormat.fromId(reader.readVarint())

        val heroCount = reader.readVarint()
        val heroes = List(heroCount) { reader.readVarint() }

        val singles = List(reader.readVarint()) { DeckCard(reader.readVarint(), 1) }
        val doubles = List(reader.readVarint()) { DeckCard(reader.readVarint(), 2) }
        val nCopies = List(reader.readVarint()) {
            val dbfId = reader.readVarint()
            val count = reader.readVarint()
            DeckCard(dbfId, count)
        }

        val cards = (singles + doubles + nCopies).sortedBy { it.dbfId }
        return Deck(format, heroes, cards)
    }

    private class VarintReader(private val bytes: ByteArray) {
        private var pos = 0

        fun readByte(): Int = bytes[pos++].toInt() and 0xFF

        fun readVarint(): Int {
            var result = 0
            var shift = 0
            while (true) {
                val b = bytes[pos++].toInt() and 0xFF
                result = result or ((b and 0x7F) shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
                if (shift > 35) throw InvalidDeckCodeException("Varint troppo lungo")
            }
        }
    }
}
