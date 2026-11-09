package dev.proxboss.proxguard

import dev.proxboss.proxguard.data.alerts.AlertEvaluator
import dev.proxboss.proxguard.data.alerts.AlertRules
import dev.proxboss.proxguard.data.alerts.AlertSeverity
import dev.proxboss.proxguard.data.alerts.AlertSnapshot
import dev.proxboss.proxguard.data.alerts.HostMetrics
import dev.proxboss.proxguard.data.model.Guest
import dev.proxboss.proxguard.data.model.GuestType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertEvaluatorTest {

    private fun guest(vmid: Int, status: String, name: String = "invite-$vmid") = Guest(
        vmid = vmid,
        name = name,
        node = "pve",
        type = GuestType.LXC,
        status = status,
        cpu = 0.0,
        maxCpu = 1,
        mem = 0L,
        maxMem = 0L,
        disk = 0L,
        maxDisk = 0L,
        uptime = 0L,
        template = false,
        tags = emptyList(),
        netIn = 0L,
        netOut = 0L,
    )

    private fun snapshot(
        cpu: Double = 10.0,
        mem: Double = 10.0,
        disk: Double = 10.0,
        guests: List<Guest> = emptyList(),
        online: Boolean = true,
    ) = AlertSnapshot(
        online = online,
        metrics = if (online) HostMetrics(cpu, mem, disk) else null,
        guests = guests,
        nodeName = "pve",
    )

    private val cpuOnly = AlertRules(
        cpuEnabled = true,
        cpuThreshold = 80,
        memEnabled = false,
        diskEnabled = false,
        guestStopped = false,
        hostUnreachable = false,
    )

    @Test
    fun `une alerte CPU ne se declenche qu une fois par franchissement`() {
        val first = AlertEvaluator.evaluate(snapshot(cpu = 85.0), cpuOnly, emptySet(), 1_000L)
        assertEquals(listOf("host_cpu"), first.triggered.map { it.key })
        assertEquals(setOf("host_cpu"), first.activeKeys)
        assertEquals(AlertSeverity.WARNING, first.triggered.first().severity)

        val second = AlertEvaluator.evaluate(snapshot(cpu = 90.0), cpuOnly, first.activeKeys, 2_000L)
        assertTrue(second.triggered.isEmpty())

        val resolved = AlertEvaluator.evaluate(snapshot(cpu = 20.0), cpuOnly, second.activeKeys, 3_000L)
        assertTrue(resolved.triggered.isEmpty())
        assertTrue(resolved.resolvedKeys.contains("host_cpu"))
        assertTrue(resolved.activeKeys.isEmpty())

        val again = AlertEvaluator.evaluate(snapshot(cpu = 85.0), cpuOnly, resolved.activeKeys, 4_000L)
        assertEquals(1, again.triggered.size)
    }

    @Test
    fun `le seuil est strictement depasse`() {
        val atThreshold = AlertEvaluator.evaluate(snapshot(cpu = 80.0), cpuOnly, emptySet(), 1L)
        assertTrue(atThreshold.triggered.isEmpty())
        val above = AlertEvaluator.evaluate(snapshot(cpu = 80.1), cpuOnly, emptySet(), 1L)
        assertEquals(1, above.triggered.size)
    }

    @Test
    fun `le CPU tres haut est critique`() {
        val evaluation = AlertEvaluator.evaluate(snapshot(cpu = 96.0), cpuOnly, emptySet(), 1L)
        assertEquals(AlertSeverity.CRITICAL, evaluation.triggered.first().severity)
    }

    @Test
    fun `chaque invite arrete a sa propre cle anti spam`() {
        val rules = AlertRules(
            cpuEnabled = false,
            memEnabled = false,
            diskEnabled = false,
            guestStopped = true,
            hostUnreachable = false,
        )
        val guests = listOf(
            guest(101, "stopped"),
            guest(102, "stopped"),
            guest(103, "running"),
        )
        val first = AlertEvaluator.evaluate(snapshot(guests = guests), rules, emptySet(), 1_000L)
        assertEquals(setOf("guest_stopped:101", "guest_stopped:102"), first.triggered.map { it.key }.toSet())
        assertEquals(2, first.triggered.size)

        val second = AlertEvaluator.evaluate(snapshot(guests = guests), rules, first.activeKeys, 2_000L)
        assertTrue(second.triggered.isEmpty())

        val partiallyFixed = guests.map { if (it.vmid == 101) it.copy(status = "running") else it }
        val resolved = AlertEvaluator.evaluate(snapshot(guests = partiallyFixed), rules, second.activeKeys, 3_000L)
        assertEquals(setOf("guest_stopped:101"), resolved.resolvedKeys)
        assertEquals(setOf("guest_stopped:102"), resolved.activeKeys)
    }

    @Test
    fun `l hote injoignable conserve les conditions deja actives`() {
        val active = setOf("host_cpu", "guest_stopped:100")
        val offline = AlertEvaluator.evaluate(snapshot(online = false), AlertRules(), active, 1_000L)
        assertEquals(listOf("host_offline"), offline.triggered.map { it.key })
        assertTrue(offline.activeKeys.containsAll(active + "host_offline"))

        val offlineAgain = AlertEvaluator.evaluate(snapshot(online = false), AlertRules(), offline.activeKeys, 2_000L)
        assertTrue(offlineAgain.triggered.isEmpty())

        val backOnline = AlertEvaluator.evaluate(
            snapshot(guests = listOf(guest(100, "running"))),
            AlertRules(),
            offlineAgain.activeKeys,
            3_000L,
        )
        assertTrue(backOnline.activeKeys.isEmpty())
        assertTrue(backOnline.resolvedKeys.containsAll(active + "host_offline"))
    }

    @Test
    fun `les regles desactivees ne declenchent rien`() {
        val rules = AlertRules(
            cpuEnabled = false,
            memEnabled = false,
            diskEnabled = false,
            guestStopped = false,
            hostUnreachable = false,
        )
        val evaluation = AlertEvaluator.evaluate(
            snapshot(cpu = 99.0, mem = 99.0, disk = 99.0, guests = listOf(guest(100, "stopped"))),
            rules,
            emptySet(),
            1L,
        )
        assertTrue(evaluation.triggered.isEmpty())
        assertTrue(evaluation.activeKeys.isEmpty())
    }

    @Test
    fun `les seuils hors bornes sont normalises`() {
        assertEquals(50, AlertRules(cpuThreshold = 10).sanitized().cpuThreshold)
        assertEquals(99, AlertRules(memThreshold = 120).sanitized().memThreshold)
        assertFalse(AlertRules(cpuThreshold = 85).sanitized().cpuThreshold == 10)
    }
}
