package com.noexcs.indolent.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import com.noexcs.indolent.data.MemoryManager

class MemorySettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSettingsActivity()
        val memoryManager = MemoryManager(applicationContext)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { finish() } })
        setContent {
            SettingsActivityTheme {
                MemorySettingsScreen(
                    memoryManager = memoryManager,
                    onBack = { onBackPressedDispatcher.onBackPressed() }
                )
            }
        }
    }
}
