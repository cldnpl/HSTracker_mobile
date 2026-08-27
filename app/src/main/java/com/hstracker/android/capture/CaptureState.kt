package com.hstracker.android.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stato osservabile della pipeline di cattura schermo (Fase 2).
 *
 * Iterazione 1: espone solo `running` e `frameCount` per verifica visiva.
 * Iterazioni successive aggiungeranno gli ultimi riconoscimenti (dbfId + score).
 */
object CaptureState {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _frameCount = MutableStateFlow(0L)
    val frameCount: StateFlow<Long> = _frameCount.asStateFlow()

    private val _lastFrameWxH = MutableStateFlow<Pair<Int, Int>?>(null)
    val lastFrameWxH: StateFlow<Pair<Int, Int>?> = _lastFrameWxH.asStateFlow()

    /** Path assoluto dell'ultimo crop salvato + timestamp per invalidare la cache Compose. */
    private val _lastCrop = MutableStateFlow<CropInfo?>(null)
    val lastCrop: StateFlow<CropInfo?> = _lastCrop.asStateFlow()

    fun setRunning(value: Boolean) { _running.value = value }

    fun onFrame(width: Int, height: Int) {
        _frameCount.value = _frameCount.value + 1
        _lastFrameWxH.value = width to height
    }

    fun onCropSaved(path: String, w: Int, h: Int) {
        _lastCrop.value = CropInfo(path = path, width = w, height = h, timestamp = System.currentTimeMillis())
    }

    fun reset() {
        _frameCount.value = 0
        _lastFrameWxH.value = null
        _lastCrop.value = null
    }
}

data class CropInfo(
    val path: String,
    val width: Int,
    val height: Int,
    val timestamp: Long,
)
