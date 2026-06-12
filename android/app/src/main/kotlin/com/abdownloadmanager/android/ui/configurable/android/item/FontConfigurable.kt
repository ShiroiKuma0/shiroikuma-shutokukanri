package com.abdownloadmanager.android.ui.configurable.android.item

import com.abdownloadmanager.android.util.ShiroikumaFonts
import com.abdownloadmanager.shared.ui.configurable.Configurable
import ir.amirab.util.compose.StringSource
import ir.amirab.util.compose.asStringSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The app-wide font of the 白い熊 UI. The backing value is a font filename in the
 * app's fonts dir ("" = system default, [ShiroikumaFonts.MONOSPACE] = built-in
 * monospace); new fonts are imported from the system file picker.
 */
class FontConfigurable(
    title: StringSource,
    description: StringSource,
    backedBy: MutableStateFlow<String>,
    enabled: StateFlow<Boolean> = DefaultEnabledValue,
    visible: StateFlow<Boolean> = DefaultVisibleValue,
) : Configurable<String>(
    title = title,
    description = description,
    backedBy = backedBy,
    describe = { ShiroikumaFonts.displayName(it).asStringSource() },
    enabled = enabled,
    visible = visible,
) {
    object Key : Configurable.Key

    override fun getKey() = Key
}
