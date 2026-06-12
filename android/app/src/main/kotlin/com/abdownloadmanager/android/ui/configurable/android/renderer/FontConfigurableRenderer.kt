package com.abdownloadmanager.android.ui.configurable.android.renderer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.abdownloadmanager.android.ui.configurable.ConfigTemplate
import com.abdownloadmanager.android.ui.configurable.ConfigurableSheet
import com.abdownloadmanager.android.ui.configurable.NextIcon
import com.abdownloadmanager.android.ui.configurable.TitleAndDescription
import com.abdownloadmanager.android.ui.configurable.android.item.FontConfigurable
import com.abdownloadmanager.android.util.ShiroikumaFonts
import com.abdownloadmanager.resources.Res
import com.abdownloadmanager.shared.ui.configurable.ConfigurableRenderer
import com.abdownloadmanager.shared.ui.configurable.ConfigurableUiProps
import com.abdownloadmanager.shared.ui.widget.Text
import com.abdownloadmanager.shared.ui.widget.TransparentIconActionButton
import com.abdownloadmanager.shared.util.div
import com.abdownloadmanager.shared.util.ui.WithContentAlpha
import com.abdownloadmanager.shared.util.ui.icon.MyIcons
import com.abdownloadmanager.shared.util.ui.myColors
import com.abdownloadmanager.shared.util.ui.theme.myTextSizes
import com.abdownloadmanager.shared.util.ui.widget.MyIcon
import ir.amirab.util.compose.asStringSource

private const val FONT_SAMPLE_TEXT = "AaIiMmOoQqWw 012 白い熊相撲道"

object FontConfigurableRenderer : ConfigurableRenderer<FontConfigurable> {
    @Composable
    override fun RenderConfigurable(
        configurable: FontConfigurable,
        configurableUiProps: ConfigurableUiProps,
    ) {
        val context = LocalContext.current
        val value by configurable.stateFlow.collectAsState()
        var isOpened by remember { mutableStateOf(false) }
        // bumped after an import so the option list is re-read from disk
        var fontsVersion by remember { mutableIntStateOf(0) }
        val fontOptions = remember(fontsVersion) {
            ShiroikumaFonts.availableFonts(context)
        }
        val importLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                val imported = ShiroikumaFonts.import(context, uri)
                if (imported != null) {
                    fontsVersion++
                    configurable.set(imported)
                }
            }
        }
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
                NextIcon()
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
            val scrollState = rememberScrollState()
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                for (option in fontOptions) {
                    val selected = option.fileName == value
                    // each font's name and sample drawn in its own glyphs
                    val family = ShiroikumaFonts.fontFamily(context, option.fileName)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                configurable.set(option.fileName)
                                isOpened = false
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                option.displayName,
                                fontSize = myTextSizes.lg,
                                fontFamily = family,
                            )
                            WithContentAlpha(0.75f) {
                                Text(
                                    FONT_SAMPLE_TEXT,
                                    fontSize = myTextSizes.base,
                                    fontFamily = family,
                                )
                            }
                        }
                        if (selected) {
                            MyIcon(
                                MyIcons.check, null,
                                Modifier
                                    .padding(4.dp)
                                    .size(16.dp)
                            )
                        }
                    }
                    FontRowDivider()
                }
                Text(
                    "フォントを追加…",
                    fontSize = myTextSizes.lg,
                    color = myColors.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            importLauncher.launch(arrayOf("*/*"))
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun FontRowDivider() {
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(myColors.onSurface / 0.1f)
    )
}
