package com.abdownloadmanager.android.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.abdownloadmanager.android.storage.ShiroikumaUiModel
import com.abdownloadmanager.android.storage.ShiroikumaUiSettings
import com.abdownloadmanager.android.ui.configurable.comon.CommonConfigurableRenderersForAndroid
import com.abdownloadmanager.android.ui.configurable.comon.ConfigurableRenderersForAndroid
import com.abdownloadmanager.android.util.ABDMAppManager
import com.abdownloadmanager.android.util.AppInfo
import com.abdownloadmanager.android.util.ShiroikumaFonts
import com.abdownloadmanager.android.util.compose.rememberIsUiVisible
import com.abdownloadmanager.shared.repository.BaseAppRepository
import com.abdownloadmanager.shared.storage.BaseAppSettingsStorage
import com.abdownloadmanager.shared.ui.ProvideCommonSettings
import com.abdownloadmanager.shared.ui.ProvideSizeUnits
import com.abdownloadmanager.shared.ui.configurable.ConfigurableRendererRegistry
import com.abdownloadmanager.shared.ui.theme.ABDownloaderTheme
import com.abdownloadmanager.shared.ui.theme.ThemeManager
import com.abdownloadmanager.shared.ui.widget.NotificationArea
import com.abdownloadmanager.shared.ui.widget.NotificationManager
import com.abdownloadmanager.shared.ui.widget.ProvideLanguageManager
import com.abdownloadmanager.shared.ui.widget.ProvideNotificationManager
import com.abdownloadmanager.shared.util.PopUpContainer
import com.abdownloadmanager.shared.util.ResponsiveBox
import com.abdownloadmanager.shared.util.ui.ProvideDebugInfo
import ir.amirab.util.compose.IIconResolver
import ir.amirab.util.compose.localizationmanager.LanguageManager
import kotlin.collections.component1
import kotlin.collections.component2

@Composable
fun ABDownloadManagerApplicationContent(
    languageManager: LanguageManager,
    themeManager: ThemeManager,
    appSettingsStorage: BaseAppSettingsStorage,
    shiroikumaUiSettings: ShiroikumaUiSettings,
    iconResolver: IIconResolver,
    appRepository: BaseAppRepository,
    notificationManager: NotificationManager,
    abdmAppManager: ABDMAppManager,
    content: @Composable () -> Unit,
) {
    val configurableRendererRegistry = remember {
        ConfigurableRendererRegistry {
            listOf(
                CommonConfigurableRenderersForAndroid,
                ConfigurableRenderersForAndroid
            ).forEach {
                it.getAllRenderers().forEach { (key, renderer) ->
                    this.register(key, renderer)
                }
            }
        }
    }
    ProvideDebugInfo(AppInfo.isInDebugMode) {
        ProvideLanguageManager(languageManager) {
            ProvideCommonSettings(
                appSettings = appSettingsStorage,
                iconProvider = iconResolver,
                configurableRendererRegistry = configurableRendererRegistry,
            ) {
                ProvideNotificationManager(notificationManager) {
                    val myColors by themeManager.currentThemeColor.collectAsState()
                    val uiScale by appSettingsStorage.uiScale.collectAsState()
                    val shiroikumaUi by shiroikumaUiSettings.data.collectAsState()
                    val effectiveColors = remember(myColors, shiroikumaUi) {
                        shiroikumaUi.applyTo(myColors)
                    }
                    val context = LocalContext.current
                    val fontFamily = remember(shiroikumaUi.fontFile) {
                        ShiroikumaFonts.fontFamily(context, shiroikumaUi.fontFile)
                    }
                    ABDownloaderTheme(
                        myColors = effectiveColors,
                        fontFamily = fontFamily,
                        uiScale = uiScale,
                        textSizeScale = shiroikumaUi.textSizeScale,
                    ) {
                        CompositionLocalProvider(LocalShiroikumaUi provides shiroikumaUi) {
                            ResponsiveBox {
                                ProvideSizeUnits(
                                    appRepository
                                ) {
                                    PopUpContainer {
                                        content()
                                    }
                                    // placed after PopUpContainer so flash notifications
                                    // draw above dialogs, on every activity
                                    FlashNotificationArea(abdmAppManager)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// The 白い熊 UI settings model, available anywhere in the tree (list spacing etc.).
val LocalShiroikumaUi = compositionLocalOf { ShiroikumaUiModel() }

/**
 * Themed flash notifications (black surface, yellow border) rendered on every activity
 * while it is resumed; registering with [ABDMAppManager] suppresses the unstylable
 * system-toast fallback for as long as any app screen shows them itself.
 */
@Composable
private fun BoxScope.FlashNotificationArea(abdmAppManager: ABDMAppManager) {
    val isUiVisible = rememberIsUiVisible()
    DisposableEffect(isUiVisible) {
        if (isUiVisible) {
            abdmAppManager.onNotificationUiVisible()
            onDispose { abdmAppManager.onNotificationUiHidden() }
        } else {
            onDispose { }
        }
    }
    if (isUiVisible) {
        NotificationArea(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomEnd)
                .padding(bottom = 96.dp)
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
        )
    }
}
