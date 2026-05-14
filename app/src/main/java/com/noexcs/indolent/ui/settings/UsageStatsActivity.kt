package com.noexcs.indolent.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import com.noexcs.indolent.data.SettingsManager
import com.noexcs.indolent.data.UsageStatisticsAggregator
import com.noexcs.indolent.ui.UsageStatsScreen

class UsageStatsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSettingsActivity()
        val settingsManager = SettingsManager(applicationContext)
        val aggregator = UsageStatisticsAggregator(applicationContext)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { finish() } })
        setContent {
            SettingsActivityTheme {
                UsageStatsScreen(
                    settingsManager = settingsManager,
                    aggregator = aggregator,
                    onBack = { onBackPressedDispatcher.onBackPressed() }
                )
            }
        }
    }
}
