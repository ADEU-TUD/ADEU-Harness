package io.github.thepytales.llmhub.transcribe

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import de.lokal.whisper.Engine
import io.github.thepytales.llmhub.LlmHubApp
import io.github.thepytales.llmhub.MainActivity
import io.github.thepytales.llmhub.R
import java.util.Locale

/**
 * Foreground service: keeps recording, transcription and model downloads running with the screen locked. The
 * notification has buttons (cancel; pause/continue and stop for recordings). When a transcription is finished while
 * the app is not on screen, a separate notification leads to the transcript.
 */
class TranscriptionService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentType = -1
    private var lastKey = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        starting = false
        channels(this)
        runCatching {
            wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "adeu:transcription")
                .apply { setReferenceCounted(false); acquire(8L * 3600 * 1000) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val t = (application as LlmHubApp).container.transcription
        val e = t.engine()
        when (intent?.action) {
            ACTION_CANCEL -> intent.getStringExtra("job")?.let(e::cancelJob)
            ACTION_PAUSE -> e.recorder()?.let { e.pauseRecording(!it.isPaused) }
            ACTION_STOP -> t.stopRecording(true, t.defaultOptions())
        }
        refresh(e)
        if (!e.busy()) stopNow()
        return START_NOT_STICKY
    }

    private fun typeFor(e: Engine): Int {
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        if (e.recorder() != null && Build.VERSION.SDK_INT >= 30) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        return type
    }

    private fun key(e: Engine): String {
        val r = e.recorder()
        return (if (r != null) "r" + r.isPaused else "") + (e.currentJob()?.id ?: "")
    }

    fun refresh(e: Engine) {
        val n = build(e)
        val type = typeFor(e)
        lastKey = key(e)
        try {
            if (type != currentType) {
                ServiceCompat.startForeground(this, ID, n, type)
                currentType = type
            } else {
                getSystemService(NotificationManager::class.java).notify(ID, n)
            }
        } catch (t: Throwable) {
            runCatching {
                ServiceCompat.startForeground(this, ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                currentType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            }
        }
    }

    private fun service(action: String, job: String?, req: Int): PendingIntent {
        val i = Intent(this, TranscriptionService::class.java).setAction(action)
        if (job != null) i.putExtra("job", job)
        return PendingIntent.getService(this, req, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun build(e: Engine): Notification {
        var title = "Transkription"
        var text = ""
        var pct = -1
        val b = NotificationCompat.Builder(this, CHANNEL)
        val r = e.recorder()
        val j = e.currentJob()
        when {
            r != null -> {
                val s = r.seconds().toInt()
                title = if (r.isPaused) "Aufnahme pausiert" else "Aufnahme läuft"
                text = String.format(Locale.GERMANY, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
                b.addAction(0, if (r.isPaused) "Weiter" else "Pause", service(ACTION_PAUSE, null, 1))
                b.addAction(0, "Stopp & transkribieren", service(ACTION_STOP, null, 2))
            }
            j != null -> {
                title = "Transkribiere: " + j.name.substringBeforeLast('.')
                pct = Math.round(j.progress * 100).toInt()
                text = j.stage + if (pct > 0) " · $pct %" else ""
                b.addAction(0, "Abbrechen", service(ACTION_CANCEL, j.id, 3))
            }
            e.downloadsSnapshot().isNotEmpty() -> {
                title = "Erkennungsmodell wird geladen"
                e.downloadsSnapshot().values.forEach { pct = Math.round(it * 100).toInt() }
                text = if (pct >= 0) "$pct %" else ""
            }
            e.hasActiveJobs() -> title = "Transkription wartet"
        }
        b.setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open(this, "transcripts", 0))
        if (pct >= 0) b.setProgress(100, pct, false)
        return b.build()
    }

    private fun stopNow() {
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
        stopSelf()
        release()
        instance = null
        currentType = -1
    }

    private fun release() {
        runCatching { wakeLock?.let { if (it.isHeld) it.release() } }
    }

    override fun onDestroy() {
        release()
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "transcription"
        private const val CHANNEL_DONE = "transcription_done"
        private const val ID = 4711
        private const val ACTION_CANCEL = "io.github.thepytales.llmhub.TRANSCRIPTION_CANCEL"
        private const val ACTION_PAUSE = "io.github.thepytales.llmhub.RECORDING_PAUSE"
        private const val ACTION_STOP = "io.github.thepytales.llmhub.RECORDING_STOP"

        @Volatile private var instance: TranscriptionService? = null
        @Volatile private var starting = false
        private var lastRefresh = 0L
        private val main = Handler(Looper.getMainLooper())

        /** The engine's state changed: start, update or stop the service. */
        fun update(ctx: Context, e: Engine) {
            main.post {
                val active = e.busy()
                val inst = instance
                if (inst == null) {
                    if (active && !starting) {
                        starting = true
                        runCatching { ctx.startForegroundService(Intent(ctx, TranscriptionService::class.java)) }
                            .onFailure { starting = false }
                    }
                    return@post
                }
                if (!active) { inst.stopNow(); return@post }
                val now = System.currentTimeMillis()
                if (now - lastRefresh < 1000 && inst.typeFor(e) == inst.currentType && inst.key(e) == inst.lastKey) return@post
                lastRefresh = now
                inst.refresh(e)
            }
        }

        fun channels(c: Context) {
            val nm = c.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Transkription & Aufnahme (läuft)", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
            nm.createNotificationChannel(NotificationChannel(CHANNEL_DONE, "Transkription fertig", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Meldet, wenn ein Transkript fertig ist – auch wenn die App im Hintergrund war."
            })
        }

        private fun open(ctx: Context, route: String, req: Int): PendingIntent {
            val i = Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_ROUTE, route)
            return PendingIntent.getActivity(ctx, req, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        /** "Fertig – tippen zum Öffnen" or the error, when the app is not on screen. */
        fun notifyFinished(ctx: Context, j: Engine.Job) {
            if (j.status == "cancelled") return
            runCatching {
                channels(ctx)
                val ok = j.status == "done"
                val id = 9000 + Math.abs(j.id.hashCode() % 100000)
                val name = j.name.substringBeforeLast('.')
                val route = if (ok && j.transcriptId != null) "transcript/${j.transcriptId}" else "transcripts"
                val n = NotificationCompat.Builder(ctx, CHANNEL_DONE)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(if (ok) "Transkription fertig – tippen zum Öffnen" else "Transkription fehlgeschlagen")
                    .setContentText(if (ok) name else "$name: ${j.error}")
                    .setStyle(NotificationCompat.BigTextStyle().bigText(if (ok) name else "$name: ${j.error}"))
                    .setContentIntent(open(ctx, route, id))
                    .setAutoCancel(true)
                    .build()
                ctx.getSystemService(NotificationManager::class.java).notify(id, n)
            }
        }
    }
}
