package com.abdownloadmanager.android.pages.shiroikumaui

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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

// each cascade level (section > subgroup > items) sits one more step in
private val INDENT_STEP = 36.dp

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
                    for (entry in component.entries) {
                        when (entry) {
                            is ShiroikumaUiComponent.Entry.Section -> {
                                SectionHeader(entry.title, entry.level)
                            }

                            is ShiroikumaUiComponent.Entry.Item -> {
                                RenderConfigurable(
                                    cfg = entry.configurable,
                                    configurableUiProps = ConfigurableUiProps(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = INDENT_STEP * entry.level),
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
}

@Composable
private fun SectionHeader(title: String, level: Int) {
    val isTopLevel = level == 0
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = INDENT_STEP * level)
            .padding(top = if (isTopLevel) 24.dp else 12.dp, bottom = 4.dp)
    ) {
        Text(
            title,
            fontSize = if (isTopLevel) myTextSizes.xl else myTextSizes.lg,
            fontWeight = FontWeight.Bold,
            color = myColors.primary,
        )
        Spacer(Modifier.height(4.dp))
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(if (isTopLevel) 2.dp else 1.dp)
                .background(myColors.primary / (if (isTopLevel) 1f else 0.6f))
        )
    }
}
