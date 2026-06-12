package com.abdownloadmanager.android.util

import com.abdownloadmanager.android.storage.ShiroikumaUiSettings
import com.abdownloadmanager.shared.ui.theme.DefaultThemes
import com.abdownloadmanager.shared.ui.theme.ThemeManager
import ir.amirab.util.compose.localizationmanager.LanguageManager
import ir.amirab.util.guardedEntry
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object AndroidUi : KoinComponent {
    val themeManager: ThemeManager by inject()
    val languageManager: LanguageManager by inject()
    val shiroikumaUiSettings: ShiroikumaUiSettings by inject()
    private var booted = guardedEntry()
    fun boot() {
        booted.action {
            themeManager.boot()
            languageManager.boot()
            seedShiroikumaDefaults()
        }
    }

    // one-time switch of installs that predate the 白い熊 theme over to it
    private fun seedShiroikumaDefaults() {
        if (!shiroikumaUiSettings.defaultsSeeded.value) {
            themeManager.setTheme(DefaultThemes.shiroikuma.id)
            themeManager.setDarkTheme(DefaultThemes.shiroikuma.id)
            shiroikumaUiSettings.defaultsSeeded.value = true
        }
    }
}
