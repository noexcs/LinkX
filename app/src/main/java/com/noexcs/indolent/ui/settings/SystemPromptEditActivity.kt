package com.noexcs.indolent.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.noexcs.indolent.prompt.SystemPromptRepository

class SystemPromptEditActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSettingsActivity()
        val promptId = intent.getStringExtra("prompt_id")
        val repository = SystemPromptRepository(applicationContext)
        setContent {
            SettingsActivityTheme {
                SystemPromptEditScreen(
                    promptId = promptId,
                    repository = repository,
                    onBack = { finish() }
                )
            }
        }
    }
}
