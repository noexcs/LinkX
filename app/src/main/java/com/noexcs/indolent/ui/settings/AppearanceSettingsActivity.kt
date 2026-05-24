package com.noexcs.indolent.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import com.noexcs.indolent.data.SettingsManager
import com.noexcs.indolent.ui.theme.ThemeState

class AppearanceSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSettingsActivity()
        val settingsManager = SettingsManager(applicationContext)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { finish() } })
        setContent {
            SettingsActivityTheme {
                AppearanceSettingsScreen(
                    settingsManager = settingsManager,
                    onThemeKeyChanged = { ThemeState.applyTheme(it) },
                    onDynamicColorChanged = { ThemeState.dynamicColor = it },
                    onSeedColorChanged = { ThemeState.applySeedColor(it) },
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onContrastLevelChanged = { ThemeState.contrastLevel = it },
                )
            }
        }
    }
}
