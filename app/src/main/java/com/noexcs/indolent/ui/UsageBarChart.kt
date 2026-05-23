package com.noexcs.indolent.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.noexcs.indolent.data.UsageStats
import com.noexcs.indolent.ui.theme.GentleSpring
import androidx.compose.ui.res.stringResource
import com.noexcs.indolent.R

@Composable
fun TaskTypeBarChart(stats: UsageStats, modifier: Modifier = Modifier) {
    val items = listOf(
        BarItem(stringResource(R.string.stats_conversations), stats.conversationCount.toFloat(), MaterialTheme.colorScheme.primary),
        BarItem(stringResource(R.string.stats_scheduled_tasks), stats.scheduledTaskStats.count.toFloat(), MaterialTheme.colorScheme.secondary),
        BarItem(stringResource(R.string.stats_conditional_triggers), stats.conditionalTriggerStats.count.toFloat(), MaterialTheme.colorScheme.tertiary),
        BarItem(stringResource(R.string.stats_heartbeat_short), stats.heartbeatStats.count.toFloat(), MaterialTheme.colorScheme.surfaceContainerHighest),
    )

    val maxCount = items.maxOf { it.value }.coerceAtLeast(1f)
    val maxLabelWidth = 140.dp

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items.forEach { item ->
            val targetFraction = item.value / maxCount
            val animatedFraction by animateFloatAsState(
                targetValue = targetFraction,
                animationSpec = GentleSpring,
                label = "barGrow"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(maxLabelWidth)
                )
                Spacer(modifier = Modifier.width(8.dp))
                AnimatedBar(
                    fraction = animatedFraction,
                    color = item.color,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.value.toInt().toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun SuccessRateBar(
    successCount: Int,
    failureCount: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    val total = successCount + failureCount
    val successFraction = if (total > 0) successCount.toFloat() / total else 0f
    val animatedSuccessFraction by animateFloatAsState(
        targetValue = successFraction,
        animationSpec = GentleSpring,
        label = "successRateGrow"
    )

    val barHeight = 8.dp
    val barColor = color
    val errorColor = MaterialTheme.colorScheme.error

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
        ) {
            val w = size.width
            val h = size.height
            val radius = CornerRadius(h / 2)

            // Background track
            drawRoundRect(
                color = errorColor.copy(alpha = 0.3f),
                cornerRadius = radius
            )
            // Success portion
            drawRoundRect(
                color = barColor,
                topLeft = androidx.compose.ui.geometry.Offset.Zero,
                size = androidx.compose.ui.geometry.Size(w * animatedSuccessFraction, h),
                cornerRadius = radius
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${stringResource(R.string.stats_success)}: $successCount",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${stringResource(R.string.stats_failure)}: $failureCount",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class BarItem(
    val label: String,
    val value: Float,
    val color: Color
)

@Composable
private fun AnimatedBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val barHeight = 20.dp

    Canvas(
        modifier = modifier
            .height(barHeight)
            .fillMaxWidth()
    ) {
        val h = size.height
        val radius = CornerRadius(h / 2)

        // Background track
        drawRoundRect(
            color = color.copy(alpha = 0.15f),
            cornerRadius = radius
        )
        // Filled portion
        if (fraction > 0f) {
            drawRoundRect(
                color = color,
                size = androidx.compose.ui.geometry.Size(size.width * fraction.coerceIn(0f, 1f), h),
                cornerRadius = radius
            )
        }
    }
}
