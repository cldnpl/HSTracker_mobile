package com.hstracker.android.overlay

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Preferenze legate all'overlay flottante (per ora: la scala UI decisa dall'utente
 * col pinch). Persistite in SharedPreferences così tra un uso e l'altro dell'app
 * l'overlay ricompare della stessa dimensione.
 */
object OverlayPrefs {

    private const val PREFS = "hstracker_overlay"
    private const val KEY_SCALE = "scale"

    const val MIN_SCALE = 0.6f
    const val MAX_SCALE = 2.0f

    private var prefs: SharedPreferences? = null

    private val _scale = MutableStateFlow(1f)
    val scale: StateFlow<Float> = _scale.asStateFlow()

    fun init(context: Context) {
        val p = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _scale.value = p.getFloat(KEY_SCALE, 1f).coerceIn(MIN_SCALE, MAX_SCALE)
    }

    fun applyScaleFactor(factor: Float) {
        val next = (_scale.value * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        if (next == _scale.value) return
        _scale.value = next
        prefs?.edit()?.putFloat(KEY_SCALE, next)?.apply()
    }
}
