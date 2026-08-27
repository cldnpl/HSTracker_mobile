package com.hstracker.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hstracker.android.data.Card
import com.hstracker.android.data.CardRepository
import com.hstracker.android.deck.Deck
import com.hstracker.android.deck.DeckCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ResolvedCard(val card: Card?, val dbfId: Int, val count: Int)

data class DeckUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val deck: Deck? = null,
    val resolved: List<ResolvedCard> = emptyList(),
)

class DeckImportViewModel(
    private val cards: CardRepository = CardRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(DeckUiState())
    val state: StateFlow<DeckUiState> = _state.asStateFlow()

    fun import(code: String) {
        _state.value = DeckUiState(loading = true)
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
                _state.value = DeckUiState(deck = deck, resolved = resolved)
            } catch (t: Throwable) {
                _state.value = DeckUiState(error = t.message ?: t::class.simpleName)
            }
        }
    }
}
