package dev.proxboss.proxguard.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.proxboss.proxguard.data.config.AppConfig
import java.util.concurrent.TimeUnit

object AlertScheduler {

    const val WORK_NAME = "proxguard-alert-check"
    const val INTERVAL_MINUTES = 15L

    fun sync(context: Context, config: AppConfig) {
        val workManager = WorkManager.getInstance(context)
        if (config.isConfigured && config.alertsEnabled) {
            val request = PeriodicWorkRequestBuilder<AlertCheckWorker>(
                INTERVAL_MINUTES,
                TimeUnit.MINUTES,
            )
                .setInitialDelay(INTERVAL_MINUTES, TimeUnit.MINUTES)
                .build()
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        } else {
            workManager.cancelUniqueWork(WORK_NAME)
        }
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
