package com.example.videoecraft.export

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import com.example.videoecraft.MainActivity
import com.example.videoecraft.R
import com.example.videoecraft.engine.MediaStoreSaver
import com.example.videoecraft.engine.VideoProcessor
import com.example.videoecraft.engine.model.EditSpec
import com.example.videoecraft.engine.model.ExportBitrate
import com.example.videoecraft.engine.model.ExportFps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Servicio de primer plano que ejecuta la exportación.
 *
 * Responsabilidades:
 *  1. Mantener el proceso vivo con `foregroundServiceType="mediaProcessing"`
 *     (obligatorio en Android 14+ para tareas de media largas).
 *  2. Adquirir un `PARTIAL_WAKE_LOCK` para que la CPU no se duerma con la
 *     pantalla apagada durante un export 4K.
 *  3. Publicar el progreso de [VideoProcessor] en la notificación.
 *  4. Al terminar, publicar el MP4 en `Movies/VideoEcraft` con
 *     [MediaStoreSaver] y emitir un Intent de resultado.
 *
 * La UI observa el progreso desde [ExportProgressHolder], un singleton
 * observable que el `ExportViewModel` (Fase 3) colecta como `StateFlow`.
 */
@UnstableApi
class ExportService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var exportJob: Job? = null
    private var processor: VideoProcessor? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground(buildProgressNotification(progress = 0f, etaMs = null))

        if (intent?.action == ACTION_CANCEL) {
            cancelExport()
            return START_NOT_STICKY
        }

        val spec = intent?.extras?.let { ExportRequest.fromBundle(it) }
        if (spec == null) {
            stopSelfSafely()
            return START_NOT_STICKY
        }

        startExport(spec)
        return START_NOT_STICKY
    }

    // -----------------------------------------------------------------
    //  Exportación
    // -----------------------------------------------------------------

    private fun startExport(request: ExportRequest) {
        if (exportJob?.isActive == true) return

        acquireWakeLock()
        ExportProgressHolder.markRunning()

        val videoProcessor = VideoProcessor(applicationContext)
        processor = videoProcessor

        exportJob = serviceScope.launch {
            try {
                videoProcessor
                    .export(
                        spec = request.spec,
                        fps = request.fps,
                        bitrate = request.bitrate,
                    )
                    .collect { progress ->
                        when (progress) {
                            is com.example.videoecraft.engine.ExportProgress.Running -> {
                                ExportProgressHolder.markRunning(progress.progress)
                                updateNotification(progress.progress, progress.etaMs)
                            }

                            is com.example.videoecraft.engine.ExportProgress.Completed -> {
                                val uri = publishToGallery(progress.file)
                                ExportProgressHolder.markCompleted(uri)
                                showCompletedNotification(uri)
                                progress.file.delete()
                                finishExport()
                            }

                            is com.example.videoecraft.engine.ExportProgress.Failed -> {
                                ExportProgressHolder.markFailed(progress.message)
                                showFailedNotification(progress.message)
                                finishExport()
                            }
                        }
                    }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                ExportProgressHolder.markFailed(t.message ?: "Error desconocido")
                showFailedNotification(t.message ?: "Error desconocido")
                finishExport()
            }
        }
    }

    /** Publica el archivo en `Movies/VideoEcraft`. */
    private suspend fun publishToGallery(file: java.io.File): Uri =
        withContext(Dispatchers.IO) {
            MediaStoreSaver.save(
                context = applicationContext,
                source = file,
                displayName = MediaStoreSaver.buildDisplayName(),
            )
        }

    private fun cancelExport() {
        exportJob?.cancel()
        processor?.cancel()
        ExportProgressHolder.markCancelled()
        finishExport()
    }

    /** Libera recursos y se detiene en primer plano. */
    private fun finishExport() {
        exportJob = null
        processor?.release()
        processor = null
        releaseWakeLock()
        stopSelfSafely()
    }

    private fun stopSelfSafely() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        processor?.release()
        releaseWakeLock()
        super.onDestroy()
    }

    // -----------------------------------------------------------------
    //  Wake lock
    // -----------------------------------------------------------------

    @Suppress("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .apply {
                setReferenceCounted(false)
                acquire()
            }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    // -----------------------------------------------------------------
    //  Notificaciones
    // -----------------------------------------------------------------

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.export_notification_channel),
            // LOW: sin sonido para no molestar durante un export largo.
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.export_title)
            setShowBadge(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    private fun startInForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        } else {
            0
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(progress: Float, etaMs: Long?) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildProgressNotification(progress, etaMs))
    }

    private fun buildProgressNotification(progress: Float, etaMs: Long?): Notification {
        val percent = (progress * 100).toInt()
        val etaText = etaMs?.let { " · ~${it / 1000}s restantes" } ?: ""

        return baseNotification()
            .setContentTitle(getString(R.string.export_title))
            .setContentText("$percent%$etaText")
            .setProgress(100, percent, /* indeterminate= */ progress <= 0f)
            .setOngoing(true)
            .addAction(
                0,
                getString(android.R.string.cancel),
                cancelPendingIntent(),
            )
            .build()
    }

    private fun showCompletedNotification(uri: Uri) {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = baseNotification()
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Video guardado en Movies/${MediaStoreSaver.ALBUM_NAME}")
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setOngoing(false)
            .build()

        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification)
    }

    private fun showFailedNotification(message: String) {
        val notification = baseNotification()
            .setContentTitle(getString(R.string.export_title))
            .setContentText(message)
            .setContentIntent(openAppPendingIntent())
            .setAutoCancel(true)
            .setOngoing(false)
            .build()

        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification)
    }

    private fun baseNotification(): NotificationCompat.Builder =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setColor(getColor(R.color.craft_accent_start))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)

    private fun openAppPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun cancelPendingIntent(): PendingIntent = PendingIntent.getService(
        this,
        1,
        Intent(this, ExportService::class.java).setAction(ACTION_CANCEL),
        PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val CHANNEL_ID = "videoecraft_export"
        const val NOTIFICATION_ID = 4591
        const val ACTION_EXPORT = "com.example.videoecraft.action.EXPORT"
        const val ACTION_CANCEL = "com.example.videoecraft.action.CANCEL_EXPORT"
        private const val WAKE_LOCK_TAG = "VideoEcraft:Export"

        /** Arranca el servicio para exportar [request]. */
        fun start(context: Context, request: ExportRequest) {
            val intent = Intent(context, ExportService::class.java).apply {
                action = ACTION_EXPORT
                putExtras(request.toBundle())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Cancela un export en curso. */
        fun cancel(context: Context) {
            context.startService(
                Intent(context, ExportService::class.java).setAction(ACTION_CANCEL),
            )
        }
    }
}

/**
 * Petición de exportación serializable a través de un Intent.
 *
 * El [EditSpec] viaja como JSON (o como id de proyecto en la Fase 3, cuando
 * esté Room). Aquí se guarda el spec ya resuelto para desacoplar el motor
 * de la base de datos.
 */
@UnstableApi
data class ExportRequest(
    val spec: EditSpec,
    val fps: ExportFps = ExportFps.FPS30,
    val bitrate: ExportBitrate = ExportBitrate.MEDIUM,
) {
    fun toBundle(): android.os.Bundle = android.os.Bundle().apply {
        putString(KEY_SPEC, EditSpecCodec.encode(spec))
        putInt(KEY_FPS, fps.fps)
        putString(KEY_BITRATE, bitrate.name)
    }

    companion object {
        private const val KEY_SPEC = "spec"
        private const val KEY_FPS = "fps"
        private const val KEY_BITRATE = "bitrate"

        fun fromBundle(bundle: android.os.Bundle): ExportRequest? {
            val encoded = bundle.getString(KEY_SPEC) ?: return null
            val spec = EditSpecCodec.decode(encoded) ?: return null
            val fpsValue = bundle.getInt(KEY_FPS, 30)
            val fps = ExportFps.entries.firstOrNull { it.fps == fpsValue } ?: ExportFps.FPS30
            val bitrate = bundle.getString(KEY_BITRATE)
                ?.let { name -> ExportBitrate.entries.firstOrNull { it.name == name } }
                ?: ExportBitrate.MEDIUM
            return ExportRequest(spec, fps, bitrate)
        }
    }
}
