package com.noexcs.indolent.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.noexcs.indolent.data.SettingsManager
import com.noexcs.indolent.ui.ConditionalTriggerListScreen
import com.noexcs.indolent.ui.HeartbeatHistoryScreen
import com.noexcs.indolent.ui.theme.IndolentTheme
import com.noexcs.indolent.ui.theme.ThemeState

private sealed class HeartbeatPage {
    data object Settings : HeartbeatPage()
    data object History : HeartbeatPage()
    data object ConditionalTriggers : HeartbeatPage()
}

class HeartbeatSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSettingsActivity()
        val settingsManager = SettingsManager(applicationContext)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { finish() } })
        setContent {
            val themeKey = ThemeState.themeKey
            val dynamicColor = ThemeState.dynamicColor
            val seedColor = ThemeState.seedColor

            IndolentTheme(themeKey, dynamicColor, seedColor) {
                var page by remember { mutableStateOf<HeartbeatPage>(HeartbeatPage.Settings) }

                when (page) {
                    HeartbeatPage.Settings -> HeartbeatSettingsScreen(
                        settingsManager = settingsManager,
                        onBack = { onBackPressedDispatcher.onBackPressed() },
                        onOpenHeartbeatHistory = { page = HeartbeatPage.History },
                        onOpenConditionalTriggers = { page = HeartbeatPage.ConditionalTriggers },
                    )
                    HeartbeatPage.History -> HeartbeatHistoryScreen(
                        onBack = { page = HeartbeatPage.Settings }
                    )
                    HeartbeatPage.ConditionalTriggers -> ConditionalTriggerListScreen(
                        onBack = { page = HeartbeatPage.Settings }
                    )
                }
            }
        }
    }
}
