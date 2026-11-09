package dev.proxboss.proxguard.data.alerts

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class AlertStore(private val file: File) {

    @Synchronized
    fun record(snapshot: AlertSnapshot, rules: AlertRules, now: Long = System.currentTimeMillis()): List<AlertEvent> {
        val state = readState()
        val evaluation = AlertEvaluator.evaluate(snapshot, rules, state.activeKeys, now)
        val updatedHistory = if (evaluation.triggered.isEmpty()) {
            state.history
        } else {
            (evaluation.triggered + state.history).take(MAX_HISTORY)
        }
        writeState(AlertState(evaluation.activeKeys, updatedHistory))
        return evaluation.triggered
    }

    @Synchronized
    fun history(): List<AlertEvent> = readState().history

    @Synchronized
    fun activeKeys(): Set<String> = readState().activeKeys

    @Synchronized
    fun clearHistory() {
        val state = readState()
        writeState(state.copy(history = emptyList()))
    }

    @Synchronized
    fun reset() {
        if (file.exists()) file.delete()
    }

    private fun readState(): AlertState = runCatching {
        if (!file.exists()) return@runCatching AlertState()
        val root = JSONObject(file.readText())
        val active = root.optJSONArray("activeKeys")
            ?.let { array -> (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) } }
            ?.toSet()
            .orEmpty()
        val historyArray = root.optJSONArray("history") ?: JSONArray()
        val history = (0 until historyArray.length()).mapNotNull { index ->
            val item = historyArray.optJSONObject(index) ?: return@mapNotNull null
            val severity = runCatching {
                AlertSeverity.valueOf(item.optString("severity", AlertSeverity.WARNING.name))
            }.getOrDefault(AlertSeverity.WARNING)
            AlertEvent(
                key = item.optString("key"),
                title = item.optString("title", "Alerte"),
                message = item.optString("message"),
                severity = severity,
                timestamp = item.optLong("timestamp", 0L),
            )
        }
        AlertState(active, history)
    }.getOrDefault(AlertState())

    private fun writeState(state: AlertState) {
        runCatching {
            file.parentFile?.mkdirs()
            val root = JSONObject().apply {
                put("activeKeys", JSONArray(state.activeKeys.toList()))
                put("history", JSONArray().apply {
                    state.history.forEach { event ->
                        put(JSONObject().apply {
                            put("key", event.key)
                            put("title", event.title)
                            put("message", event.message)
                            put("severity", event.severity.name)
                            put("timestamp", event.timestamp)
                        })
                    }
                })
            }
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(root.toString())
            if (!temp.renameTo(file)) {
                file.writeText(root.toString())
                temp.delete()
            }
        }
    }

    companion object {
        const val MAX_HISTORY = 200
    }
}
