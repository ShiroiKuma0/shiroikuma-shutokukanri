package com.abdownloadmanager.android.ui.configurable.android.item

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.abdownloadmanager.shared.ui.configurable.Configurable
import ir.amirab.util.compose.StringSource
import ir.amirab.util.compose.asStringSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * One settable UI color of the 白い熊 UI. The backing value is the override
 * (null = follow the active theme); [themeDefault] is what the theme currently
 * resolves this color to, used for the swatch and as the picker's starting point.
 */
class ColorConfigurable(
    title: StringSource,
    description: StringSource,
    backedBy: MutableStateFlow<Color?>,
    val themeDefault: StateFlow<Color>,
    enabled: StateFlow<Boolean> = DefaultEnabledValue,
    visible: StateFlow<Boolean> = DefaultVisibleValue,
) : Configurable<Color?>(
    title = title,
    description = description,
    backedBy = backedBy,
    describe = { value ->
        (value?.let { hexString(it) } ?: THEME_DEFAULT_LABEL).asStringSource()
    },
    enabled = enabled,
    visible = visible,
) {
    object Key : Configurable.Key

    override fun getKey() = Key

    companion object {
        const val THEME_DEFAULT_LABEL = "テーマ既定"

        fun hexString(color: Color): String {
            return "#%08X".format(color.toArgb())
        }
    }
}
