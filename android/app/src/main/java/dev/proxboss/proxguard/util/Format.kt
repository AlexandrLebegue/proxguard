package dev.proxboss.proxguard.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val frenchSymbols = DecimalFormatSymbols(Locale.FRANCE)

fun formatBytes(bytes: Long): String {
    val value = bytes.coerceAtLeast(0L).toDouble()
    if (value < 1024.0) return "${bytes.coerceAtLeast(0L)} o"
    val units = listOf("Ko", "Mo", "Go", "To", "Po")
    var scaled = value / 1024.0
    var index = 0
    while (scaled >= 1024.0 && index < units.lastIndex) {
        scaled /= 1024.0
        index++
    }
    val pattern = if (scaled >= 100.0) "0" else "0.#"
    return "${DecimalFormat(pattern, frenchSymbols).format(scaled)} ${units[index]}"
}

fun formatBytesPerSecond(bytes: Long): String = "${formatBytes(bytes)}/s"

fun formatPercent(fraction: Double): String {
    val value = (fraction.coerceIn(0.0, 1.0)) * 100.0
    val pattern = if (value >= 99.95 || value < 0.1) "0" else "0.#"
    return "${DecimalFormat(pattern, frenchSymbols).format(value)} %"
}

fun formatPercentValue(value: Double): String {
    val clamped = value.coerceIn(0.0, 100.0)
    val pattern = if (clamped >= 99.95 || clamped < 0.1) "0" else "0.#"
    return "${DecimalFormat(pattern, frenchSymbols).format(clamped)} %"
}

fun formatPercentCompact(value: Double): String {
    val clamped = value.coerceIn(0.0, 100.0)
    return "${DecimalFormat(if (clamped >= 100.0 || clamped < 0.1) "0" else "0.#", frenchSymbols).format(clamped)}%"
}

fun formatDuration(seconds: Long): String {
    val s = seconds.coerceAtLeast(0L)
    return when {
        s < 60L -> "$s s"
        s < 3600L -> "${s / 60L} min"
        s < 86_400L -> "${s / 3600L} h ${(s % 3600L) / 60L} min"
        s < 31_536_000L -> "${s / 86_400L} j ${(s % 86_400L) / 3600L} h"
        else -> "${s / 31_536_000L} an ${(s % 31_536_000L) / 86_400L} j"
    }
}

fun formatUptimeShort(seconds: Long): String = formatDuration(seconds)

fun formatLoad(value: Double): String =
    DecimalFormat("0.00", frenchSymbols).format(value.coerceAtLeast(0.0))

fun formatDate(timestampMillis: Long): String =
    SimpleDateFormat("dd/MM/yyyy à HH:mm", Locale.FRANCE).format(Date(timestampMillis))

fun formatShortDate(timestampMillis: Long): String =
    SimpleDateFormat("dd/MM · HH:mm", Locale.FRANCE).format(Date(timestampMillis))

fun formatTime(timestampMillis: Long): String =
    SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(timestampMillis))

fun formatRelative(timestampMillis: Long, now: Long = System.currentTimeMillis()): String {
    val delta = (now - timestampMillis).coerceAtLeast(0L)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
    return when {
        minutes < 1L -> "à l'instant"
        minutes < 60L -> "il y a $minutes min"
        minutes < 1440L -> "il y a ${minutes / 60L} h"
        else -> "il y a ${minutes / 1440L} j"
    }
}

fun parsePveSize(raw: String): Long? {
    val text = raw.trim().uppercase(Locale.ROOT)
    val match = Regex("^([0-9]+(?:\\.[0-9]+)?)([KMGT]?)B?$").find(text) ?: return null
    val number = match.groupValues[1].toDoubleOrNull() ?: return null
    val multiplier = when (match.groupValues[2]) {
        "K" -> 1024.0
        "M" -> 1024.0 * 1024.0
        "G" -> 1024.0 * 1024.0 * 1024.0
        "T" -> 1024.0 * 1024.0 * 1024.0 * 1024.0
        else -> 1.0
    }
    return (number * multiplier).toLong()
}

fun pveVersionLabel(version: String): String {
    if (version.isBlank()) return "Proxmox VE"
    val parts = version.split('/')
    val number = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return version
    return "PVE $number"
}

fun taskTypeLabel(type: String): String {
    val normalized = type.lowercase(Locale.ROOT)
    return when {
        normalized.contains("vnc") -> "Console"
        normalized.contains("backup") || normalized.contains("vzdump") || normalized.contains("dump") -> "Sauvegarde"
        normalized.contains("restore") -> "Restauration"
        normalized.contains("snapshot") -> "Snapshot"
        normalized.contains("migrate") -> "Migration"
        normalized.contains("apt") || normalized.contains("update") -> "Mise à jour"
        normalized.contains("shutdown") -> "Extinction"
        normalized.contains("reboot") -> "Redémarrage"
        normalized.contains("start") -> "Démarrage"
        normalized.contains("stop") -> "Arrêt"
        normalized.contains("create") -> "Création"
        normalized.contains("destroy") -> "Suppression"
        normalized.contains("clone") -> "Clonage"
        normalized.contains("resize") -> "Redimensionnement"
        else -> normalized.replace('-', ' ')
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.FRANCE) else it.toString() }
            .ifBlank { "Tâche" }
    }
}

fun taskStatusLabel(status: String, running: Boolean): String = when {
    running -> "En cours"
    status.equals("OK", ignoreCase = true) -> "OK"
    status.isBlank() -> "Terminé"
    else -> status
}
