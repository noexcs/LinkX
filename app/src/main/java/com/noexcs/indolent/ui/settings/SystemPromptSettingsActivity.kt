package com.noexcs.indolent.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import com.noexcs.indolent.data.SettingsManager
import com.noexcs.indolent.prompt.SystemPromptRepository

class SystemPromptSettingsActivity : ComponentActivity() {
    private val resumeCount = mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        resumeCount.intValue++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSettingsActivity()
        val settingsManager = SettingsManager(applicationContext)
        val repository = SystemPromptRepository(applicationContext)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { finish() } })
        setContent {
            SettingsActivityTheme {
                SystemPromptSettingsScreen(
                    repository = repository,
                    settingsManager = settingsManager,
                    resumeTrigger = resumeCount.intValue,
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onEditPrompt = { promptId ->
                        val intent = Intent(this, SystemPromptEditActivity::class.java).apply {
                            if (promptId != null) putExtra("prompt_id", promptId)
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }
}
