package dev.proxboss.proxguard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.proxboss.proxguard.data.model.TaskInfo
import dev.proxboss.proxguard.util.formatDuration
import dev.proxboss.proxguard.util.formatRelative
import dev.proxboss.proxguard.util.taskStatusLabel
import dev.proxboss.proxguard.util.taskTypeLabel

@Composable
fun TaskRow(
    task: TaskInfo,
    modifier: Modifier = Modifier,
    showResource: Boolean = false,
) {
    val color = when {
        task.running -> MaterialTheme.colorScheme.primary
        task.status.equals("OK", ignoreCase = true) -> MaterialTheme.colorScheme.secondary
        task.status.isBlank() -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.error
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Terminal,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = taskTypeLabel(task.type),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val details = buildString {
                append(formatRelative(task.startTime * 1000L))
                if (!task.running && task.durationSeconds > 0) {
                    append(" · ")
                    append(formatDuration(task.durationSeconds))
                }
                if (showResource && task.vmid.isNotBlank()) {
                    append(" · ")
                    append("invité ")
                    append(task.vmid)
                }
            }
            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        StatusPill(text = taskStatusLabel(task.status, task.running), color = color)
    }
}
