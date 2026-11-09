package dev.proxboss.proxguard

import dev.proxboss.proxguard.data.history.MetricCodec
import dev.proxboss.proxguard.data.history.MetricSample
import dev.proxboss.proxguard.data.history.MetricStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MetricStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun sample(time: Long, cpu: Double = 10.0) = MetricSample(
        time = time,
        cpu = cpu,
        mem = 40.0,
        swap = 2.0,
        disk = 30.0,
        load = 1.5,
        netIn = 1024.0,
        netOut = 512.0,
    )

    @Test
    fun `encode et decode le JSON de maniere stable`() {
        val samples = listOf(sample(1_000L), sample(2_000L, cpu = 42.0))
        val decoded = MetricCodec.decode(MetricCodec.encode(samples))
        assertEquals(samples, decoded)
    }

    @Test
    fun `purge les echantillons de plus de 24 heures`() {
        val now = 1_700_000_000_000L
        val old = sample(now - 25 * 3_600_000L)
        val recent = sample(now - 23 * 3_600_000L)
        val fresh = sample(now)
        val purged = MetricCodec.purge(listOf(old, fresh, recent), now)
        assertEquals(listOf(recent, fresh), purged)
    }

    @Test
    fun `remplace le dernier echantillon trop proche`() {
        val file = File(folder.root, "metrics.json")
        val store = MetricStore(file)
        val now = 1_700_000_000_000L
        store.append(sample(now))
        store.append(sample(now + 5_000L, cpu = 42.0))
        assertEquals(1, store.samples(now = now + 6_000L).size)
        assertEquals(42.0, store.samples(now = now + 6_000L).first().cpu, 0.001)

        store.append(sample(now + 20_000L, cpu = 60.0))
        assertEquals(2, store.samples(now = now + 21_000L).size)
    }

    @Test
    fun `lit un fichier absent ou corrompu sans planter`() {
        val store = MetricStore(File(folder.root, "absent.json"))
        assertTrue(store.samples(now = 1_000L).isEmpty())

        val corrupt = File(folder.root, "corrupt.json")
        corrupt.writeText("[] pas du json")
        assertTrue(MetricStore(corrupt).samples(now = 1_000L).isEmpty())
    }
}
