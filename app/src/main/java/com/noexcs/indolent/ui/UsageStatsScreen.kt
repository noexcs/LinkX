package com.noexcs.indolent.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noexcs.indolent.R
import com.noexcs.indolent.data.BalanceInfo
import com.noexcs.indolent.data.SettingsManager
import com.noexcs.indolent.data.TimePeriod
import com.noexcs.indolent.data.UsageStatisticsAggregator
import com.noexcs.indolent.data.UsageStats
import com.noexcs.indolent.data.fetchUserBalance
import com.noexcs.indolent.ui.settings.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageStatsScreen(
    settingsManager: SettingsManager,
    aggregator: UsageStatisticsAggregator,
    onBack: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val promptTokens = settingsManager.cumulativePromptTokens.toInt()
    val completionTokens = settingsManager.cumulativeCompletionTokens.toInt()
    val totalTokens = promptTokens + completionTokens

    var selectedPeriod by remember { mutableStateOf(TimePeriod.TOTAL) }
    var stats by remember { mutableStateOf<UsageStats?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var balanceState by remember { mutableStateOf<BalanceState>(BalanceState.Loading) }

    LaunchedEffect(selectedPeriod) {
        isLoading = true
        stats = aggregator.computeStats(selectedPeriod)
        isLoading = false
    }

    LaunchedEffect(Unit) {
        val baseUrl = settingsManager.baseUrl ?: ""
        val apiKey = settingsManager.apiKey ?: ""
        if (baseUrl.isBlank() || apiKey.isBlank()) {
            balanceState = BalanceState.NotConfigured
            return@LaunchedEffect
        }
        try {
            val response = fetchUserBalance(baseUrl, apiKey)
            if (response.isAvailable && response.balanceInfos.isNotEmpty()) {
                balanceState = BalanceState.Success(response.balanceInfos)
            } else {
                balanceState = BalanceState.Error
            }
        } catch (_: Exception) {
            balanceState = BalanceState.Error
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.title_usage_stats)) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Time period selector
            PeriodSelector(
                selected = selectedPeriod,
                onSelect = { selectedPeriod = it }
            )

            AnimatedVisibility(
                visible = isLoading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                SectionCard(
                    title = "",
                    subtitle = stringResource(R.string.stats_loading)
                ) {}
            }

            val currentStats = stats
            if (currentStats != null && !isLoading) {
                val isEmpty = currentStats.conversationCount == 0 &&
                    currentStats.scheduledTaskStats.count == 0 &&
                    currentStats.conditionalTriggerStats.count == 0 &&
                    currentStats.heartbeatStats.count == 0

                if (isEmpty) {
                    SectionCard(
                        title = stringResource(R.string.stats_overview),
                        subtitle = stringResource(R.string.stats_empty)
                    ) {}
                } else {
                    // Overview bar chart
                    SectionCard(
                        title = stringResource(R.string.stats_overview),
                        subtitle = stringResource(R.string.stats_overview_subtitle)
                    ) {
                        TaskTypeBarChart(stats = currentStats)
                    }

                    // Conversations
                    SectionCard(
                        title = stringResource(R.string.stats_conversations),
                        subtitle = stringResource(R.string.stats_conversations_subtitle)
                    ) {
                        BigStatCard(
                            value = currentStats.conversationCount,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Scheduled Tasks
                    val sched = currentStats.scheduledTaskStats
                    if (sched.count > 0) {
                        SectionCard(
                            title = stringResource(R.string.stats_scheduled_tasks),
                            subtitle = stringResource(R.string.stats_scheduled_subtitle)
                        ) {
                            BigStatCard(
                                value = sched.count,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            SuccessRateBar(
                                successCount = sched.successCount,
                                failureCount = sched.failureCount,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    // Conditional Triggers
                    val cond = currentStats.conditionalTriggerStats
                    if (cond.count > 0) {
                        SectionCard(
                            title = stringResource(R.string.stats_conditional_triggers),
                            subtitle = stringResource(R.string.stats_conditional_subtitle)
                        ) {
                            BigStatCard(
                                value = cond.count,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            SuccessRateBar(
                                successCount = cond.successCount,
                                failureCount = cond.failureCount,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }

                    // Heartbeat
                    val hb = currentStats.heartbeatStats
                    if (hb.count > 0) {
                        SectionCard(
                            title = stringResource(R.string.stats_heartbeat),
                            subtitle = stringResource(R.string.stats_heartbeat_subtitle)
                        ) {
                            BigStatCard(
                                value = hb.count,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                            SuccessRateBar(
                                successCount = hb.successCount,
                                failureCount = hb.failureCount,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Token Usage (preserved)
            SectionCard(
                title = stringResource(R.string.section_token_usage),
                subtitle = stringResource(R.string.section_token_usage_subtitle)
            ) {
                StatRow(label = "Total tokens", value = formatTokens(totalTokens))
                StatRow(label = "Prompt tokens", value = formatTokens(promptTokens))
                StatRow(label = "Completion tokens", value = formatTokens(completionTokens))
            }

            // User Balance (preserved)
            SectionCard(
                title = stringResource(R.string.section_user_balance),
                subtitle = stringResource(R.string.section_user_balance_subtitle)
            ) {
                when (val state = balanceState) {
                    is BalanceState.Loading -> {
                        Text(
                            "Loading...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    is BalanceState.NotConfigured -> {
                        Text(
                            "Configure API settings to view balance",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    is BalanceState.Error -> {
                        Text(
                            "Failed to fetch balance",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    is BalanceState.Success -> {
                        state.infos.forEach { info ->
                            StatRow(
                                label = "Total balance",
                                value = "${info.totalBalance} ${info.currency}"
                            )
                            StatRow(
                                label = "Topped up",
                                value = "${info.toppedUpBalance} ${info.currency}"
                            )
                            StatRow(
                                label = "Granted",
                                value = "${info.grantedBalance} ${info.currency}"
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PeriodSelector(
    selected: TimePeriod,
    onSelect: (TimePeriod) -> Unit
) {
    val periods = listOf(
        TimePeriod.TODAY to R.string.stats_period_today,
        TimePeriod.THIS_WEEK to R.string.stats_period_week,
        TimePeriod.THIS_MONTH to R.string.stats_period_month,
        TimePeriod.TOTAL to R.string.stats_period_total
    )

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        periods.forEachIndexed { index, (period, labelRes) ->
            SegmentedButton(
                selected = selected == period,
                onClick = { onSelect(period) },
                shape = SegmentedButtonDefaults.itemShape(index, periods.size)
            ) {
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun BigStatCard(
    value: Int,
    color: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineLarge,
            color = color
        )
    }
}

private sealed class BalanceState {
    data object Loading : BalanceState()
    data object NotConfigured : BalanceState()
    data object Error : BalanceState()
    data class Success(val infos: List<BalanceInfo>) : BalanceState()
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatTokens(n: Int): String = when {
    n >= 1_000_000 -> "${"%.1f".format(n / 1_000_000.0)}M"
    n >= 1_000 -> "${"%.1f".format(n / 1_000.0)}k"
    else -> n.toString()
}
