package dev.proxboss.proxguard.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.proxboss.proxguard.ProxGuardApp
import dev.proxboss.proxguard.data.alerts.AlertSnapshot
import dev.proxboss.proxguard.data.api.toUserMessage
import dev.proxboss.proxguard.data.history.MetricSample
import dev.proxboss.proxguard.notify.NotificationHelper

class AlertCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? ProxGuardApp ?: return Result.success()
        return try {
            val config = app.container.configStore.current()
            if (!config.isConfigured || !config.alertsEnabled) return Result.success()

            val repository = app.container.repository(config)
            val snapshot = try {
                repository.fetchAlertSnapshot()
            } catch (error: Throwable) {
                AlertSnapshot(
                    online = false,
                    nodeName = config.host ?: "pve",
                    errorMessage = error.toUserMessage(),
                )
            }
            val now = System.currentTimeMillis()
            if (snapshot.online) {
                snapshot.metrics?.let { metrics ->
                    app.container.metricStore.append(
                        MetricSample(
                            time = now,
                            cpu = metrics.cpuPercent,
                            mem = metrics.memPercent,
                            swap = metrics.swapPercent,
                            disk = metrics.diskPercent,
                            load = metrics.load1,
                            netIn = 0.0,
                            netOut = 0.0,
                        )
                    )
                }
            }
            val events = app.container.alertStore.record(snapshot, config.rules, now)
            if (events.isNotEmpty()) {
                NotificationHelper.notifyEvents(applicationContext, events)
            }
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        }
    }
}
