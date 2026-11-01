package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.logFailure
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.thepytales.llmhub.LlmHubApp
import io.github.thepytales.llmhub.R
import io.github.thepytales.llmhub.tasks.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the app alive while [ChatRuns] generates answers, so they are finished
 * even when the app is closed (swiped away). Stops itself as soon as nothing runs anymore.
 */
class AgentService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watcher: Job? = null
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        val runs = (application as LlmHubApp).container.runs
        if (intent?.action == ACTION_STOP) {
            runs.stopAll()
            val c = (application as LlmHubApp).container
            c.batchRunner.current.value?.let { c.batchRunner.pause(it.jobId) }
        }
        // Must be called for every start, even if the answer is already finished.
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(runs), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } catch (e: Exception) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (watcher == null) {
            val batches = (application as LlmHubApp).container.batchRunner
            watcher = scope.launch {
                // Chats and batches (AP-20) keep the service; it stops when neither runs.
                kotlinx.coroutines.flow.combine(runs.runs, batches.active, batches.current) { map, batch, _ -> map to batch }.collect { (map, batch) ->
                    if (map.isEmpty() && !batch) {
                        ServiceCompat.stopForeground(this@AgentService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf(lastStartId)
                    } else {
                        runCatching { getSystemService(android.app.NotificationManager::class.java).notify(NOTIFICATION_ID, notification(runs)) }
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    /** Android 15 limits background data work to a few hours per day. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        (application as LlmHubApp).container.runs.stopAll()
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(runs: ChatRuns): Notification {
        val active = runs.runs.value.values.toList()
        val titles = active.map { it.conversation.value.title }
        val c = (application as LlmHubApp).container
        val batch = c.batchRunner.current.value?.let { cur -> c.batches.get(cur.jobId)?.let { j -> "Stapel „${j.title}“: ${j.done} von ${j.items.size}" } }
        val text = when (titles.size) {
            0 -> batch ?: "Wird abgeschlossen …"
            1 -> titles.first()
            else -> "${titles.size} Chats: " + titles.joinToString(", ")
        }
        val stop = PendingIntent.getService(
            this, 1, Intent(this, AgentService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(this, Notifier.CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (titles.isEmpty() && batch != null) "Stapel läuft …" else "Antwort wird geschrieben …")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(0, 0, true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "Stoppen", stop)
        active.firstOrNull()?.let { builder.setContentIntent(Notifier.openChat(this, it.chatId)) }
        return builder.build()
    }

    companion object {
        private const val NOTIFICATION_ID = 4711
        private const val ACTION_STOP = "stop"

        fun start(context: Context) {
            Notifier.ensureChannel(context)
            // Only possible while the app is visible; if Android refuses, the answer still runs as long as the app lives.
            runCatching { ContextCompat.startForegroundService(context, Intent(context, AgentService::class.java)) }.logFailure("Agent-Dienst starten")
        }
    }
}
