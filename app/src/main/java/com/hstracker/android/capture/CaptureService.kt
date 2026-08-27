package com.hstracker.android.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import com.hstracker.android.MainActivity
import com.hstracker.android.game.GameSession
import com.hstracker.android.recognition.RecognitionState
import java.io.File
import java.io.FileOutputStream

/**
 * Fase 2 — Iterazione 1: infrastruttura di cattura schermo.
 *
 * Riceve il resultCode + Intent restituiti da MediaProjectionManager,
 * apre una VirtualDisplay verso un ImageReader e conta i frame in arrivo
 * (throttled a ~2 FPS). Nessun riconoscimento ancora: serve solo a verificare
 * che la pipeline giri prima di attaccarci l'image matching nell'iterazione 2.
 */
class CaptureService : Service() {

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private lateinit var backgroundThread: HandlerThread
    private lateinit var backgroundHandler: Handler
    private var lastFrameNs = 0L
    private var lastCropSaveNs = 0L
    private var reusableBitmap: Bitmap? = null
    private val roi = RoiConfig.DEFAULT
    private val cropFile: File by lazy { File(cacheDir, "last_crop.png") }
    private val lastDispatchByDbfId = HashMap<Int, Long>()

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            teardown()
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        backgroundThread = HandlerThread("HSCaptureThread").also { it.start() }
        backgroundHandler = Handler(backgroundThread.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                teardown()
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION") intent?.getParcelableExtra(EXTRA_DATA)
        }
        if (resultCode == Int.MIN_VALUE || data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Su Android 14+ serve avviare il foreground con type mediaProjection PRIMA
        // di aprire la MediaProjection.
        startForegroundNotification()

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mp = mpm.getMediaProjection(resultCode, data)
        if (mp == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        mp.registerCallback(projectionCallback, backgroundHandler)
        projection = mp

        setupVirtualDisplay(mp)
        CaptureState.setRunning(true)
        return START_STICKY
    }

    private fun setupVirtualDisplay(mp: MediaProjection) {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.getRealMetrics(metrics)
        // Scaliamo a 720p max lato lungo per limitare la banda: la recognition
        // successiva non ha bisogno della risoluzione nativa.
        val (w, h) = downscale(metrics.widthPixels, metrics.heightPixels, maxLongEdge = 1280)
        val density = metrics.densityDpi

        val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        reader.setOnImageAvailableListener(::onFrameAvailable, backgroundHandler)
        imageReader = reader

        virtualDisplay = mp.createVirtualDisplay(
            "HSTrackerCapture",
            w, h, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            backgroundHandler,
        )
    }

    private fun onFrameAvailable(reader: ImageReader) {
        val image = reader.acquireLatestImage() ?: return
        try {
            val now = System.nanoTime()
            if (now - lastFrameNs < MIN_FRAME_INTERVAL_NS) return
            lastFrameNs = now
            CaptureState.onFrame(image.width, image.height)

            // Ogni ~1s salvo il crop della ROI per debugging.
            if (now - lastCropSaveNs < CROP_SAVE_INTERVAL_NS) return
            val plane = image.planes.getOrNull(0) ?: return
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * image.width
            val paddedWidth = image.width + rowPadding / pixelStride

            val bmp = reusableBitmap?.takeIf {
                it.width == paddedWidth && it.height == image.height
            } ?: run {
                reusableBitmap?.recycle()
                Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
                    .also { reusableBitmap = it }
            }
            bmp.copyPixelsFromBuffer(plane.buffer)

            val rect = roi.toRect(image.width, image.height)
            if (rect.width() <= 0 || rect.height() <= 0) return
            val cropped = Bitmap.createBitmap(bmp, rect.left, rect.top, rect.width(), rect.height())
            try {
                // 1) Riconoscimento: se il recognizer è popolato prova a matchare
                //    e, se distanza + margine sono buoni, decrementa il tracker.
                val recognition = RecognitionState.recognizer.recognize(cropped)
                if (recognition != null && recognition.marginOverSecond >= MIN_MARGIN) {
                    maybeDispatchRecognition(recognition.dbfId, recognition.distance, now)
                }

                // 2) Salvataggio crop per debugging (~1 Hz).
                FileOutputStream(cropFile).use { out ->
                    cropped.compress(Bitmap.CompressFormat.PNG, 90, out)
                }
                CaptureState.onCropSaved(cropFile.absolutePath, cropped.width, cropped.height)
                lastCropSaveNs = now
            } finally {
                cropped.recycle()
            }
        } catch (t: Throwable) {
            // In caso di problemi (buffer sconosciuto, formato imprevisto)
            // meglio ignorare il frame che crashare il service.
        } finally {
            image.close()
        }
    }

    private fun startForegroundNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "Riconoscimento carte", NotificationManager.IMPORTANCE_LOW,
            )
            nm.createNotificationChannel(ch)
        }
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, CaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Riconoscimento carte attivo")
            .setContentText("HSTracker sta catturando lo schermo per riconoscere le carte pescate.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun maybeDispatchRecognition(dbfId: Int, distance: Int, nowNs: Long) {
        val lastNs = lastDispatchByDbfId[dbfId]
        if (lastNs != null && nowNs - lastNs < DISPATCH_DEBOUNCE_NS) return
        val applied = GameSession.tryPlayerRecognized(dbfId)
        if (!applied) return
        lastDispatchByDbfId[dbfId] = nowNs
        // Il nome viene risolto in UI dove CardRepository è già caricato.
        RecognitionState.onRecognized(dbfId, name = "", distance = distance)
    }

    private fun teardown() {
        CaptureState.setRunning(false)
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        reusableBitmap?.recycle()
        reusableBitmap = null
        projection?.let {
            runCatching { it.unregisterCallback(projectionCallback) }
            it.stop()
        }
        projection = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
    }

    override fun onDestroy() {
        teardown()
        if (::backgroundThread.isInitialized) backgroundThread.quitSafely()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.hstracker.android.capture.STOP"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"

        private const val CHANNEL_ID = "hstracker_capture"
        private const val NOTIF_ID = 2
        private const val MIN_FRAME_INTERVAL_NS = 500_000_000L  // ~2 FPS
        private const val CROP_SAVE_INTERVAL_NS = 1_000_000_000L // 1 crop/s
        private const val DISPATCH_DEBOUNCE_NS = 3_000_000_000L  // 3s per stesso dbfId
        private const val MIN_MARGIN = 4                          // second_best - best

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, CaptureService::class.java)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_DATA, data)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, CaptureService::class.java).setAction(ACTION_STOP)
            )
        }

        private fun downscale(w: Int, h: Int, maxLongEdge: Int): Pair<Int, Int> {
            val longEdge = maxOf(w, h)
            if (longEdge <= maxLongEdge) return w to h
            val ratio = maxLongEdge.toFloat() / longEdge
            return (w * ratio).toInt() to (h * ratio).toInt()
        }
    }
}
