package com.noexcs.indolent.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.noexcs.indolent.data.SettingsManager
import com.noexcs.indolent.ui.SettingsScreen

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSettingsActivity()
        val settingsManager = SettingsManager(applicationContext)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finish() }
        })

        setContent {
            SettingsActivityTheme {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    SettingsScreen(
                    settingsManager = settingsManager,
                    scrollState = rememberScrollState(),
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onNavigateToApiSettings = { startActivity(Intent(this@SettingsActivity, ApiSettingsActivity::class.java)) },
                    onNavigateToSystemPromptSettings = { startActivity(Intent(this@SettingsActivity, SystemPromptSettingsActivity::class.java)) },
                    onNavigateToMemorySettings = { startActivity(Intent(this@SettingsActivity, MemorySettingsActivity::class.java)) },
                    onNavigateToToolSettings = { startActivity(Intent(this@SettingsActivity, ToolSettingsActivity::class.java)) },
                    onNavigateToHeartbeatSettings = { startActivity(Intent(this@SettingsActivity, HeartbeatSettingsActivity::class.java)) },
                    onNavigateToUsageStats = { startActivity(Intent(this@SettingsActivity, UsageStatsActivity::class.java)) },
                    onNavigateToAppearance = { startActivity(Intent(this@SettingsActivity, AppearanceSettingsActivity::class.java)) },
                    onNavigateToAbout = { startActivity(Intent(this@SettingsActivity, AboutActivity::class.java)) },
                    onNavigateToSkillSettings = { startActivity(Intent(this@SettingsActivity, SkillSettingsActivity::class.java)) },
                )
                }
            }
        }
    }
}
