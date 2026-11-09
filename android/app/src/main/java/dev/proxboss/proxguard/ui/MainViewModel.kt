package dev.proxboss.proxguard.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.proxboss.proxguard.ProxGuardApp
import dev.proxboss.proxguard.data.alerts.AlertEvent
import dev.proxboss.proxguard.data.alerts.AlertRules
import dev.proxboss.proxguard.data.alerts.AlertSnapshot
import dev.proxboss.proxguard.data.alerts.HostMetrics
import dev.proxboss.proxguard.data.api.toUserMessage
import dev.proxboss.proxguard.data.config.AppConfig
import dev.proxboss.proxguard.data.config.normalizeBaseUrl
import dev.proxboss.proxguard.data.history.MetricSample
import dev.proxboss.proxguard.data.model.ConnectionTest
import dev.proxboss.proxguard.data.model.DashboardData
import dev.proxboss.proxguard.data.model.GuestDetail
import dev.proxboss.proxguard.data.model.GuestType
import dev.proxboss.proxguard.data.model.GuestsData
import dev.proxboss.proxguard.data.model.ThemeMode
import dev.proxboss.proxguard.notify.NotificationHelper
import dev.proxboss.proxguard.work.AlertScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AsyncUi<T>(
    val data: T? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
)

data class TestUi(
    val loading: Boolean = false,
    val result: ConnectionTest? = null,
    val error: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ProxGuardApp
    private val container = app.container

    val config: StateFlow<AppConfig?> = container.configStore.config
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _dashboard = MutableStateFlow(AsyncUi<DashboardData>())
    val dashboard: StateFlow<AsyncUi<DashboardData>> = _dashboard.asStateFlow()

    private val _guests = MutableStateFlow(AsyncUi<GuestsData>())
    val guests: StateFlow<AsyncUi<GuestsData>> = _guests.asStateFlow()

    private val _guestDetail = MutableStateFlow(AsyncUi<GuestDetail>())
    val guestDetail: StateFlow<AsyncUi<GuestDetail>> = _guestDetail.asStateFlow()

    private val _test = MutableStateFlow(TestUi())
    val test: StateFlow<TestUi> = _test.asStateFlow()

    private val _incidents = MutableStateFlow<List<AlertEvent>>(emptyList())
    val incidents: StateFlow<List<AlertEvent>> = _incidents.asStateFlow()

    private val _metrics = MutableStateFlow<List<MetricSample>>(emptyList())
    val metrics: StateFlow<List<MetricSample>> = _metrics.asStateFlow()

    private var dashboardJob: Job? = null
    private var guestsJob: Job? = null
    private var detailJob: Job? = null
    private var previousConnectionKey: Triple<String, String, String>? = null

    init {
        viewModelScope.launch {
            config.filterNotNull().collect { current ->
                val key = Triple(current.normalizedBaseUrl, current.tokenId, current.secret)
                val shouldLoad = previousConnectionKey != key
                previousConnectionKey = key
                AlertScheduler.sync(app, current)
                if (!current.isConfigured) {
                    resetDataStates()
                } else if (shouldLoad) {
                    _dashboard.value = AsyncUi()
                    _guests.value = AsyncUi()
                    _guestDetail.value = AsyncUi()
                    refreshDashboard()
                    refreshGuests()
                }
                refreshMetrics()
                refreshIncidents()
            }
        }
        refreshMetrics()
        refreshIncidents()
    }

    fun refreshDashboard(silent: Boolean = false) {
        if (dashboardJob?.isActive == true) return
        val currentConfig = config.value ?: return
        if (!currentConfig.isConfigured) return
        dashboardJob = viewModelScope.launch {
            val current = _dashboard.value
            _dashboard.value = current.copy(
                loading = current.data == null && !silent,
                refreshing = current.data != null || silent,
                error = null,
            )
            try {
                val repository = container.repository(currentConfig)
                val data = repository.fetchDashboard()
                _dashboard.value = AsyncUi(data = data)
                container.metricStore.append(data.toMetricSample())
                refreshMetrics()
                if (currentConfig.alertsEnabled) {
                    val events = container.alertStore.record(data.toAlertSnapshot(), currentConfig.rules)
                    if (events.isNotEmpty()) {
                        NotificationHelper.notifyEvents(app, events)
                        refreshIncidents()
                    }
                }
            } catch (error: Throwable) {
                val message = error.toUserMessage()
                _dashboard.value = _dashboard.value.copy(
                    loading = false,
                    refreshing = false,
                    error = message,
                )
                if (currentConfig.alertsEnabled) {
                    val offline = AlertSnapshot(
                        online = false,
                        nodeName = currentConfig.host ?: "pve",
                        errorMessage = message,
                    )
                    val events = container.alertStore.record(offline, currentConfig.rules)
                    if (events.isNotEmpty()) {
                        NotificationHelper.notifyEvents(app, events)
                    }
                    refreshIncidents()
                }
            }
        }
    }

    fun refreshGuests(silent: Boolean = false) {
        if (guestsJob?.isActive == true) return
        val currentConfig = config.value ?: return
        if (!currentConfig.isConfigured) return
        guestsJob = viewModelScope.launch {
            val current = _guests.value
            _guests.value = current.copy(
                loading = current.data == null && !silent,
                refreshing = current.data != null || silent,
                error = null,
            )
            try {
                val data = container.repository(currentConfig).fetchGuests()
                _guests.value = AsyncUi(data = data)
            } catch (error: Throwable) {
                _guests.value = _guests.value.copy(
                    loading = false,
                    refreshing = false,
                    error = error.toUserMessage(),
                )
            }
        }
    }

    fun loadGuestDetail(node: String, type: GuestType, vmid: Int, force: Boolean = false) {
        val existing = _guestDetail.value
        if (!force && existing.data?.guest?.vmid == vmid && existing.error == null && !existing.loading) return
        val currentConfig = config.value ?: return
        if (!currentConfig.isConfigured) return
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            _guestDetail.value = AsyncUi(
                data = existing.data?.takeIf { it.guest.vmid == vmid },
                loading = existing.data?.guest?.vmid != vmid,
                refreshing = existing.data?.guest?.vmid == vmid,
            )
            try {
                val detail = container.repository(currentConfig).fetchGuestDetail(node, type, vmid)
                _guestDetail.value = AsyncUi(data = detail)
            } catch (error: Throwable) {
                _guestDetail.value = _guestDetail.value.copy(
                    loading = false,
                    refreshing = false,
                    error = error.toUserMessage(),
                )
            }
        }
    }

    fun testConnection(candidate: AppConfig) {
        if (_test.value.loading) return
        _test.value = TestUi(loading = true)
        viewModelScope.launch {
            val normalized = candidate.copy(baseUrl = normalizeBaseUrl(candidate.baseUrl))
            runCatching { container.repository(normalized).testConnection() }
                .onSuccess { result -> _test.value = TestUi(result = result) }
                .onFailure { error -> _test.value = TestUi(error = error.toUserMessage()) }
        }
    }

    fun clearTest() {
        _test.value = TestUi()
    }

    fun saveConfig(candidate: AppConfig, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching { container.configStore.save(candidate) }
            if (result.isSuccess) {
                AlertScheduler.sync(app, container.configStore.current())
            }
            onResult(result)
        }
    }

    fun setThemeMode(mode: ThemeMode) = updateConfig { it.copy(themeMode = mode) }

    fun setRefreshSeconds(seconds: Int) = updateConfig { it.copy(refreshSeconds = seconds.coerceIn(15, 300)) }

    fun setAlertsEnabled(enabled: Boolean) {
        updateConfig { it.copy(alertsEnabled = enabled) }
    }

    fun updateRules(rules: AlertRules) {
        updateConfig { it.copy(rules = rules.sanitized()) }
    }

    fun refreshIncidents() {
        viewModelScope.launch {
            _incidents.value = withContext(Dispatchers.IO) { container.alertStore.history() }
        }
    }

    fun refreshMetrics() {
        viewModelScope.launch {
            _metrics.value = withContext(Dispatchers.IO) { container.metricStore.samples() }
        }
    }

    fun clearIncidents() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.alertStore.clearHistory() }
            _incidents.value = emptyList()
        }
    }

    fun clearMetrics() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.metricStore.clear() }
            _metrics.value = emptyList()
        }
    }

    fun clearLocalData() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                container.metricStore.clear()
                container.alertStore.clearHistory()
            }
            _metrics.value = emptyList()
            _incidents.value = emptyList()
        }
    }

    fun resetApplication(onDone: () -> Unit) {
        viewModelScope.launch {
            AlertScheduler.cancel(app)
            dashboardJob?.cancel()
            guestsJob?.cancel()
            detailJob?.cancel()
            withContext(Dispatchers.IO) {
                container.alertStore.reset()
                container.metricStore.clear()
                container.configStore.clear()
            }
            resetDataStates()
            _metrics.value = emptyList()
            _incidents.value = emptyList()
            _test.value = TestUi()
            onDone()
        }
    }

    private fun updateConfig(transform: (AppConfig) -> AppConfig) {
        viewModelScope.launch {
            container.configStore.update(transform)
            AlertScheduler.sync(app, container.configStore.current())
        }
    }

    private fun resetDataStates() {
        _dashboard.value = AsyncUi()
        _guests.value = AsyncUi()
        _guestDetail.value = AsyncUi()
    }
}

private fun DashboardData.toAlertSnapshot(): AlertSnapshot = AlertSnapshot(
    online = true,
    metrics = HostMetrics(
        cpuPercent = nodeStatus.cpuPercent,
        memPercent = nodeStatus.memPercent,
        diskPercent = nodeStatus.diskPercent,
        swapPercent = nodeStatus.swapPercent,
        load1 = nodeStatus.load1,
    ),
    guests = guests,
    nodeName = nodeName,
)

private fun DashboardData.toMetricSample(): MetricSample {
    val latest = samples.lastOrNull()
    return MetricSample(
        time = fetchedAt,
        cpu = nodeStatus.cpuPercent,
        mem = nodeStatus.memPercent,
        swap = nodeStatus.swapPercent,
        disk = nodeStatus.diskPercent,
        load = latest?.load ?: nodeStatus.load1,
        netIn = latest?.netIn ?: 0.0,
        netOut = latest?.netOut ?: 0.0,
    )
}
