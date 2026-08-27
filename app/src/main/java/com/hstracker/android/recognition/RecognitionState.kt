package com.hstracker.android.recognition

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class IndexingState(
    val inProgress: Boolean = false,
    val total: Int = 0,
    val done: Int = 0,
    val failed: Int = 0,
    val message: String? = null,
)

data class LastRecognition(
    val dbfId: Int,
    val name: String,
    val distance: Int,
    val timestamp: Long,
)

/**
 * Stato globale del riconoscimento: contiene il [CardRecognizer] popolato per la
 * sessione corrente, l'avanzamento della fase di indicizzazione (fetch artwork +
 * hash) e l'ultimo riconoscimento andato a buon fine.
 */
object RecognitionState {
    val recognizer = CardRecognizer()

    private val _indexing = MutableStateFlow(IndexingState())
    val indexing: StateFlow<IndexingState> = _indexing.asStateFlow()

    private val _lastRecognition = MutableStateFlow<LastRecognition?>(null)
    val lastRecognition: StateFlow<LastRecognition?> = _lastRecognition.asStateFlow()

    fun startIndexing(total: Int) {
        _indexing.value = IndexingState(inProgress = true, total = total)
    }

    fun updateIndexing(done: Int, failed: Int) {
        _indexing.value = _indexing.value.copy(done = done, failed = failed)
    }

    fun finishIndexing(message: String? = null) {
        _indexing.value = _indexing.value.copy(inProgress = false, message = message)
    }

    fun onRecognized(dbfId: Int, name: String, distance: Int) {
        _lastRecognition.value = LastRecognition(
            dbfId = dbfId,
            name = name,
            distance = distance,
            timestamp = System.currentTimeMillis(),
        )
    }

    fun clear() {
        recognizer.clear()
        _indexing.value = IndexingState()
        _lastRecognition.value = null
    }
}
