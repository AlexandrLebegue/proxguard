package dev.proxboss.proxguard.di

import android.content.Context
import dev.proxboss.proxguard.data.alerts.AlertStore
import dev.proxboss.proxguard.data.api.PveClient
import dev.proxboss.proxguard.data.config.AppConfig
import dev.proxboss.proxguard.data.config.ConfigStore
import dev.proxboss.proxguard.data.history.MetricStore
import dev.proxboss.proxguard.data.repo.PveRepository
import java.io.File

class AppContainer(context: Context) {

    val configStore = ConfigStore(context)
    val metricStore = MetricStore(File(context.filesDir, "metrics_history.json"))
    val alertStore = AlertStore(File(context.filesDir, "alerts_state.json"))

    fun repository(config: AppConfig): PveRepository =
        PveRepository(PveClient(config, PveClient.buildHttpClient(config)))
}
