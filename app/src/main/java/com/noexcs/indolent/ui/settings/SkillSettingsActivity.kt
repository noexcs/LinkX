package com.noexcs.indolent.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import com.noexcs.indolent.agent.skills.SkillRepository
import com.noexcs.indolent.data.SettingsManager

class SkillSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSettingsActivity()
        val settingsManager = SettingsManager(applicationContext)
        val skillRepository = SkillRepository(applicationContext, settingsManager)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { finish() } })
        setContent {
            SettingsActivityTheme {
                SkillSettingsScreen(
                    skillRepository = skillRepository,
                    settingsManager = settingsManager,
                    onBack = { onBackPressedDispatcher.onBackPressed() }
                )
            }
        }
    }
}
