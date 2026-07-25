package com.abdownloadmanager.android.pages.shiroikumaui

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abdownloadmanager.android.ui.page.FooterFade
import com.abdownloadmanager.android.ui.page.PageHeader
import com.abdownloadmanager.android.ui.page.PageTitle
import com.abdownloadmanager.android.ui.page.PageUi
import com.abdownloadmanager.android.ui.page.createAlphaForHeader
import com.abdownloadmanager.resources.Res
import com.abdownloadmanager.shared.ui.configurable.ConfigurableUiProps
import com.abdownloadmanager.shared.ui.configurable.RenderConfigurable
import com.abdownloadmanager.shared.ui.widget.Text
import com.abdownloadmanager.shared.ui.widget.TransparentIconActionButton
import com.abdownloadmanager.shared.util.div
import com.abdownloadmanager.shared.util.ui.VerticalScrollableContent
import com.abdownloadmanager.shared.util.ui.icon.MyIcons
import com.abdownloadmanager.shared.util.ui.myColors
import com.abdownloadmanager.shared.util.ui.theme.myTextSizes
import ir.amirab.util.compose.asStringSource

// kxkb indent ladder: headings start at 36dp and every tier (section > subgroup > items)
// steps 18dp further in
private val INDENT_BASE = 36.dp
private val INDENT_STEP = 18.dp

@Composable
fun ShiroikumaUiPage(
    component: ShiroikumaUiComponent,
) {
    val scrollState = rememberScrollState()
    var pageContentPaddingValues by remember {
        mutableStateOf(PaddingValues())
    }
    val topPadding = pageContentPaddingValues.calculateTopPadding()
    val bottomPadding = pageContentPaddingValues.calculateBottomPadding()
    val density = LocalDensity.current
    PageUi(
        header = {
            val backDispatcher = LocalOnBackPressedDispatcherOwner.current
            PageHeader(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        myColors.background.copy(
                            createAlphaForHeader(
                                scrollState.value.toFloat(),
                                density.run { topPadding.toPx() },
                            ) * 0.75f
                        )
                    )
                    .statusBarsPadding(),
                leadingIcon = {
                    TransparentIconActionButton(
                        icon = MyIcons.back,
                        contentDescription = Res.string.back.asStringSource(),
                        onClick = {
                            backDispatcher?.onBackPressedDispatcher?.onBackPressed()
                        }
                    )
                },
                headerTitle = {
                    PageTitle("白い熊 取得管理 UI")
                },
            )
        },
        footer = {
            Spacer(Modifier.navigationBarsPadding())
        }
    ) { params ->
        pageContentPaddingValues = params.paddingValues
        Box {
            VerticalScrollableContent(
                scrollState,
                Modifier.fillMaxSize()
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp)
                        .padding(horizontal = 8.dp),
                ) {
                    Spacer(Modifier.height(topPadding))
                    var seenFirstSection = false
                    for (entry in component.entries) {
                        when (entry) {
                            is ShiroikumaUiComponent.Entry.Section -> {
                                SectionHeader(
                                    entry.title,
                                    entry.level,
                                    isFirst = !seenFirstSection,
                                )
                                if (entry.level == 0) seenFirstSection = true
                            }

                            is ShiroikumaUiComponent.Entry.Action -> {
                                ActionRow(entry)
                            }

                            is ShiroikumaUiComponent.Entry.Item -> {
                                RenderConfigurable(
                                    cfg = entry.configurable,
                                    configurableUiProps = ConfigurableUiProps(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = INDENT_BASE + INDENT_STEP * (entry.level + 1)),
                                        itemPaddingValues = PaddingValues(
                                            vertical = 8.dp,
                                            horizontal = 8.dp,
                                        ),
                                    ),
                                )
                            }
                        }
                    }
                }
            }
            FooterFade(bottomPadding)
        }
    }

    val showPanel by component.showExportImportPanel.collectAsState()
    if (showPanel) {
        ExportImportSheet(component)
    }
    val infoDialog by component.infoDialog.collectAsState()
    infoDialog?.let {
        ExportImportInfoDialog(it, component)
    }
    val confirmDialog by component.confirmDialog.collectAsState()
    confirmDialog?.let {
        ShiroikumaConfirmDialog(it, onDismiss = component::dismissConfirmDialog)
    }
}

/**
 * A tappable row (title + description + live status), e.g. the Export/Import entry —
 * with an optional secondary action pinned to the right (e.g. トークン → 再生成).
 */
@Composable
private fun ActionRow(entry: ShiroikumaUiComponent.Entry.Action) {
    val status by entry.status.collectAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = INDENT_BASE + INDENT_STEP * (entry.level + 1)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .clickable(onClick = entry.onClick)
                .padding(vertical = 8.dp, horizontal = 8.dp)
        ) {
            Text(
                entry.title,
                fontSize = myTextSizes.lg,
                color = myColors.onBackground,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                entry.description,
                fontSize = myTextSizes.sm,
                color = myColors.onBackground / 0.75f,
            )
            Text(
                status.first,
                fontSize = myTextSizes.sm,
                color = if (status.second) myColors.error else myColors.onBackground / 0.75f,
            )
        }
        val trailingLabel = entry.trailingLabel
        val onTrailingClick = entry.onTrailingClick
        if (trailingLabel != null && onTrailingClick != null) {
            Box(
                Modifier
                    .padding(end = 8.dp)
                    .clip(RoundedCornerShape(50))
                    .border(1.5.dp, myColors.primary, RoundedCornerShape(50))
                    .clickable(onClick = onTrailingClick)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    trailingLabel,
                    fontSize = myTextSizes.sm,
                    fontWeight = FontWeight.Bold,
                    color = myColors.primary,
                )
            }
        }
    }
}

// kxkb heading format: bold yellow title underlined only as wide as the text itself,
// top-level sections separated from the previous one by a full-width hairline.
@Composable
private fun SectionHeader(title: String, level: Int, isFirst: Boolean) {
    val isTopLevel = level == 0
    val hairline = with(LocalDensity.current) { 1.toDp() }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = if (isFirst) 12.dp else 10.dp, bottom = 2.dp)
    ) {
        if (isTopLevel && !isFirst) {
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(hairline)
                    .background(myColors.primary)
            )
        }
        // IntrinsicSize.Max = the text's full single-line width (Min collapses CJK
        // text — no word breaks — to a single glyph)
        Column(
            Modifier
                .width(IntrinsicSize.Max)
                .padding(
                    start = INDENT_BASE + INDENT_STEP * level,
                    top = if (isTopLevel) 8.dp else 0.dp,
                )
        ) {
            Text(
                title,
                fontSize = if (isTopLevel) myTextSizes.x2l else myTextSizes.xl,
                fontWeight = FontWeight.Bold,
                color = myColors.primary,
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.height(2.dp))
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(if (isTopLevel) 2.5.dp else 1.5.dp)
                    .background(myColors.primary)
            )
        }
    }
}
