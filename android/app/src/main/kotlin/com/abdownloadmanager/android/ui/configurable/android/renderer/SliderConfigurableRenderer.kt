package com.abdownloadmanager.android.ui.configurable.android.renderer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import com.abdownloadmanager.android.ui.configurable.ConfigTemplate
import com.abdownloadmanager.android.ui.configurable.TitleAndDescription
import com.abdownloadmanager.android.ui.configurable.android.item.SliderConfigurable
import com.abdownloadmanager.shared.ui.configurable.ConfigurableRenderer
import com.abdownloadmanager.shared.ui.configurable.ConfigurableUiProps
import com.abdownloadmanager.shared.ui.widget.Text
import com.abdownloadmanager.shared.util.div
import com.abdownloadmanager.shared.util.ui.myColors
import com.abdownloadmanager.shared.util.ui.theme.myShapes
import com.abdownloadmanager.shared.util.ui.theme.myTextSizes
import kotlin.math.roundToInt

object SliderConfigurableRenderer : ConfigurableRenderer<SliderConfigurable> {
    @Composable
    override fun RenderConfigurable(
        configurable: SliderConfigurable,
        configurableUiProps: ConfigurableUiProps,
    ) {
        val value by configurable.stateFlow.collectAsState()
        ConfigTemplate(
            modifier = configurableUiProps.modifier
                .padding(configurableUiProps.itemPaddingValues),
            title = {
                Column {
                    TitleAndDescription(configurable, true)
                }
            },
            value = {},
            nestedContent = {
                ValueSlider(
                    value = value,
                    min = configurable.min,
                    max = configurable.max,
                    onChange = { configurable.set(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .padding(bottom = 8.dp),
                )
            }
        )
    }
}

@Composable
private fun ValueSlider(
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val range = (max - min).coerceAtLeast(1)
    fun positionToValue(x: Float, width: Int): Int {
        if (width <= 0) return min
        return (min + (x / width) * range).roundToInt().coerceIn(min, max)
    }
    BoxWithConstraints(
        modifier
            .height(28.dp)
            .pointerInput(min, max) {
                detectTapGestures { offset ->
                    onChange(positionToValue(offset.x, size.width))
                }
            }
            .pointerInput(min, max) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onChange(positionToValue(change.position.x, size.width))
                }
            }
    ) {
        val maxWidthPx = constraints.maxWidth
        val fraction = (value - min).toFloat() / range
        val thumbSizePx = with(LocalDensity.current) { 16.dp.roundToPx() }
        val thumbX = ((fraction * (maxWidthPx - thumbSizePx)).roundToInt()).coerceAtLeast(0)
        // track
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(4.dp)
                .clip(myShapes.defaultRounded)
                .background(myColors.onSurface / 0.2f)
        )
        // filled part
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(fraction)
                .height(4.dp)
                .clip(myShapes.defaultRounded)
                .background(myColors.primary)
        )
        // thumb
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset(thumbX, 0) }
                .size(16.dp)
                .clip(CircleShape)
                .background(myColors.primary)
        )
    }
}
