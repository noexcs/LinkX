package com.noexcs.indolent.ui.settings

import androidx.compose.runtime.Composable
import com.noexcs.indolent.ui.theme.IndolentTheme
import com.noexcs.indolent.ui.theme.ThemeState

@Composable
fun SettingsActivityTheme(content: @Composable () -> Unit) {
    IndolentTheme(
        themeKey = ThemeState.themeKey,
        dynamicColor = ThemeState.dynamicColor,
        seedColor = ThemeState.seedColor,
        contrastLevel = ThemeState.contrastLevel,
    ) {
        content()
    }
}
