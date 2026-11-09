package dev.proxboss.proxguard

import android.app.Application
import dev.proxboss.proxguard.di.AppContainer
import dev.proxboss.proxguard.work.AlertScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ProxGuardApp : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch {
            val config = container.configStore.current()
            AlertScheduler.sync(this@ProxGuardApp, config)
        }
    }
}
