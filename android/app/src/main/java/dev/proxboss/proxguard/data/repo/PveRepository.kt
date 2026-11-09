package dev.proxboss.proxguard.data.repo

import dev.proxboss.proxguard.data.alerts.AlertSnapshot
import dev.proxboss.proxguard.data.alerts.HostMetrics
import dev.proxboss.proxguard.data.api.ApiException
import dev.proxboss.proxguard.data.api.NoNodeException
import dev.proxboss.proxguard.data.api.PveClient
import dev.proxboss.proxguard.data.api.PveParsers
import dev.proxboss.proxguard.data.model.ConnectionTest
import dev.proxboss.proxguard.data.model.DashboardData
import dev.proxboss.proxguard.data.model.Guest
import dev.proxboss.proxguard.data.model.GuestDetail
import dev.proxboss.proxguard.data.model.GuestType
import dev.proxboss.proxguard.data.model.GuestsData
import dev.proxboss.proxguard.data.model.NodeSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PveRepository(private val client: PveClient) {

    suspend fun testConnection(): ConnectionTest = withContext(Dispatchers.IO) {
        client.testConnection()
    }

    suspend fun fetchDashboard(): DashboardData = withContext(Dispatchers.IO) {
        val nodes = PveParsers.parseNodes(client.get("/nodes"))
        val node = nodes.firstOrNull { it.online } ?: nodes.firstOrNull() ?: throw NoNodeException()
        val nodeName = node.node
        val status = PveParsers.parseNodeStatus(client.get("/nodes/$nodeName/status"))
        val samples = runCatching {
            PveParsers.parseRrd(client.get("/nodes/$nodeName/rrddata?timeframe=hour&cf=AVERAGE"))
        }.getOrDefault(emptyList())
        val guests = runCatching {
            PveParsers.parseGuests(client.get("/cluster/resources?type=vm"))
        }.getOrDefault(emptyList())
        val tasks = runCatching {
            PveParsers.parseTasks(client.get("/nodes/$nodeName/tasks?limit=20"))
        }.getOrDefault(emptyList())
        val updates = runCatching {
            PveParsers.parseAptUpdates(client.get("/nodes/$nodeName/apt/update"))
        }.getOrDefault(emptyList())
        val storages = runCatching {
            PveParsers.parseStorages(client.get("/nodes/$nodeName/storage"))
        }.getOrDefault(emptyList())

        DashboardData(
            nodeName = nodeName,
            nodeStatus = status,
            samples = samples,
            guests = guests,
            tasks = tasks,
            updates = updates,
            storages = storages,
            nodes = nodes,
            fetchedAt = System.currentTimeMillis(),
        )
    }

    suspend fun fetchGuests(): GuestsData = withContext(Dispatchers.IO) {
        val nodes = PveParsers.parseNodes(client.get("/nodes"))
        if (nodes.isEmpty()) throw NoNodeException()
        val guests = PveParsers.parseGuests(client.get("/cluster/resources?type=vm"))
        GuestsData(guests = guests, nodes = nodes, fetchedAt = System.currentTimeMillis())
    }

    suspend fun fetchGuestDetail(node: String, type: GuestType, vmid: Int): GuestDetail =
        withContext(Dispatchers.IO) {
            val guests = runCatching {
                PveParsers.parseGuests(client.get("/cluster/resources?type=vm"))
            }.getOrDefault(emptyList())
            val guest = guests.firstOrNull { it.vmid == vmid && it.type == type && it.node == node }
                ?: guests.firstOrNull { it.vmid == vmid && it.type == type }
                ?: throw ApiException(404, "Invité $vmid introuvable sur le nœud $node.")

            val warnings = mutableListOf<String>()

            val config = runCatching {
                client.get("/nodes/$node/${type.apiValue}/$vmid/config")
            }.onFailure { warnings += "Configuration indisponible." }.getOrNull()

            val tasks = runCatching {
                PveParsers.parseTasks(client.get("/nodes/$node/tasks?vmid=$vmid&limit=12"))
            }.onFailure { warnings += "Tâches récentes indisponibles." }.getOrDefault(emptyList())

            val samples = runCatching {
                PveParsers.parseRrd(
                    client.get("/nodes/$node/${type.apiValue}/$vmid/rrddata?timeframe=hour&cf=AVERAGE")
                )
            }.onFailure { warnings += "Courbes indisponibles pour cet invité." }.getOrDefault(emptyList())

            PveParsers.parseGuestDetail(guest, config, tasks, samples, warnings)
        }

    suspend fun fetchAlertSnapshot(): AlertSnapshot = withContext(Dispatchers.IO) {
        val nodes = PveParsers.parseNodes(client.get("/nodes"))
        val node = nodes.firstOrNull { it.online } ?: nodes.firstOrNull() ?: throw NoNodeException()
        val status = PveParsers.parseNodeStatus(client.get("/nodes/${node.node}/status"))
        val guests = runCatching {
            PveParsers.parseGuests(client.get("/cluster/resources?type=vm"))
        }.getOrDefault(emptyList())
        AlertSnapshot(
            online = node.online,
            metrics = HostMetrics(
                cpuPercent = status.cpuPercent,
                memPercent = status.memPercent,
                diskPercent = status.diskPercent,
                swapPercent = status.swapPercent,
                load1 = status.load1,
            ),
            guests = guests,
            nodeName = node.node,
        )
    }

    companion object {
        fun firstNode(nodes: List<NodeSummary>): NodeSummary? =
            nodes.firstOrNull { it.online } ?: nodes.firstOrNull()
    }
}
