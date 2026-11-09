package dev.proxboss.proxguard.data.history

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class MetricSample(
    val time: Long,
    val cpu: Double,
    val mem: Double,
    val swap: Double,
    val disk: Double,
    val load: Double,
    val netIn: Double,
    val netOut: Double,
)

class MetricStore(private val file: File) {

    @Synchronized
    fun append(sample: MetricSample, minGapMs: Long = 10_000L) {
        runCatching {
            val samples = readInternal().toMutableList()
            val last = samples.lastOrNull()
            if (last != null && sample.time >= last.time && sample.time - last.time < minGapMs) {
                samples[samples.lastIndex] = sample
            } else {
                samples += sample
            }
            writeInternal(purge(samples, sample.time))
        }
    }

    @Synchronized
    fun samples(windowMs: Long = WINDOW_MS, now: Long = System.currentTimeMillis()): List<MetricSample> {
        return runCatching {
            val stored = readInternal()
            val purged = purge(stored, now, windowMs)
            if (purged.size != stored.size) writeInternal(purged)
            purged
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun clear() {
        runCatching { if (file.exists()) file.delete() }
    }

    private fun purge(samples: List<MetricSample>, now: Long, windowMs: Long = WINDOW_MS): List<MetricSample> =
        MetricCodec.purge(samples, now, windowMs)

    private fun readInternal(): List<MetricSample> = runCatching {
        if (!file.exists()) emptyList() else MetricCodec.decode(file.readText())
    }.getOrDefault(emptyList())

    private fun writeInternal(samples: List<MetricSample>) {
        runCatching {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(MetricCodec.encode(samples))
            if (!temp.renameTo(file)) {
                file.writeText(MetricCodec.encode(samples))
                temp.delete()
            }
        }
    }

    companion object {
        const val WINDOW_MS = 24L * 60L * 60L * 1000L
    }
}

object MetricCodec {

    fun encode(samples: List<MetricSample>): String {
        val array = JSONArray()
        samples.forEach { sample ->
            array.put(JSONObject().apply {
                put("t", sample.time)
                put("c", sample.cpu)
                put("m", sample.mem)
                put("s", sample.swap)
                put("d", sample.disk)
                put("l", sample.load)
                put("ni", sample.netIn)
                put("no", sample.netOut)
            })
        }
        return array.toString()
    }

    fun decode(text: String): List<MetricSample> {
        if (text.isBlank()) return emptyList()
        val array = JSONArray(text)
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val time = item.optLong("t", 0L)
            if (time <= 0L) return@mapNotNull null
            MetricSample(
                time = time,
                cpu = item.optDouble("c", 0.0),
                mem = item.optDouble("m", 0.0),
                swap = item.optDouble("s", 0.0),
                disk = item.optDouble("d", 0.0),
                load = item.optDouble("l", 0.0),
                netIn = item.optDouble("ni", 0.0),
                netOut = item.optDouble("no", 0.0),
            )
        }.sortedBy { it.time }
    }

    fun purge(samples: List<MetricSample>, now: Long, windowMs: Long = MetricStore.WINDOW_MS): List<MetricSample> {
        val cutoff = now - windowMs
        return samples
            .filter { it.time in cutoff..(now + 60_000L) }
            .sortedBy { it.time }
    }
}
