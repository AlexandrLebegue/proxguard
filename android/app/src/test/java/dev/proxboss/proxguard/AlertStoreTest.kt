package dev.proxboss.proxguard

import dev.proxboss.proxguard.data.alerts.AlertRules
import dev.proxboss.proxguard.data.alerts.AlertSnapshot
import dev.proxboss.proxguard.data.alerts.AlertStore
import dev.proxboss.proxguard.data.alerts.HostMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AlertStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val rules = AlertRules(
        cpuEnabled = true,
        cpuThreshold = 80,
        memEnabled = false,
        diskEnabled = false,
        guestStopped = false,
        hostUnreachable = false,
    )

    private fun snapshot(cpu: Double) = AlertSnapshot(
        online = true,
        metrics = HostMetrics(cpu, 10.0, 10.0),
        nodeName = "pve",
    )

    @Test
    fun `l anti spam persiste entre deux instances`() {
        val file = File(folder.root, "alerts.json")
        val first = AlertStore(file)
        val triggered = first.record(snapshot(92.0), rules, 1_000L)
        assertEquals(1, triggered.size)

        val second = AlertStore(file)
        assertTrue(second.record(snapshot(93.0), rules, 2_000L).isEmpty())
        assertEquals(1, second.history().size)
        assertEquals(setOf("host_cpu"), second.activeKeys())
    }

    @Test
    fun `un nouveau franchissement apres retour a la normale declenche a nouveau`() {
        val store = AlertStore(File(folder.root, "alerts.json"))
        assertEquals(1, store.record(snapshot(92.0), rules, 1_000L).size)
        assertTrue(store.record(snapshot(10.0), rules, 2_000L).isEmpty())
        val secondCrossing = store.record(snapshot(92.0), rules, 3_000L)
        assertEquals(1, secondCrossing.size)
        assertEquals(2, store.history().size)
    }

    @Test
    fun `un fichier corrompu ne fait pas planter le stockage`() {
        val file = File(folder.root, "alerts.json")
        file.writeText("ceci n'est pas du json")
        val store = AlertStore(file)
        assertEquals(1, store.record(snapshot(92.0), rules, 1_000L).size)
        assertEquals(1, store.history().size)
    }

    @Test
    fun `effacer l historique conserve l etat anti spam`() {
        val store = AlertStore(File(folder.root, "alerts.json"))
        store.record(snapshot(92.0), rules, 1_000L)
        store.clearHistory()
        assertTrue(store.history().isEmpty())
        assertTrue(store.record(snapshot(92.0), rules, 2_000L).isEmpty())
    }
}
