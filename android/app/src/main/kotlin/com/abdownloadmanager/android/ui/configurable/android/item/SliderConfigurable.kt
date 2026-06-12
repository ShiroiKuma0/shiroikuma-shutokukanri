package com.abdownloadmanager.android.ui.configurable.android.item

import com.abdownloadmanager.shared.ui.configurable.Configurable
import ir.amirab.util.compose.StringSource
import ir.amirab.util.compose.asStringSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** An Int setting picked with an inline slider over [min]..[max] (applied live while dragging). */
class SliderConfigurable(
    title: StringSource,
    description: StringSource,
    backedBy: MutableStateFlow<Int>,
    val min: Int,
    val max: Int,
    describe: (Int) -> StringSource = { it.toString().asStringSource() },
    enabled: StateFlow<Boolean> = DefaultEnabledValue,
    visible: StateFlow<Boolean> = DefaultVisibleValue,
) : Configurable<Int>(
    title = title,
    description = description,
    backedBy = backedBy,
    validate = { it in min..max },
    describe = describe,
    enabled = enabled,
    visible = visible,
) {
    object Key : Configurable.Key

    override fun getKey() = Key
}
