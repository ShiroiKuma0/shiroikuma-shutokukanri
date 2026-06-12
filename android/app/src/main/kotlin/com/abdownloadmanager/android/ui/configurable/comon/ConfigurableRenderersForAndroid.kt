package com.abdownloadmanager.android.ui.configurable.comon

import com.abdownloadmanager.android.ui.configurable.android.AndroidConfigurableRenderers
import com.abdownloadmanager.android.ui.configurable.android.renderer.ColorConfigurableRenderer
import com.abdownloadmanager.android.ui.configurable.android.renderer.FontConfigurableRenderer
import com.abdownloadmanager.android.ui.configurable.android.renderer.PermissionConfigurableRenderer
import com.abdownloadmanager.android.ui.configurable.android.renderer.SliderConfigurableRenderer

val ConfigurableRenderersForAndroid = AndroidConfigurableRenderers(
    permissionConfigurableRenderers = PermissionConfigurableRenderer,
    colorConfigurableRenderer = ColorConfigurableRenderer,
    fontConfigurableRenderer = FontConfigurableRenderer,
    sliderConfigurableRenderer = SliderConfigurableRenderer,
)
