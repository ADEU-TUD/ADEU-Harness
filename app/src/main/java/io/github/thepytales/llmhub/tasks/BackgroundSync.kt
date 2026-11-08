package io.github.thepytales.llmhub.tasks

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.thepytales.llmhub.LlmHubApp
import io.github.thepytales.llmhub.data.AppSettings
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Sync im Hintergrund (AP-63): Android's WorkManager runs the phone sync (AP-28) about every 30 minutes, also while the
 * app is closed – with a network (Wi-Fi only if wished) and not on a low battery. So a literature entry made on the PC
 * is on the phone before the app is opened. The Windows version of this file does nothing (the PCs sync with the cloud
 * service's own client, AP-12).
 */
object BackgroundSync {
    private const val WORK = "phone_sync"
    const val MINUTES = 30L

    /** Whether background sync is wanted with [s]. */
    fun wanted(s: AppSettings): Boolean = s.hasNextcloud && s.phoneSyncRoot.isNotBlank() && s.phoneSyncBackground

    /** Plans (or cancels) the periodic sync according to [s]; a plan that exists with the same rules is kept. */
    fun schedule(context: Context, s: AppSettings) {
        val wm = WorkManager.getInstance(context)
        if (!wanted(s)) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (s.phoneSyncWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(MINUTES, TimeUnit.MINUTES, 10, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .addTag(WORK)
            .build()
        // UPDATE keeps the running period and only takes the new rules (Wi-Fi only on or off).
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val c = (applicationContext as LlmHubApp).container
            return try {
                val text = c.phoneSync.background() ?: return Result.success()
                // A PC has the folder open or its changes are on their way: try again at the next turn, no retry storm.
                if (text.startsWith("Sync fehlgeschlagen")) Result.retry() else Result.success()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.retry()
            }
        }
    }
}
