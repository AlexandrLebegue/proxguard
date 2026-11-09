package dev.proxboss.proxguard

import dev.proxboss.proxguard.data.api.PveParsers
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PveParsersTest {

    private fun resource(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource(name)) { "Ressource absente : $name" }.readText()

    private fun root(name: String): JSONObject = JSONObject(resource(name))

    @Test
    fun `parse les noeuds reels`() {
        val nodes = PveParsers.parseNodes(root("nodes.json"))
        assertEquals(1, nodes.size)
        val node = nodes.first()
        assertEquals("pve", node.node)
        assertTrue(node.online)
        assertEquals(12, node.maxCpu)
        assertEquals(33_570_926_592L, node.maxMem)
        assertNotNull(node.sslFingerprint)
        assertTrue(node.sslFingerprint!!.contains(":"))
    }

    @Test
    fun `parse le statut de l hote`() {
        val status = PveParsers.parseNodeStatus(root("status.json"))
        assertTrue(status.pveVersion.startsWith("pve-manager/"))
        assertEquals(6, status.cores)
        assertEquals(1, status.sockets)
        assertTrue(status.load1 > 0.0)
        assertTrue(status.memTotal > 0L)
        assertTrue(status.memPercent in 0.0..100.0)
        assertTrue(status.cpuPercent in 0.0..100.0)
        assertTrue(status.diskPercent in 0.0..100.0)
        assertTrue(status.swapTotal > 0L)
        assertTrue(status.kernel.isNotBlank())
    }

    @Test
    fun `parse les courbes rrd de l hote`() {
        val points = PveParsers.parseRrd(root("rrddata.json"))
        assertEquals(60, points.size)
        assertEquals(points.sortedBy { it.time }, points)
        assertTrue(points.all { it.memPercent in 0.0..100.0 })
        assertTrue(points.any { it.netIn > 0.0 })
    }

    @Test
    fun `parse les courbes rrd d un invite avec les champs mem et maxmem`() {
        val points = PveParsers.parseRrd(root("lxc_rrddata.json"))
        assertEquals(60, points.size)
        assertTrue(points.any { it.memPercent > 0.0 })
        assertTrue(points.any { it.diskPercent > 0.0 })
    }

    @Test
    fun `parse les invites et leurs tags`() {
        val guests = PveParsers.parseGuests(root("resources.json"))
        assertEquals(12, guests.size)
        assertTrue(guests.all { it.type == dev.proxboss.proxguard.data.model.GuestType.LXC })
        val first = guests.first { it.vmid == 100 }
        assertEquals("openclaw", first.name)
        assertTrue(first.running)
        assertTrue(first.tags.contains("community-script"))
        assertTrue(first.tags.contains("os"))
    }

    @Test
    fun `parse les taches recentes`() {
        val tasks = PveParsers.parseTasks(root("tasks.json"))
        assertEquals(20, tasks.size)
        assertTrue(tasks.first().startTime >= tasks.last().startTime)
        assertTrue(tasks.any { it.vmid == "100" })
        assertTrue(tasks.any { it.status == "OK" })
    }

    @Test
    fun `parse les disques et leur sante`() {
        val disks = PveParsers.parseDisks(root("disks.json"))
        assertEquals(2, disks.size)
        val first = disks.first()
        assertEquals("/dev/sda", first.devPath)
        assertEquals("PASSED", first.health)
        assertTrue(first.size > 0L)
    }

    @Test
    fun `parse les mises a jour apt`() {
        val updates = PveParsers.parseAptUpdates(root("apt.json"))
        assertEquals(201, updates.size)
        assertTrue(updates.any { it.packageName == "librados2" })
        assertTrue(updates.all { it.packageName.isNotBlank() })
    }

    @Test
    fun `parse le stockage`() {
        val storages = PveParsers.parseStorages(root("storage.json"))
        assertEquals(3, storages.size)
        assertTrue(storages.any { it.storage == "local-lvm" })
        assertTrue(storages.all { it.total >= 0L })
    }

    @Test
    fun `resume la configuration reelle d un conteneur`() {
        val config = root("lxc_config.json").getJSONObject("data")
        val guest = PveParsers.parseGuests(root("resources.json")).first { it.vmid == 100 }
        val tasks = PveParsers.parseTasks(root("tasks.json"))
        val samples = PveParsers.parseRrd(root("lxc_rrddata.json"))

        val detail = PveParsers.parseGuestDetail(guest, config, tasks, samples)

        assertEquals("openclaw", detail.guest.name)
        assertTrue(detail.warnings.isEmpty())
        assertTrue(detail.summary.any { it.label == "Hostname" && it.value == "openclaw" })
        assertTrue(detail.summary.any { it.label == "vCPU" && it.value == "4" })
        assertTrue(detail.summary.any { it.label == "Mémoire" && it.value.contains("Go") })
        assertTrue(detail.disks.any { it.label == "Disque racine" && it.value.contains("24 Go") })
        assertTrue(detail.networks.any { it.label == "NET0" && it.value.contains("vmbr0") })
        assertTrue(detail.tasks.isNotEmpty())
        assertTrue(detail.tasks.all { it.vmid == "100" })
        assertTrue(detail.samples.isNotEmpty())
        assertFalse(detail.summary.any { it.label == "Description" })
    }
}
