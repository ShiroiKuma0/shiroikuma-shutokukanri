package com.abdownloadmanager.android.ui.configurable.android

import com.abdownloadmanager.android.ui.configurable.android.item.ColorConfigurable
import com.abdownloadmanager.android.ui.configurable.android.item.FontConfigurable
import com.abdownloadmanager.android.ui.configurable.android.item.PermissionConfigurable
import com.abdownloadmanager.android.ui.configurable.android.item.SliderConfigurable
import com.abdownloadmanager.shared.ui.configurable.Configurable
import com.abdownloadmanager.shared.ui.configurable.ConfigurableRenderer
import com.abdownloadmanager.shared.ui.configurable.ContainsConfigurableRenderers

data class AndroidConfigurableRenderers(
    val permissionConfigurableRenderers: ConfigurableRenderer<PermissionConfigurable>,
    val colorConfigurableRenderer: ConfigurableRenderer<ColorConfigurable>,
    val fontConfigurableRenderer: ConfigurableRenderer<FontConfigurable>,
    val sliderConfigurableRenderer: ConfigurableRenderer<SliderConfigurable>,
) : ContainsConfigurableRenderers {
    override fun getAllRenderers(): Map<Configurable.Key, ConfigurableRenderer<*>> {
        return mapOf(
            PermissionConfigurable.Key to permissionConfigurableRenderers,
            ColorConfigurable.Key to colorConfigurableRenderer,
            FontConfigurable.Key to fontConfigurableRenderer,
            SliderConfigurable.Key to sliderConfigurableRenderer,
        )
    }
}
