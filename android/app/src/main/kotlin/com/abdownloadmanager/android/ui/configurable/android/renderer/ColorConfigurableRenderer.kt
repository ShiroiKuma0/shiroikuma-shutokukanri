package com.abdownloadmanager.android.ui.configurable.android.renderer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.abdownloadmanager.android.ui.configurable.ConfigTemplate
import com.abdownloadmanager.android.ui.configurable.ConfigurableSheet
import com.abdownloadmanager.android.ui.configurable.TitleAndDescription
import com.abdownloadmanager.android.ui.configurable.android.item.ColorConfigurable
import com.abdownloadmanager.resources.Res
import com.abdownloadmanager.shared.ui.configurable.ConfigurableRenderer
import com.abdownloadmanager.shared.ui.configurable.ConfigurableUiProps
import com.abdownloadmanager.shared.ui.widget.MyTextField
import com.abdownloadmanager.shared.ui.widget.Text
import com.abdownloadmanager.shared.ui.widget.TransparentIconActionButton
import com.abdownloadmanager.shared.util.div
import com.abdownloadmanager.shared.util.ui.icon.MyIcons
import com.abdownloadmanager.shared.util.ui.myColors
import com.abdownloadmanager.shared.util.ui.theme.myShapes
import com.abdownloadmanager.shared.util.ui.theme.myTextSizes
import ir.amirab.util.compose.asStringSource
import kotlin.math.roundToInt

object ColorConfigurableRenderer : ConfigurableRenderer<ColorConfigurable> {
    @Composable
    override fun RenderConfigurable(
        configurable: ColorConfigurable,
        configurableUiProps: ConfigurableUiProps,
    ) {
        val override by configurable.stateFlow.collectAsState()
        val themeDefault by configurable.themeDefault.collectAsState()
        val effective = override ?: themeDefault
        var isOpened by remember { mutableStateOf(false) }
        ConfigTemplate(
            modifier = configurableUiProps.modifier
                .clickable { isOpened = true }
                .padding(configurableUiProps.itemPaddingValues),
            title = {
                Column {
                    TitleAndDescription(configurable, true)
                }
            },
            value = {
                ColorSwatch(effective, 24.dp)
            }
        )
        ConfigurableSheet(
            title = configurable.title,
            isOpened = isOpened,
            onDismiss = { isOpened = false },
            headerActions = {
                TransparentIconActionButton(
                    MyIcons.close,
                    contentDescription = Res.string.close.asStringSource(),
                    onClick = { isOpened = false }
                )
            }
        ) {
            ColorPickerContent(
                initial = effective,
                onPick = { configurable.set(it) },
                onReset = {
                    configurable.set(null)
                    isOpened = false
                },
            )
        }
    }
}

@Composable
private fun ColorSwatch(color: Color, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, myColors.onSurface / 0.3f, CircleShape)
    )
}

private val PALETTE = listOf(
    0xFFFFFF00, 0xFFFFC107, 0xFFFF9800, 0xFFFF5722, 0xFFF44336, 0xFFE91E63,
    0xFF9C27B0, 0xFF673AB7, 0xFF3F51B5, 0xFF2196F3, 0xFF00BCD4, 0xFF009688,
    0xFF4CAF50, 0xFF8BC34A, 0xFFCDDC39, 0xFF795548, 0xFF607D8B, 0xFF9E9E9E,
    0xFFFFFFFF, 0xFFBDBDBD, 0xFF757575, 0xFF424242, 0xFF212121, 0xFF000000,
).map { Color(it) }

