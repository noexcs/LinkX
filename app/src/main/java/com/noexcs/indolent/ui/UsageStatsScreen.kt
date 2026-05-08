package com.noexcs.indolent.ui

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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noexcs.indolent.R
import com.noexcs.indolent.data.BalanceInfo
import com.noexcs.indolent.data.UserBalanceResponse
import com.noexcs.indolent.data.SettingsManager
import com.noexcs.indolent.data.fetchUserBalance

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageStatsScreen(settingsManager: SettingsManager, onBack: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val promptTokens = settingsManager.cumulativePromptTokens.toInt()
    val completionTokens = settingsManager.cumulativeCompletionTokens.toInt()
    val totalTokens = promptTokens + completionTokens

    var balanceState by remember { mutableStateOf<BalanceState>(BalanceState.Loading) }

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
            SectionCard(
                title = stringResource(R.string.section_token_usage),
                subtitle = stringResource(R.string.section_token_usage_subtitle)
            ) {
                StatRow(label = "Total tokens", value = formatTokens(totalTokens))
                StatRow(label = "Prompt tokens", value = formatTokens(promptTokens))
                StatRow(label = "Completion tokens", value = formatTokens(completionTokens))
            }

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
                            StatRow(label = "Total balance", value = "${info.totalBalance} ${info.currency}")
                            StatRow(label = "Topped up", value = "${info.toppedUpBalance} ${info.currency}")
                            StatRow(label = "Granted", value = "${info.grantedBalance} ${info.currency}")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
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
