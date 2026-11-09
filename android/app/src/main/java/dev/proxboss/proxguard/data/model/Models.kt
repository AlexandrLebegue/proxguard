package dev.proxboss.proxguard.data.model

enum class GuestType(val apiValue: String, val label: String, val iconLabel: String) {
    QEMU("qemu", "VM", "VM"),
    LXC("lxc", "Conteneur", "CT");

    companion object {
        fun from(value: String?): GuestType? = entries.firstOrNull { it.apiValue == value }
    }
}

enum class ThemeMode(val label: String) {
    DARK("Sombre"),
    LIGHT("Clair"),
    SYSTEM("Système");

    companion object {
        fun from(value: String?): ThemeMode = entries.firstOrNull { it.name == value } ?: DARK
    }
}

data class NodeSummary(
    val node: String,
    val status: String,
    val cpu: Double,
    val maxCpu: Int,
    val mem: Long,
    val maxMem: Long,
    val disk: Long,
    val maxDisk: Long,
    val uptime: Long,
    val sslFingerprint: String?,
) {
    val online: Boolean get() = status == "online"
}

data class NodeStatus(
    val cpu: Double,
    val load1: Double,
    val load5: Double,
    val load15: Double,
    val memUsed: Long,
    val memTotal: Long,
    val memAvailable: Long,
    val swapUsed: Long,
    val swapTotal: Long,
    val rootUsed: Long,
    val rootTotal: Long,
    val uptime: Long,
    val pveVersion: String,
    val cpuModel: String,
    val cores: Int,
    val sockets: Int,
    val kernel: String,
) {
    val memPercent: Double get() = percent(memUsed, memTotal)
    val swapPercent: Double get() = percent(swapUsed, swapTotal)
    val diskPercent: Double get() = percent(rootUsed, rootTotal)
    val cpuPercent: Double get() = (cpu.coerceIn(0.0, 1.0)) * 100.0

    private fun percent(used: Long, total: Long): Double =
        if (total <= 0L) 0.0 else (used.toDouble() / total.toDouble() * 100.0).coerceIn(0.0, 100.0)
}

data class RrdPoint(
    val time: Long,
    val cpu: Double,
    val load: Double,
    val memUsed: Double,
    val memTotal: Double,
    val swapUsed: Double,
    val swapTotal: Double,
    val rootUsed: Double,
    val rootTotal: Double,
    val netIn: Double,
    val netOut: Double,
) {
    val cpuPercent: Double get() = (cpu.coerceIn(0.0, 1.0)) * 100.0
    val memPercent: Double get() = ratio(memUsed, memTotal)
    val swapPercent: Double get() = ratio(swapUsed, swapTotal)
    val diskPercent: Double get() = ratio(rootUsed, rootTotal)

    private fun ratio(used: Double, total: Double): Double =
        if (total <= 0.0) 0.0 else (used / total * 100.0).coerceIn(0.0, 100.0)
}

data class Guest(
    val vmid: Int,
    val name: String,
    val node: String,
    val type: GuestType,
    val status: String,
    val cpu: Double,
    val maxCpu: Int,
    val mem: Long,
    val maxMem: Long,
    val disk: Long,
    val maxDisk: Long,
    val uptime: Long,
    val template: Boolean,
    val tags: List<String>,
    val netIn: Long,
    val netOut: Long,
) {
    val running: Boolean get() = status == "running"
    val stopped: Boolean get() = status == "stopped"
    val displayName: String get() = name.ifBlank { "${type.iconLabel} $vmid" }
    val cpuPercent: Double get() = (cpu * 100.0).coerceAtLeast(0.0)
    val memPercent: Double get() = if (maxMem <= 0) 0.0 else (mem.toDouble() / maxMem * 100.0).coerceIn(0.0, 100.0)
    val diskPercent: Double get() = if (maxDisk <= 0) 0.0 else (disk.toDouble() / maxDisk * 100.0).coerceIn(0.0, 100.0)
    val statusLabel: String
        get() = when (status) {
            "running" -> "Actif"
            "stopped" -> "Arrêté"
            "paused" -> "En pause"
            "suspended" -> "Suspendu"
            else -> status.ifBlank { "Inconnu" }
        }
}

data class TaskInfo(
    val upid: String,
    val type: String,
    val status: String,
    val startTime: Long,
    val endTime: Long,
    val node: String,
    val vmid: String,
    val user: String,
) {
    val running: Boolean get() = endTime <= 0L || status.isBlank()
    val durationSeconds: Long get() = if (endTime > startTime) endTime - startTime else 0L
}

data class DiskInfo(
    val devPath: String,
    val model: String,
    val serial: String,
    val type: String,
    val size: Long,
    val health: String,
    val wearout: String,
    val used: String,
)

data class AptUpdateInfo(
    val packageName: String,
    val version: String,
    val oldVersion: String,
    val title: String,
    val origin: String,
    val section: String,
)

data class StorageInfo(
    val storage: String,
    val type: String,
    val content: String,
    val total: Long,
    val used: Long,
    val avail: Long,
    val active: Boolean,
) {
    val usedPercent: Double get() = if (total <= 0) 0.0 else (used.toDouble() / total * 100.0).coerceIn(0.0, 100.0)
}

data class GuestDetail(
    val guest: Guest,
    val summary: List<ConfigField>,
    val disks: List<ConfigField>,
    val networks: List<ConfigField>,
    val tasks: List<TaskInfo>,
    val samples: List<RrdPoint>,
    val warnings: List<String> = emptyList(),
)

data class ConfigField(val label: String, val value: String)

data class DashboardData(
    val nodeName: String,
    val nodeStatus: NodeStatus,
    val samples: List<RrdPoint>,
    val guests: List<Guest>,
    val tasks: List<TaskInfo>,
    val updates: List<AptUpdateInfo>,
    val storages: List<StorageInfo>,
    val nodes: List<NodeSummary>,
    val fetchedAt: Long,
) {
    val runningGuests: Int get() = guests.count { it.running }
    val stoppedGuests: Int get() = guests.count { !it.running && !it.template }
    val vmCount: Int get() = guests.count { it.type == GuestType.QEMU && !it.template }
    val containerCount: Int get() = guests.count { it.type == GuestType.LXC && !it.template }
}

data class GuestsData(
    val guests: List<Guest>,
    val nodes: List<NodeSummary>,
    val fetchedAt: Long,
)

data class ConnectionTest(
    val pveVersion: String,
    val nodeName: String,
    val nodeCount: Int,
    val fingerprint: String?,
)