@Composable
private fun ColorPickerContent(
    initial: Color,
    onPick: (Color) -> Unit,
    onReset: () -> Unit,
) {
    var current by remember { mutableStateOf(initial) }
    var hexText by remember { mutableStateOf(ColorConfigurable.hexString(initial)) }

    fun update(color: Color, fromHexField: Boolean = false) {
        current = color
        if (!fromHexField) {
            hexText = ColorConfigurable.hexString(color)
        }
        onPick(color)
    }

    val scrollState = rememberScrollState()
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // live preview + hex input
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(myShapes.defaultRounded)
                    .background(current)
                    .border(1.dp, myColors.onSurface / 0.3f, myShapes.defaultRounded)
            )
            Spacer(Modifier.width(12.dp))
            MyTextField(
                text = hexText,
                onTextChange = { newText ->
                    hexText = newText
                    parseHexColor(newText)?.let { update(it, fromHexField = true) }
                },
                placeholder = "#AARRGGBB",
                modifier = Modifier.weight(1f),
            )
        }
        // RGB channel sliders
        val argb = current.toArgb()
        val red = (argb shr 16) and 0xFF
        val green = (argb shr 8) and 0xFF
        val blue = argb and 0xFF
        ChannelSlider(
            label = "R",
            value = red,
            brush = Brush.horizontalGradient(
                listOf(Color(0, green, blue), Color(255, green, blue))
            ),
            onChange = { update(Color(it, green, blue)) },
        )
        ChannelSlider(
            label = "G",
            value = green,
            brush = Brush.horizontalGradient(
                listOf(Color(red, 0, blue), Color(red, 255, blue))
            ),
            onChange = { update(Color(red, it, blue)) },
        )
        ChannelSlider(
            label = "B",
            value = blue,
            brush = Brush.horizontalGradient(
                listOf(Color(red, green, 0), Color(red, green, 255))
            ),
            onChange = { update(Color(red, green, it)) },
        )
        // preset palette
        PALETTE.chunked(6).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { color ->
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (color == current) 2.dp else 1.dp,
                                color = if (color == current) {
                                    myColors.primary
                                } else {
                                    myColors.onSurface / 0.3f
                                },
                                shape = CircleShape,
                            )
                            .clickable { update(color) }
                    )
                }
            }
        }
        // back to the theme's own value
        Text(
            ColorConfigurable.THEME_DEFAULT_LABEL + "に戻す",
            fontSize = myTextSizes.base,
            color = myColors.primary,
            modifier = Modifier
                .clip(myShapes.defaultRounded)
                .clickable { onReset() }
                .padding(8.dp),
        )
        Spacer(Modifier.height(8.dp))
    }
}

private fun parseHexColor(text: String): Color? {
    val hex = text.trim().removePrefix("#")
    if (hex.length != 6 && hex.length != 8) {
        return null
    }
    val parsed = hex.toULongOrNull(16) ?: return null
    val argb = if (hex.length == 6) {
        parsed or 0xFF000000UL
    } else {
        parsed
    }
    return Color(argb.toLong())
}

@Composable
private fun ChannelSlider(
    label: String,
    value: Int,
    brush: Brush,
    onChange: (Int) -> Unit,
) {
    fun positionToValue(x: Float, width: Int): Int {
        if (width <= 0) return 0
        return ((x / width) * 255f).roundToInt().coerceIn(0, 255)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            fontSize = myTextSizes.base,
            modifier = Modifier.width(20.dp),
        )
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .height(28.dp)
                .clip(myShapes.defaultRounded)
                .background(brush)
                .border(1.dp, myColors.onSurface / 0.2f, myShapes.defaultRounded)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onChange(positionToValue(offset.x, size.width))
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        onChange(positionToValue(change.position.x, size.width))
                    }
                }
        ) {
            val maxWidthPx = constraints.maxWidth
            val thumbX = ((value / 255f) * maxWidthPx).roundToInt()
                .coerceIn(0, (maxWidthPx - 3).coerceAtLeast(0))
            Box(
                Modifier
                    .offset { IntOffset(thumbX, 0) }
                    .fillMaxHeight()
                    .width(3.dp)
                    .background(myColors.onBackground)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            value.toString(),
            fontSize = myTextSizes.base,
            modifier = Modifier.width(32.dp),
        )
    }
}
