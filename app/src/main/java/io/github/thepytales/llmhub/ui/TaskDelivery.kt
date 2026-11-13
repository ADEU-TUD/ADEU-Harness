package io.github.thepytales.llmhub.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.tasks.Notifier

/**
 * Shows whether results can reach the user (notifications) and whether Android lets tasks run in the
 * background, with buttons to fix both.
 */
@Composable
fun DeliveryStatus(@Suppress("UNUSED_PARAMETER") c: AppContainer) {
    val context = LocalContext.current
    var notify by remember { mutableStateOf(Notifier.canNotify(context)) }
    var background by remember { mutableStateOf(Notifier.ignoresBatteryOptimizations(context)) }
    LifecycleResumeEffect(Unit) {
        notify = Notifier.canNotify(context)
        background = Notifier.ignoresBatteryOptimizations(context)
        onPauseOrDispose { }
    }
    fun openSettings(intent: Intent) {
        runCatching { context.startActivity(intent) }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notify = Notifier.canNotify(context)
        // Asked too often before: Android no longer shows the dialog, so open the settings.
        if (!granted) openSettings(Notifier.notificationSettingsIntent(context))
    }
    Surface(color = LocalChatLook.current.card, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusLine(
                notify,
                if (notify) tr("Benachrichtigungen sind an – du bekommst eine Nachricht, sobald ein Ergebnis da ist.")
                else tr("Benachrichtigungen sind aus – Ergebnisse siehst du nur hier in der App."),
            )
            if (notify) {
                TextButton(onClick = { Notifier.test(context) }) { Text(tr("Test-Benachrichtigung senden")) }
            } else {
                Button(onClick = {
                    val missing = Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    if (missing) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else openSettings(Notifier.notificationSettingsIntent(context))
                }) { Text(tr("Benachrichtigungen einschalten")) }
            }
            StatusLine(
                background,
                if (background) tr("Aufgaben dürfen im Hintergrund laufen – auch wenn die App geschlossen ist.")
                else tr("Android darf Aufgaben im Energiesparmodus verzögern oder abbrechen, wenn die App geschlossen ist."),
            )
            if (!background) {
                OutlinedButton(onClick = {
                    runCatching { context.startActivity(Notifier.batteryIntent(context)) }
                        .onFailure { openSettings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                }) { Text(tr("Im Hintergrund erlauben")) }
            }
        }
    }
}

/** Asks once for the notification permission (Android 13+) when a task should notify. */
@Composable
fun rememberAskNotifications(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Notices.error(tr("Ohne Berechtigung gibt es keine Benachrichtigungen – die Ergebnisse landen trotzdem im Ordner."))
    }
    return {
        if (Build.VERSION.SDK_INT >= 33 && !Notifier.canNotify(context)) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
