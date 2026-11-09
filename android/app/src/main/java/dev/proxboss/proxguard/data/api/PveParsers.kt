package dev.proxboss.proxguard.data.api

import dev.proxboss.proxguard.data.model.AptUpdateInfo
import dev.proxboss.proxguard.data.model.ConfigField
import dev.proxboss.proxguard.data.model.DiskInfo
import dev.proxboss.proxguard.data.model.Guest
import dev.proxboss.proxguard.data.model.GuestDetail
import dev.proxboss.proxguard.data.model.GuestType
import dev.proxboss.proxguard.data.model.NodeStatus
import dev.proxboss.proxguard.data.model.NodeSummary
import dev.proxboss.proxguard.data.model.RrdPoint
import dev.proxboss.proxguard.data.model.StorageInfo
import dev.proxboss.proxguard.data.model.TaskInfo
import dev.proxboss.proxguard.util.formatBytes
import dev.proxboss.proxguard.util.parsePveSize
import org.json.JSONArray
import org.json.JSONObject

object PveParsers {

    fun parseNodes(root: JSONObject): List<NodeSummary> {
        val data = root.optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).mapNotNull { index ->
            val item = data.optJSONObject(index) ?: return@mapNotNull null
            val node = item.text("node") ?: return@mapNotNull null
            NodeSummary(
                node = node,
                status = item.text("status").orEmpty(),
                cpu = item.number("cpu"),
                maxCpu = item.integer("maxcpu", 1),
                mem = item.long("mem"),
                maxMem = item.long("maxmem"),
                disk = item.long("disk"),
                maxDisk = item.long("maxdisk"),
                uptime = item.long("uptime"),
                sslFingerprint = item.text("ssl_fingerprint"),
            )
        }
    }

    fun parseNodeStatus(root: JSONObject): NodeStatus {
        val data = root.optJSONObject("data") ?: JSONObject()
        val memory = data.optJSONObject("memory") ?: JSONObject()
        val swap = data.optJSONObject("swap") ?: JSONObject()
        val rootfs = data.optJSONObject("rootfs") ?: JSONObject()
        val cpuinfo = data.optJSONObject("cpuinfo") ?: JSONObject()
        val kernel = data.optJSONObject("current-kernel")?.text("release")
            ?: data.text("kversion").orEmpty()
        val load = data.optJSONArray("loadavg")
        return NodeStatus(
            cpu = data.number("cpu"),
            load1 = load.doubleAt(0),
            load5 = load.doubleAt(1),
            load15 = load.doubleAt(2),
            memUsed = memory.long("used"),
            memTotal = memory.long("total"),
            memAvailable = memory.long("available"),
            swapUsed = swap.long("used"),
            swapTotal = swap.long("total"),
            rootUsed = rootfs.long("used"),
            rootTotal = rootfs.long("total"),
            uptime = data.long("uptime"),
            pveVersion = data.text("pveversion").orEmpty(),
            cpuModel = cpuinfo.text("model").orEmpty(),
            cores = cpuinfo.integer("cores", 0),
            sockets = cpuinfo.integer("sockets", 1),
            kernel = kernel,
        )
    }

    fun parseRrd(root: JSONObject): List<RrdPoint> {
        val data = root.optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).mapNotNull { index ->
            val item = data.optJSONObject(index) ?: return@mapNotNull null
            val time = item.long("time")
            if (time <= 0L) return@mapNotNull null
            RrdPoint(
                time = time,
                cpu = item.number("cpu"),
                load = item.number("loadavg"),
                memUsed = item.number("memused").takeIf { it > 0.0 } ?: item.number("mem"),
                memTotal = item.number("memtotal").takeIf { it > 0.0 } ?: item.number("maxmem"),
                swapUsed = item.number("swapused"),
                swapTotal = item.number("swaptotal"),
                rootUsed = item.number("rootused").takeIf { it > 0.0 } ?: item.number("disk"),
                rootTotal = item.number("roottotal").takeIf { it > 0.0 } ?: item.number("maxdisk"),
                netIn = item.number("netin"),
                netOut = item.number("netout"),
            )
        }.sortedBy { it.time }
    }

    fun parseGuests(root: JSONObject): List<Guest> {
        val data = root.optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).mapNotNull { index ->
            val item = data.optJSONObject(index) ?: return@mapNotNull null
            val type = GuestType.from(item.text("type")) ?: return@mapNotNull null
            val vmid = item.integer("vmid", 0)
            if (vmid <= 0) return@mapNotNull null
            Guest(
                vmid = vmid,
                name = item.text("name").orEmpty(),
                node = item.text("node").orEmpty(),
                type = type,
                status = item.text("status").orEmpty(),
                cpu = item.number("cpu"),
                maxCpu = item.integer("maxcpu", 1),
                mem = item.long("mem"),
                maxMem = item.long("maxmem"),
                disk = item.long("disk"),
                maxDisk = item.long("maxdisk"),
                uptime = item.long("uptime"),
                template = item.integer("template", 0) == 1,
                tags = item.text("tags").orEmpty().split(';').map(String::trim).filter(String::isNotBlank),
                netIn = item.long("netin"),
                netOut = item.long("netout"),
            )
        }.sortedWith(compareByDescending<Guest> { it.running }.thenBy { it.displayName.lowercase() })
    }

    fun parseTasks(root: JSONObject): List<TaskInfo> {
        val data = root.optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).mapNotNull { index ->
            val item = data.optJSONObject(index) ?: return@mapNotNull null
            TaskInfo(
                upid = item.text("upid").orEmpty(),
                type = item.text("type").orEmpty(),
                status = item.text("status").orEmpty(),
                startTime = item.long("starttime"),
                endTime = item.long("endtime"),
                node = item.text("node").orEmpty(),
                vmid = item.text("id").orEmpty(),
                user = item.text("user").orEmpty(),
            )
        }.sortedByDescending { it.startTime }
    }

    fun parseDisks(root: JSONObject): List<DiskInfo> {
        val data = root.optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).mapNotNull { index ->
            val item = data.optJSONObject(index) ?: return@mapNotNull null
            DiskInfo(
                devPath = item.text("devpath").orEmpty(),
                model = item.text("model").orEmpty(),
                serial = item.text("serial").orEmpty(),
                type = item.text("type").orEmpty(),
                size = item.long("size"),
                health = item.text("health").orEmpty(),
                wearout = item.text("wearout").orEmpty(),
                used = item.text("used").orEmpty(),
            )
        }
    }

    fun parseAptUpdates(root: JSONObject): List<AptUpdateInfo> {
        val data = root.optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).mapNotNull { index ->
            val item = data.optJSONObject(index) ?: return@mapNotNull null
            AptUpdateInfo(
                packageName = item.text("Package").orEmpty().ifBlank { item.text("PackageName").orEmpty() },
                version = item.text("Version").orEmpty(),
                oldVersion = item.text("OldVersion").orEmpty(),
                title = item.text("Title").orEmpty().ifBlank { item.text("Description").orEmpty() },
                origin = item.text("Origin").orEmpty(),
                section = item.text("Section").orEmpty(),
            )
        }.filter { it.packageName.isNotBlank() }
    }

    fun parseStorages(root: JSONObject): List<StorageInfo> {
        val data = root.optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).mapNotNull { index ->
            val item = data.optJSONObject(index) ?: return@mapNotNull null
            val name = item.text("storage") ?: return@mapNotNull null
            StorageInfo(
                storage = name,
                type = item.text("type").orEmpty(),
                content = item.text("content").orEmpty(),
                total = item.long("total"),
                used = item.long("used"),
                avail = item.long("avail"),
                active = item.integer("active", 1) == 1,
            )
        }
    }

    fun parseGuestDetail(
        guest: Guest,
        config: JSONObject?,
        tasks: List<TaskInfo>,
        samples: List<RrdPoint>,
        warnings: List<String> = emptyList(),
    ): GuestDetail {
        val fields = if (config == null) {
            ConfigFields(emptyList(), emptyList(), emptyList())
        } else {
            parseConfig(guest.type, config)
        }
        return GuestDetail(
            guest = guest,
            summary = fields.summary,
            disks = fields.disks,
            networks = fields.networks,
            tasks = tasks.filter { it.vmid == guest.vmid.toString() },
            samples = samples,
            warnings = warnings,
        )
    }

    private data class ConfigFields(
        val summary: List<ConfigField>,
        val disks: List<ConfigField>,
        val networks: List<ConfigField>,
    )

    private fun parseConfig(type: GuestType, config: JSONObject): ConfigFields {
        val summary = mutableListOf<ConfigField>()
        val disks = mutableListOf<ConfigField>()
        val networks = mutableListOf<ConfigField>()

        fun add(label: String, raw: String?, convert: (String) -> String = { it }) {
            if (raw.isNullOrBlank()) return
            summary += ConfigField(label, convert(raw))
        }

        fun boolText(value: String): String = when (value) {
            "1" -> "Oui"
            "0" -> "Non"
            else -> value
        }

        fun memoryText(value: String): String {
            val megabytes = value.toLongOrNull() ?: return value
            return formatBytes(megabytes * 1024L * 1024L)
        }

        val sockets = config.integer("sockets", 0)
        val cores = config.integer("cores", typeIfLxcDefault(type))
        if (cores > 0) {
            val total = if (type == GuestType.QEMU && sockets > 1) cores * sockets else cores
            summary += ConfigField("vCPU", total.toString())
        }
        if (sockets > 1) summary += ConfigField("Sockets", sockets.toString())
        add("Mémoire", config.text("memory"), ::memoryText)
        add("Swap", config.text("swap"), ::memoryText)
        add("Type d'OS", config.text("ostype"))
        add("Architecture", config.text("arch"))
        add("BIOS", config.text("bios"))
        add("Machine", config.text("machine"))
        add("Démarrage auto", config.text("onboot"), ::boolText)
        add("Protection", config.text("protection"), ::boolText)
        if (type == GuestType.LXC) {
            add("Hostname", config.text("hostname"))
            add("Mode", config.text("unprivileged")) { if (it == "1") "Non privilégié" else "Privilégié" }
            add("Fonctionnalités", config.text("features"))
            add("Console", config.text("console"))
            add("TTY", config.text("tty"))
        } else {
            val agent = config.optJSONObject("agent")
            if (agent != null) {
                summary += ConfigField("Agent invité", if (agent.integer("enabled", 0) == 1) "Activé" else "Désactivé")
            } else {
                add("Agent invité", config.text("agent"), ::boolText)
            }
            add("Disque de démarrage", config.text("bootdisk"))
            add("Ordre de démarrage", config.text("boot"))
            add("NUMA", config.text("numa"), ::boolText)
            add("Ballooning", config.text("balloon"), ::memoryText)
            add("Contrôleur SCSI", config.text("scsihw"))
            add("Affichage", config.text("vga"))
        }
        add("Type de CPU", config.text("cpu"))
        add("DNS", config.text("nameserver"))
        add("Domaine de recherche", config.text("searchdomain"))
        add("Tags", config.text("tags")) { it.split(';').joinToString(" · ") }
        add("Verrou", config.text("lock"))
        add("Template", config.text("template"), ::boolText)

        val diskPattern = Regex("^(?:scsi|virtio|sata|ide|mp)\\d+$|^rootfs$")
        config.keys().asSequence()
            .filter { diskPattern.matches(it) }
            .sorted()
            .forEach { key ->
                val raw = config.text(key) ?: return@forEach
                val parts = raw.split(',')
                val volume = parts.firstOrNull().orEmpty()
                val size = parts.firstOrNull { it.startsWith("size=") }?.removePrefix("size=")
                val mount = parts.firstOrNull { it.startsWith("mp=") }?.removePrefix("mp=")
                val isCdrom = raw.contains("media=cdrom") || raw.contains("cdrom")
                val label = when {
                    key == "rootfs" -> "Disque racine"
                    key.startsWith("mp") -> "Montage ${key.drop(2)}"
                    isCdrom -> key.uppercase() + " (CD)"
                    else -> key.uppercase()
                }
                val description = if (isCdrom) {
                    "Lecteur CD · $volume"
                } else {
                    buildList {
                        add(volume)
                        size?.let { add(parsePveSize(it)?.let(::formatBytes) ?: it) }
                        mount?.let { add(it) }
                    }.joinToString(" · ")
                }
                disks += ConfigField(label, description)
            }

        val networkPattern = Regex("^net\\d+$")
        config.keys().asSequence()
            .filter { networkPattern.matches(it) }
            .sorted()
            .forEach { key ->
                val raw = config.text(key) ?: return@forEach
                networks += ConfigField(key.uppercase(), raw.split(',').joinToString(" · "))
            }

        return ConfigFields(summary, disks, networks)
    }

    private fun typeIfLxcDefault(type: GuestType): Int = if (type == GuestType.LXC) 1 else 0

    private fun JSONObject.text(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return opt(key)?.toString()
    }

    private fun JSONObject.number(key: String): Double {
        if (!has(key) || isNull(key)) return 0.0
        return optDouble(key, 0.0)
    }

    private fun JSONObject.long(key: String): Long {
        if (!has(key) || isNull(key)) return 0L
        return optLong(key, 0L)
    }

    private fun JSONObject.integer(key: String, fallback: Int): Int {
        if (!has(key) || isNull(key)) return fallback
        return optInt(key, fallback)
    }

    private fun JSONArray?.doubleAt(index: Int): Double {
        if (this == null || index >= length() || isNull(index)) return 0.0
        return optDouble(index, 0.0)
    }
}
