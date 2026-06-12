package com.abdownloadmanager.android.storage

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.core.DataStore
import arrow.optics.Lens
import com.abdownloadmanager.shared.util.ConfigBaseSettingsByJson
import com.abdownloadmanager.shared.util.ui.MyColors
import kotlinx.serialization.Serializable

/**
 * 白い熊 取得管理 UI settings: per-attribute color overrides layered on top of the active
 * theme (null = follow the theme), plus the app-wide font and text-size scale.
 * Everything here is edited live from the 白い熊 取得管理 UI page.
 */
@Serializable
data class ShiroikumaUiModel(
    // one-time switch of existing installs over to the 白い熊 theme
    val defaultsSeeded: Boolean = false,
    // "" = system default, "@monospace" = built-in monospace, otherwise a file in the fonts dir
    val fontFile: String = "",
    val textSizeScale: Float = 1f,
    // vertical gap between download-list items on the main view, in dp (0 = items touch)
    val listItemSpacing: Int = 0,
    // color overrides (ARGB stored as Long; null = follow the theme)
    val background: Long? = null,
    val onBackground: Long? = null,
    val surface: Long? = null,
    val onSurface: Long? = null,
    val primary: Long? = null,
    val onPrimary: Long? = null,
    val secondary: Long? = null,
    val onSecondary: Long? = null,
    val success: Long? = null,
    val error: Long? = null,
    val warning: Long? = null,
    val info: Long? = null,
) {
    companion object {
        fun default() = ShiroikumaUiModel()
    }

    /** The active theme with every set override applied. */
    fun applyTo(base: MyColors): MyColors {
        fun c(override: Long?, fallback: Color) = override?.let { Color(it) } ?: fallback
        return base.copy(
            background = c(background, base.background),
            onBackground = c(onBackground, base.onBackground),
            surface = c(surface, base.surface),
            onSurface = c(onSurface, base.onSurface),
            primary = c(primary, base.primary),
            // variants follow an overridden base color so gradients stay coherent
            primaryVariant = c(primary, base.primaryVariant),
            onPrimary = c(onPrimary, base.onPrimary),
            secondary = c(secondary, base.secondary),
            secondaryVariant = c(secondary, base.secondaryVariant),
            onSecondary = c(onSecondary, base.onSecondary),
            success = c(success, base.success),
            error = c(error, base.error),
            warning = c(warning, base.warning),
            info = c(info, base.info),
        )
    }
}

private fun <T> lensOf(
    get: (ShiroikumaUiModel) -> T,
    set: (ShiroikumaUiModel, T) -> ShiroikumaUiModel,
): Lens<ShiroikumaUiModel, T> = Lens(get, set)

private fun colorLens(
    get: (ShiroikumaUiModel) -> Long?,
    set: (ShiroikumaUiModel, Long?) -> ShiroikumaUiModel,
): Lens<ShiroikumaUiModel, Color?> = Lens(
    get = { model -> get(model)?.let { Color(it) } },
    set = { model, color -> set(model, color?.toArgb()?.toUInt()?.toLong()) },
)

class ShiroikumaUiSettings(
    dataStore: DataStore<ShiroikumaUiModel>,
) : ConfigBaseSettingsByJson<ShiroikumaUiModel>(dataStore) {
    val defaultsSeeded = from(lensOf({ it.defaultsSeeded }, { s, v -> s.copy(defaultsSeeded = v) }))
    val fontFile = from(lensOf({ it.fontFile }, { s, v -> s.copy(fontFile = v) }))
    val textSizeScale = from(lensOf({ it.textSizeScale }, { s, v -> s.copy(textSizeScale = v) }))
    val listItemSpacing = from(lensOf({ it.listItemSpacing }, { s, v -> s.copy(listItemSpacing = v) }))

    val background = from(colorLens({ it.background }, { s, v -> s.copy(background = v) }))
    val onBackground = from(colorLens({ it.onBackground }, { s, v -> s.copy(onBackground = v) }))
    val surface = from(colorLens({ it.surface }, { s, v -> s.copy(surface = v) }))
    val onSurface = from(colorLens({ it.onSurface }, { s, v -> s.copy(onSurface = v) }))
    val primary = from(colorLens({ it.primary }, { s, v -> s.copy(primary = v) }))
    val onPrimary = from(colorLens({ it.onPrimary }, { s, v -> s.copy(onPrimary = v) }))
    val secondary = from(colorLens({ it.secondary }, { s, v -> s.copy(secondary = v) }))
    val onSecondary = from(colorLens({ it.onSecondary }, { s, v -> s.copy(onSecondary = v) }))
    val success = from(colorLens({ it.success }, { s, v -> s.copy(success = v) }))
    val error = from(colorLens({ it.error }, { s, v -> s.copy(error = v) }))
    val warning = from(colorLens({ it.warning }, { s, v -> s.copy(warning = v) }))
    val info = from(colorLens({ it.info }, { s, v -> s.copy(info = v) }))
}
