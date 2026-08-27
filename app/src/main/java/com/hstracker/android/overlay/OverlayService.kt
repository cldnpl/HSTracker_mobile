package com.hstracker.android.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.hstracker.android.MainActivity

/**
 * Foreground service che aggiunge una ComposeView flottante via WindowManager,
 * sopra Hearthstone (o qualsiasi altra app). Osserva GameSession per aggiornare
 * il conteggio delle carte quando l'utente le pesca.
 */
class OverlayService : Service(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = store

    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        OverlayPrefs.init(this)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        startForegroundNotification()
        if (overlayView == null) showOverlay()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        return START_STICKY
    }

    private fun showOverlay() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setContent { OverlayRoot() }
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 120
        }

        // Drag con un dito, pinch-to-resize con due.
        view.setOnTouchListener(OverlayTouchController(wm, view, params))

        wm.addView(view, params)
        windowManager = wm
        overlayView = view
    }

    private fun startForegroundNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Overlay partita",
                NotificationManager.IMPORTANCE_LOW,
            )
            nm.createNotificationChannel(ch)
        }
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification =
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("HSTracker attivo")
                .setContentText("Tocca per aprire, o usa Stop per chiudere l'overlay.")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentIntent(openApp)
                .addAction(0, "Stop", stop)
                .setOngoing(true)
                .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
        overlayView?.let { windowManager?.removeView(it) }
        overlayView = null
        windowManager = null
    }

    override fun onDestroy() {
        overlayView?.let { runCatching { windowManager?.removeView(it) } }
        overlayView = null
        windowManager = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.hstracker.android.overlay.STOP"
        private const val CHANNEL_ID = "hstracker_overlay"
        private const val NOTIF_ID = 1

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, OverlayService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}

/**
 * Gestione touch dell'overlay:
 * - un dito che si sposta > 20px → drag della finestra
 * - due dita → pinch che aggiorna [OverlayPrefs.scale]
 * - tap breve con un dito → passa al Compose sottostante (tocca la carta)
 */
private class OverlayTouchController(
    private val wm: WindowManager,
    private val view: android.view.View,
    private val params: WindowManager.LayoutParams,
) : android.view.View.OnTouchListener {

    private val scaleDetector = android.view.ScaleGestureDetector(view.context, ScaleListener()).apply {
        isQuickScaleEnabled = false
    }

    private var startX = 0
    private var startY = 0
    private var touchX = 0f
    private var touchY = 0f
    private var isDragging = false
    private var isPinching = false

    override fun onTouch(v: android.view.View, event: android.view.MotionEvent): Boolean {
        // Il ScaleGestureDetector va sempre alimentato per riconoscere il pinch.
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    isPinching = true
                    isDragging = false
                    return true
                }
            }
            android.view.MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount <= 2) isPinching = false
                return true
            }
            android.view.MotionEvent.ACTION_DOWN -> {
                startX = params.x; startY = params.y
                touchX = event.rawX; touchY = event.rawY
                isDragging = false
                // Lascio passare il tocco al Compose sottostante: solo quando
                // sposto oltre soglia divento drag.
                return false
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                if (isPinching) return true
                val dx = (event.rawX - touchX).toInt()
                val dy = (event.rawY - touchY).toInt()
                if (isDragging || kotlin.math.abs(dx) > 20 || kotlin.math.abs(dy) > 20) {
                    isDragging = true
                    params.x = startX + dx
                    params.y = startY + dy
                    wm.updateViewLayout(v, params)
                    return true
                }
                return false
            }
            android.view.MotionEvent.ACTION_UP,
            android.view.MotionEvent.ACTION_CANCEL -> {
                isPinching = false
                isDragging = false
                return false
            }
        }
        return false
    }

    private inner class ScaleListener : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
            OverlayPrefs.applyScaleFactor(detector.scaleFactor)
            return true
        }
    }
}
