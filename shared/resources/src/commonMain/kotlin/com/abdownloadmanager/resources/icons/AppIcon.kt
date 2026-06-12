package com.abdownloadmanager.resources.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private val Yellow = Color(0xFFFFFF00)

val ABDMIcons.AppIcon: ImageVector
    get() {
        if (_AppIcon != null) {
            return _AppIcon!!
        }
        _AppIcon = ImageVector.Builder(
            name = "AppIcon",
            defaultWidth = 48.dp,
            defaultHeight = 48.dp,
            viewportWidth = 560f,
            viewportHeight = 560f
        ).apply {
            // Glyph paths are authored in a 512x512 box; the 24f offset centers
            // them so the 22f stroke stays inside the viewport.
            group(translationX = 24f, translationY = 24f) {
                path(
                    fill = SolidColor(Color.Black),
                    stroke = SolidColor(Yellow),
                    strokeLineWidth = 22f,
                    strokeLineJoin = StrokeJoin.Round,
                    strokeLineCap = StrokeCap.Round
                ) {
                    moveTo(76.81f, 256f)
                    curveTo(104.57f, 256f, 126.2f, 279.34f, 136.82f, 304.98f)
                    curveTo(143.26f, 320.51f, 152.68f, 334.62f, 164.57f, 346.51f)
                    curveTo(176.46f, 358.4f, 190.57f, 367.82f, 206.1f, 374.26f)
                    curveTo(221.63f, 380.69f, 238.27f, 384f, 255.08f, 384f)
                    curveTo(271.89f, 384f, 288.53f, 380.69f, 304.06f, 374.26f)
                    curveTo(319.59f, 367.82f, 333.7f, 358.4f, 345.59f, 346.51f)
                    curveTo(357.48f, 334.62f, 366.9f, 320.51f, 373.34f, 304.98f)
                    curveTo(383.96f, 279.34f, 405.58f, 256f, 433.34f, 256f)
                    horizontalLineTo(512f)
                    curveTo(512f, 289.62f, 505.38f, 322.91f, 492.51f, 353.97f)
                    curveTo(479.65f, 385.03f, 460.79f, 413.25f, 437.02f, 437.02f)
                    curveTo(413.25f, 460.79f, 385.03f, 479.65f, 353.97f, 492.51f)
                    curveTo(322.91f, 505.38f, 289.62f, 512f, 256f, 512f)
                    curveTo(222.38f, 512f, 189.09f, 505.38f, 158.03f, 492.51f)
                    curveTo(126.97f, 479.65f, 98.75f, 460.79f, 74.98f, 437.02f)
                    curveTo(51.21f, 413.25f, 32.35f, 385.03f, 19.49f, 353.97f)
                    curveTo(6.62f, 322.91f, 0f, 289.62f, 0f, 256f)
                    horizontalLineTo(76.81f)
                    close()
                }
                path(
                    fill = SolidColor(Color.Black),
                    stroke = SolidColor(Yellow),
                    strokeLineWidth = 22f,
                    strokeLineJoin = StrokeJoin.Round,
                    strokeLineCap = StrokeCap.Round
                ) {
                    moveTo(206f, 10f)
                    horizontalLineTo(306f)
                    verticalLineTo(195f)
                    horizontalLineTo(366f)
                    lineTo(256f, 310f)
                    lineTo(146f, 195f)
                    horizontalLineTo(206f)
                    close()
                }
                path(fill = SolidColor(Yellow)) {
                    moveTo(50.44f, 139.02f)
                    curveTo(53.65f, 130.7f, 62.88f, 126.61f, 71.04f, 129.89f)
                    curveTo(79.2f, 133.18f, 83.2f, 142.59f, 79.99f, 150.91f)
                    lineTo(69.06f, 179.18f)
                    curveTo(65.84f, 187.5f, 56.62f, 191.59f, 48.46f, 188.31f)
                    curveTo(40.3f, 185.02f, 36.29f, 175.61f, 39.51f, 167.29f)
                    lineTo(50.44f, 139.02f)
                    close()
                    moveTo(443.52f, 129.89f)
                    curveTo(451.68f, 126.61f, 460.91f, 130.7f, 464.12f, 139.02f)
                    lineTo(475.05f, 167.29f)
                    curveTo(478.27f, 175.61f, 474.26f, 185.02f, 466.1f, 188.31f)
                    curveTo(457.94f, 191.59f, 448.72f, 187.5f, 445.5f, 179.18f)
                    lineTo(434.57f, 150.91f)
                    curveTo(431.36f, 142.59f, 435.36f, 133.18f, 443.52f, 129.89f)
                    close()
                    moveTo(148.94f, 36.76f)
                    curveTo(155.83f, 31.22f, 165.82f, 32.42f, 171.25f, 39.45f)
                    curveTo(176.68f, 46.47f, 175.51f, 56.66f, 168.62f, 62.2f)
                    lineTo(145.24f, 81.03f)
                    curveTo(138.36f, 86.57f, 128.37f, 85.37f, 122.94f, 78.35f)
                    curveTo(117.5f, 71.32f, 118.68f, 61.13f, 125.57f, 55.59f)
                    lineTo(148.94f, 36.76f)
                    close()
                    moveTo(343.31f, 39.45f)
                    curveTo(348.74f, 32.42f, 358.73f, 31.22f, 365.62f, 36.76f)
                    lineTo(388.99f, 55.59f)
                    curveTo(395.88f, 61.13f, 397.06f, 71.32f, 391.62f, 78.35f)
                    curveTo(386.19f, 85.37f, 376.2f, 86.57f, 369.31f, 81.03f)
                    lineTo(345.94f, 62.2f)
                    curveTo(339.05f, 56.66f, 337.88f, 46.47f, 343.31f, 39.45f)
                    close()
                }
            }
        }.build()

        return _AppIcon!!
    }

@Suppress("ObjectPropertyName")
private var _AppIcon: ImageVector? = null
