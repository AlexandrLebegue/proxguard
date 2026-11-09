package dev.proxboss.proxguard

import dev.proxboss.proxguard.data.config.normalizeBaseUrl
import dev.proxboss.proxguard.data.config.validateBaseUrl
import dev.proxboss.proxguard.data.config.validateTokenId
import dev.proxboss.proxguard.util.formatBytes
import dev.proxboss.proxguard.util.formatDuration
import dev.proxboss.proxguard.util.formatPercent
import dev.proxboss.proxguard.util.formatPercentCompact
import dev.proxboss.proxguard.util.formatPercentValue
import dev.proxboss.proxguard.util.formatRelative
import dev.proxboss.proxguard.util.parsePveSize
import dev.proxboss.proxguard.util.pveVersionLabel
import dev.proxboss.proxguard.util.taskTypeLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FormatTest {

    @Test
    fun `formate les tailles en francais`() {
        assertEquals("0 o", formatBytes(0))
        assertEquals("1023 o", formatBytes(1023))
        assertEquals("1,5 Ko", formatBytes(1536))
        assertEquals("1 Go", formatBytes(1024L * 1024L * 1024L))
        assertEquals("63,9 Go", formatBytes(68_630_765_568L))
    }

    @Test
    fun `formate les durees`() {
        assertEquals("45 s", formatDuration(45))
        assertEquals("2 min", formatDuration(125))
        assertEquals("2 h 5 min", formatDuration(7_500))
        assertEquals("6 j 4 h", formatDuration(532_850))
        assertEquals("1 an 2 j", formatDuration(31_536_000L + 2 * 86_400L))
    }

    @Test
    fun `formate les pourcentages`() {
        assertEquals("12,3 %", formatPercent(0.1234))
        assertEquals("100 %", formatPercent(1.0))
        assertEquals("0 %", formatPercent(0.0))
        assertEquals("13,1 %", formatPercentValue(13.13))
        assertEquals("12,5%", formatPercentCompact(12.53))
    }

    @Test
    fun `normalise les URL Proxmox`() {
        assertEquals("https://192.168.1.55:8006", normalizeBaseUrl("192.168.1.55:8006"))
        assertEquals("https://pve.example.lan:8006", normalizeBaseUrl("https://pve.example.lan:8006/api2/json/"))
        assertEquals("", normalizeBaseUrl("   "))
        assertNull(validateBaseUrl("https://pve.example.lan:8006"))
        assertNotNull(validateBaseUrl(""))
        assertNull(validateTokenId("root@pam!aoe-mobile"))
        assertNotNull(validateTokenId("root@pam"))
    }

    @Test
    fun `convertit les tailles Proxmox`() {
        assertEquals(32L * 1024 * 1024 * 1024, parsePveSize("32G"))
        assertEquals(512L * 1024 * 1024, parsePveSize("512M"))
        assertNull(parsePveSize("inconnu"))
    }

    @Test
    fun `resume la version PVE et les types de taches`() {
        assertEquals(
            "PVE 9.2.2",
            pveVersionLabel("pve-manager/9.2.2/b9984c6d90a4bd80"),
        )
        assertEquals("Console", taskTypeLabel("vncproxy"))
        assertEquals("Mise à jour", taskTypeLabel("aptupdate"))
        assertEquals("Sauvegarde", taskTypeLabel("vzdump"))
        assertEquals("Snapshot", taskTypeLabel("vzsnapshot"))
    }

    @Test
    fun `formate le temps relatif avec une horloge fixe`() {
        val now = 1_700_000_000_000L
        assertEquals("il y a 3 min", formatRelative(now - 3 * 60_000L, now))
        assertEquals("il y a 2 h", formatRelative(now - 2 * 3_600_000L, now))
        assertEquals("il y a 1 j", formatRelative(now - 25 * 3_600_000L, now))
    }
}
