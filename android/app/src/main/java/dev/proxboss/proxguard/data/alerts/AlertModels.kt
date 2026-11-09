package dev.proxboss.proxguard.data.alerts

import dev.proxboss.proxguard.data.model.Guest

enum class AlertSeverity(val label: String) {
    INFO("Information"),
    WARNING("Attention"),
    CRITICAL("Critique"),
}

data class AlertRules(
    val cpuEnabled: Boolean = true,
    val cpuThreshold: Int = 85,
    val memEnabled: Boolean = true,
    val memThreshold: Int = 90,
    val diskEnabled: Boolean = true,
    val diskThreshold: Int = 85,
    val guestStopped: Boolean = true,
    val hostUnreachable: Boolean = true,
) {
    fun sanitized(): AlertRules = copy(
        cpuThreshold = cpuThreshold.coerceIn(50, 99),
        memThreshold = memThreshold.coerceIn(50, 99),
        diskThreshold = diskThreshold.coerceIn(50, 99),
    )
}

data class HostMetrics(
    val cpuPercent: Double,
    val memPercent: Double,
    val diskPercent: Double,
    val swapPercent: Double = 0.0,
    val load1: Double = 0.0,
)

data class AlertSnapshot(
    val online: Boolean,
    val metrics: HostMetrics? = null,
    val guests: List<Guest> = emptyList(),
    val nodeName: String = "pve",
    val errorMessage: String? = null,
)

data class AlertEvent(
    val key: String,
    val title: String,
    val message: String,
    val severity: AlertSeverity,
    val timestamp: Long,
)

data class AlertEvaluation(
    val triggered: List<AlertEvent>,
    val activeKeys: Set<String>,
    val resolvedKeys: Set<String>,
)

data class AlertState(
    val activeKeys: Set<String> = emptySet(),
    val history: List<AlertEvent> = emptyList(),
)
