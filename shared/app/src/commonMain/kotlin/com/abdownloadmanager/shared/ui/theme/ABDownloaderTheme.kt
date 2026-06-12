package com.abdownloadmanager.shared.ui.theme

import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import com.abdownloadmanager.shared.util.ui.*
import com.abdownloadmanager.shared.util.ui.theme.*
import com.abdownloadmanager.shared.util.ui.theme.UiScaledContent


@Composable
fun ABDownloaderTheme(
    myColors: MyColors,
    fontFamily: FontFamily? = null,
    uiScale: Float = DEFAULT_UI_SCALE,
    textSizeScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val systemDensity = LocalDensity.current
    val textSizes = myPlatformTextSizes().scaledBy(textSizeScale)
    CompositionLocalProvider(
        LocalMyColors provides animatedColors(myColors),
        LocalUiScale provides uiScale,
        LocalSystemDensity provides systemDensity,
        LocalMyShapes provides myPlatformShapes(),
        LocalSpacing provides myPlatformSpacing(),
    ) {
        CompositionLocalProvider(
            LocalMultiplatformScrollbarStyle provides myPlatformScrollbarStyle(),
            LocalIndication provides ripple(),
            LocalContentColor provides myColors.onBackground,
            LocalContentAlpha provides 1f,
            LocalTextSizes provides textSizes,
            LocalTextStyle provides LocalTextStyle.current.copy(
                lineHeight = TextUnit.Unspecified,
                fontSize = textSizes.base,
                fontFamily = fontFamily,
            ),
        ) {
            PlatformDependentProviders {
                // it is overridden by [Window] Composable,
                // but I put this here. maybe I need this outside of window  scope!
                UiScaledContent {
                    content()
                }
            }

        }
    }
}

private fun TextSizes.scaledBy(factor: Float): TextSizes {
    if (factor == 1f) return this
    return TextSizes(
        xs = xs * factor,
        sm = sm * factor,
        base = base * factor,
        lg = lg * factor,
        xl = xl * factor,
        x2l = x2l * factor,
        x3l = x3l * factor,
        x4l = x4l * factor,
        x5l = x5l * factor,
    )
}

