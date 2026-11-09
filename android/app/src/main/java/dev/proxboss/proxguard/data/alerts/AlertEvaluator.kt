package dev.proxboss.proxguard.data.alerts

import kotlin.math.roundToInt

object AlertEvaluator {

    fun evaluate(
        snapshot: AlertSnapshot,
        rules: AlertRules,
        activeKeys: Set<String>,
        now: Long,
    ): AlertEvaluation {
        val safeRules = rules.sanitized()
        val current = linkedSetOf<String>()
        val triggered = mutableListOf<AlertEvent>()

        fun trigger(key: String, title: String, message: String, severity: AlertSeverity) {
            current += key
            if (key !in activeKeys) {
                triggered += AlertEvent(
                    key = key,
                    title = title,
                    message = message,
                    severity = severity,
                    timestamp = now,
                )
            }
        }

        if (!snapshot.online) {
            if (safeRules.hostUnreachable) {
                trigger(
                    key = "host_offline",
                    title = "Hôte injoignable",
                    message = "Impossible de joindre « ${snapshot.nodeName} ». Vérifie l'URL, le réseau et le jeton d'API.",
                    severity = AlertSeverity.CRITICAL,
                )
            }
            current += activeKeys.filterNot { it == "host_offline" }
        } else {
            val metrics = snapshot.metrics
            if (metrics != null) {
                if (safeRules.cpuEnabled && metrics.cpuPercent > safeRules.cpuThreshold) {
                    val severity = if (metrics.cpuPercent >= 95.0) AlertSeverity.CRITICAL else AlertSeverity.WARNING
                    trigger(
                        key = "host_cpu",
                        title = "CPU de l'hôte élevé",
                        message = "Le CPU de « ${snapshot.nodeName} » est à ${metrics.cpuPercent.roundToInt()} % " +
                            "(seuil ${safeRules.cpuThreshold} %).",
                        severity = severity,
                    )
                }
                if (safeRules.memEnabled && metrics.memPercent > safeRules.memThreshold) {
                    val severity = if (metrics.memPercent >= 95.0) AlertSeverity.CRITICAL else AlertSeverity.WARNING
                    trigger(
                        key = "host_mem",
                        title = "Mémoire de l'hôte élevée",
                        message = "La RAM de « ${snapshot.nodeName} » est à ${metrics.memPercent.roundToInt()} % " +
                            "(seuil ${safeRules.memThreshold} %).",
                        severity = severity,
                    )
                }
                if (safeRules.diskEnabled && metrics.diskPercent > safeRules.diskThreshold) {
                    val severity = if (metrics.diskPercent >= 95.0) AlertSeverity.CRITICAL else AlertSeverity.WARNING
                    trigger(
                        key = "host_disk",
                        title = "Disque de l'hôte presque plein",
                        message = "Le disque racine de « ${snapshot.nodeName} » est à ${metrics.diskPercent.roundToInt()} % " +
                            "(seuil ${safeRules.diskThreshold} %).",
                        severity = severity,
                    )
                }
            }
            if (safeRules.guestStopped) {
                snapshot.guests
                    .filter { !it.template && it.stopped }
                    .forEach { guest ->
                        trigger(
                            key = "guest_stopped:${guest.vmid}",
                            title = "Invité arrêté",
                            message = "« ${guest.displayName} » (${guest.type.iconLabel} ${guest.vmid}) est arrêté.",
                            severity = AlertSeverity.WARNING,
                        )
                    }
            }
        }

        val resolved = activeKeys - current
        return AlertEvaluation(
            triggered = triggered,
            activeKeys = current,
            resolvedKeys = resolved,
        )
    }
}
