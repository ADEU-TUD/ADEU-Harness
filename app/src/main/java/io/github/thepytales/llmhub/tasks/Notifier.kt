package io.github.thepytales.llmhub.tasks

import io.github.thepytales.llmhub.logFailure
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.thepytales.llmhub.MainActivity
import io.github.thepytales.llmhub.R
import io.github.thepytales.llmhub.data.ScheduledTask

/** System notifications: finished answers, results of scheduled tasks and questions of the agent. */
object Notifier {
    /** High importance, so results pop up on the screen and not only in the status bar. */
    private const val CHANNEL_RESULTS = "results"
    /** Silent notification while an answer is generated in the background. */
    const val CHANNEL_RUNNING = "running"
    /** Channel of earlier versions (default importance, no pop-up). */
    private const val OLD_CHANNEL = "tasks"
    const val EXTRA_CHAT = "chatId"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel(OLD_CHANNEL)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RESULTS, "Ergebnisse", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Fertige Antworten, Ergebnisse geplanter Aufgaben und Rückfragen des Agenten"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RUNNING, "Laufende Antworten", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Wird angezeigt, solange eine Antwort im Hintergrund entsteht"
                setShowBadge(false)
            }
        )
    }

    /** Notifications are allowed for the app (permission on Android 13+) and the results channel is on. */
    fun canNotify(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL_RESULTS)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /** Android may delay or stop background work of apps with battery optimisation. */
    fun ignoresBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    fun notificationSettingsIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        }

    @SuppressLint("BatteryLife")
    fun batteryIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

    /** Opens [chatId] in the app. */
    fun openChat(context: Context, chatId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_CHAT, chatId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context, chatId.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun preview(answer: String?): String? =
        answer?.replace(Regex("[#*_`>|]"), "")?.replace(Regex("\\s+"), " ")?.trim()?.take(400)?.ifBlank { null }

    @SuppressLint("MissingPermission")
    private fun post(context: Context, id: Int, title: String, text: String, chatId: String?) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_RESULTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(if (chatId != null) openChat(context, chatId) else openApp(context))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }.logFailure("Benachrichtigung")
    }

    fun taskFinished(context: Context, task: ScheduledTask, chatId: String, answer: String?, error: String?) {
        post(context, task.id.hashCode(), task.name, error ?: preview(answer) ?: "Fertig – tippen, um das Ergebnis zu sehen.", chatId)
    }

    /** A batch (AP-20) is finished; tapping opens the app. */
    fun batchFinished(context: Context, title: String, text: String) {
        post(context, ("batch:" + title).hashCode(), title, text, null)
    }

    /** An answer that was generated in the background is ready. */
    fun chatFinished(context: Context, title: String, chatId: String, answer: String?, error: String?) {
        post(context, chatId.hashCode(), title, error?.let { "Fehler: $it" } ?: preview(answer) ?: "Antwort fertig.", chatId)
    }

    /** The agent waits for permission to run a write action. */
    fun confirmationNeeded(context: Context, title: String, chatId: String, action: String) {
        post(context, chatId.hashCode(), title, "Der Agent wartet auf deine Bestätigung für „$action“. Tippen zum Öffnen.", chatId)
    }

    fun cancel(context: Context, chatId: String) {
        NotificationManagerCompat.from(context).cancel(chatId.hashCode())
    }

    /** Shows a sample notification so the user can check that results will reach them. */
    fun test(context: Context) {
        post(context, "test".hashCode(), "Lora", "So sieht es aus, wenn eine Aufgabe fertig ist – Benachrichtigungen funktionieren.", null)
    }
}
