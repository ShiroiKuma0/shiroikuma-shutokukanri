package com.abdownloadmanager.android.pages.shiroikumaui

import androidx.compose.ui.graphics.Color
import com.abdownloadmanager.android.storage.AppSettingsStorage
import com.abdownloadmanager.android.storage.ShiroikumaUiSettings
import com.abdownloadmanager.android.ui.configurable.android.item.ColorConfigurable
import com.abdownloadmanager.android.ui.configurable.android.item.FontConfigurable
import com.abdownloadmanager.android.ui.configurable.android.item.SliderConfigurable
import com.abdownloadmanager.shared.settings.CommonSettings
import com.abdownloadmanager.shared.ui.configurable.Configurable
import com.abdownloadmanager.shared.ui.configurable.item.EnumConfigurable
import com.abdownloadmanager.shared.ui.theme.ThemeManager
import com.abdownloadmanager.shared.util.BaseComponent
import com.abdownloadmanager.shared.util.ui.MyColors
import com.arkivanov.decompose.ComponentContext
import ir.amirab.util.compose.asStringSource
import ir.amirab.util.flow.mapStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.roundToInt

/** Opens / closes the 白い熊 取得管理 UI page (implemented by MainComponent). */
interface ShiroikumaUiPageManager {
    fun openShiroikumaUiPage()
    fun closeShiroikumaUiPage()
}

/**
 * The 白い熊 取得管理 UI page: every customizable attribute of the UI, laid out as a
 * section > subgroup > items cascade (one indent step deeper per level), following
 * the sister repos (白い熊 電話 / メッセージ).
 */
class ShiroikumaUiComponent(
    ctx: ComponentContext,
) : BaseComponent(ctx), KoinComponent {
    private val appSettings by inject<AppSettingsStorage>()
    private val themeManager by inject<ThemeManager>()
    private val uiSettings by inject<ShiroikumaUiSettings>()

    sealed interface Entry {
        val level: Int

        data class Section(val title: String, override val level: Int) : Entry
        data class Item(val configurable: Configurable<*>, override val level: Int) : Entry
    }

    private fun colorItem(
        title: String,
        description: String,
        override: MutableStateFlow<Color?>,
        themeColor: (MyColors) -> Color,
    ) = ColorConfigurable(
        title = title.asStringSource(),
        description = description.asStringSource(),
        backedBy = override,
        themeDefault = themeManager.currentThemeColor.mapStateFlow(themeColor),
    )

    private fun textSizeScaleItem() = EnumConfigurable(
        title = "文字サイズ".asStringSource(),
        description = "アプリ全体の文字の大きさ。".asStringSource(),
        backedBy = uiSettings.textSizeScale,
        possibleValues = listOf(0.8f, 0.9f, 1f, 1.1f, 1.25f, 1.5f),
        renderMode = EnumConfigurable.RenderMode.Spinner,
        describe = { "${(it * 100).roundToInt()}%".asStringSource() },
    )

    val entries: List<Entry> = buildList {
        add(Entry.Section("テーマ", 0))
        add(Entry.Item(CommonSettings.themeConfig(themeManager, scope), 1))
        add(Entry.Item(CommonSettings.uiScaleConfig(appSettings), 1))

        add(Entry.Section("色", 0))
        add(Entry.Section("基盤", 1))
        add(Entry.Item(colorItem("背景色", "アプリ全体の背景。", uiSettings.background) { it.background }, 2))
        add(Entry.Item(colorItem("文字色", "背景上の文字とアイコン。", uiSettings.onBackground) { it.onBackground }, 2))
        add(Entry.Section("表面", 1))
        add(Entry.Item(colorItem("表面色", "カード・メニュー・シートの背景。", uiSettings.surface) { it.surface }, 2))
        add(Entry.Item(colorItem("表面の文字色", "表面上の文字・アイコン・枠線。", uiSettings.onSurface) { it.onSurface }, 2))
        add(Entry.Section("アクセント", 1))
        add(Entry.Item(colorItem("アクセント色", "主要ボタン・枠線・強調。", uiSettings.primary) { it.primary }, 2))
        add(Entry.Item(colorItem("アクセント上の文字色", "アクセント色の上の文字。", uiSettings.onPrimary) { it.onPrimary }, 2))
        add(Entry.Item(colorItem("第二アクセント色", "グラデーションの相方。", uiSettings.secondary) { it.secondary }, 2))
        add(Entry.Item(colorItem("第二アクセント上の文字色", "第二アクセント色の上の文字。", uiSettings.onSecondary) { it.onSecondary }, 2))
        add(Entry.Section("状態色", 1))
        add(Entry.Item(colorItem("成功", "完了などの表示色。", uiSettings.success) { it.success }, 2))
        add(Entry.Item(colorItem("エラー", "失敗などの表示色。", uiSettings.error) { it.error }, 2))
        add(Entry.Item(colorItem("警告", "注意などの表示色。", uiSettings.warning) { it.warning }, 2))
        add(Entry.Item(colorItem("情報", "案内などの表示色。", uiSettings.info) { it.info }, 2))

        add(Entry.Section("レイアウト", 0))
        add(
            Entry.Item(
                SliderConfigurable(
                    title = "項目の間隔".asStringSource(),
                    description = "メイン画面の取得一覧で、項目同士の縦の間隔。0 = 隙間なし。".asStringSource(),
                    backedBy = uiSettings.listItemSpacing,
                    min = 0,
                    max = 32,
                    describe = { "$it dp".asStringSource() },
                ),
                1,
            )
        )

        add(Entry.Section("フォント", 0))
        add(
            Entry.Item(
                FontConfigurable(
                    title = "書体".asStringSource(),
                    description = "アプリ全体の書体。外部フォント（.ttf / .otf）を追加できる。".asStringSource(),
                    backedBy = uiSettings.fontFile,
                ),
                1,
            )
        )
        add(Entry.Item(textSizeScaleItem(), 1))
    }
}
