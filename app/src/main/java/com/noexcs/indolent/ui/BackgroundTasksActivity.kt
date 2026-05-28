package com.noexcs.indolent.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import com.noexcs.indolent.MainActivity
import com.noexcs.indolent.PendingExecutionData
import com.noexcs.indolent.agent.LLMMessage
import com.noexcs.indolent.task.ExecutionStatus
import com.noexcs.indolent.ui.settings.setupSettingsActivity
import com.noexcs.indolent.ui.theme.IndolentTheme
import com.noexcs.indolent.ui.theme.ThemeState

class BackgroundTasksActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSettingsActivity()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finish() }
        })

        setContent {
            IndolentTheme(
                themeKey = ThemeState.themeKey,
                dynamicColor = ThemeState.dynamicColor,
                seedColor = ThemeState.seedColor,
                contrastLevel = ThemeState.contrastLevel,
            ) {
                BackgroundTasksScreen(
                    onViewInChat = { record ->
                        val messages = if (record.status == ExecutionStatus.SUCCESS) record.result
                        else listOf(LLMMessage(role = "assistant", content = record.errorMessage))

                        PendingExecutionData.set(
                            PendingExecutionData.ExecutionData(
                                taskId = record.taskId,
                                title = record.taskTitle,
                                prompt = record.prompt,
                                messages = messages
                            )
                        )
                        startActivity(Intent(this@BackgroundTasksActivity, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        })
                        finish()
                    }
                )
            }
        }
    }
}
