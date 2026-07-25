package com.abdownloadmanager.android.pages.shiroikumaui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.Color
import com.abdownloadmanager.android.automation.AutomationAuth
import com.abdownloadmanager.android.storage.AppSettingsStorage
import com.abdownloadmanager.android.storage.ShiroikumaUiSettings
import com.abdownloadmanager.android.ui.configurable.android.item.ColorConfigurable
import com.abdownloadmanager.android.ui.configurable.android.item.FontConfigurable
import com.abdownloadmanager.android.ui.configurable.android.item.SliderConfigurable
import com.abdownloadmanager.shared.pagemanager.NotificationSender
import com.abdownloadmanager.shared.settings.CommonSettings
import com.abdownloadmanager.shared.ui.configurable.Configurable
import com.abdownloadmanager.shared.ui.configurable.item.BooleanConfigurable
import com.abdownloadmanager.shared.ui.configurable.item.EnumConfigurable
import com.abdownloadmanager.shared.ui.theme.ThemeManager
import com.abdownloadmanager.shared.ui.widget.NotificationType
import com.abdownloadmanager.shared.util.BaseComponent
import com.abdownloadmanager.shared.util.ui.MyColors
import com.arkivanov.decompose.ComponentContext
import ir.amirab.util.compose.asStringSource
import ir.amirab.util.flow.mapStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import kotlin.math.roundToInt

/** Opens / closes the 白い熊 取得管理 UI page (implemented by MainComponent). */
interface ShiroikumaUiPageManager {
    fun openShiroikumaUiPage()
    fun closeShiroikumaUiPage()
}

/**
 * The 白い熊 取得管理 UI page: every customizable attribute of the UI, laid out as a
 * section > subgroup > items cascade (one indent step deeper per level), following
 * the sister repos (白い熊 電話 / メッセージ). The first section is the settings
 * Export/Import (Kōjiki flow).
 */
class ShiroikumaUiComponent(
    ctx: ComponentContext,
    private val pageManager: ShiroikumaUiPageManager,
    private val notificationSender: NotificationSender,
) : BaseComponent(ctx), KoinComponent {
    private val appSettings by inject<AppSettingsStorage>()
    private val themeManager by inject<ThemeManager>()
    private val uiSettings by inject<ShiroikumaUiSettings>()
    private val appContext by inject<Context>()

    sealed interface Entry {
        val level: Int

        data class Section(val title: String, override val level: Int) : Entry
        data class Item(val configurable: Configurable<*>, override val level: Int) : Entry

        /**
         * A tappable row: title, static description, and a live (status, isWarning) line —
         * optionally with a secondary action pinned to the right of the row.
         */
        data class Action(
            val title: String,
            val description: String,
            val status: StateFlow<Pair<String, Boolean>>,
            val onClick: () -> Unit,
            override val level: Int,
            val trailingLabel: String? = null,
            val onTrailingClick: (() -> Unit)? = null,
        ) : Entry
    }

    // ---- settings export / import (Kōjiki flow) ----

    /** The acknowledged-info dialog above the Export/Import panel. */
    data class InfoDialog(
        val title: String,
        val body: String,
        val isImport: Boolean,
    )

    /** A yes/no dialog for the one destructive action on this page (token regeneration). */
    data class ConfirmDialog(
        val title: String,
        val body: String,
        val confirmLabel: String,
        val onConfirm: () -> Unit,
    )

    val exportDir = uiSettings.exportDir
    val showExportImportPanel = MutableStateFlow(false)
    val infoDialog = MutableStateFlow<InfoDialog?>(null)
    val confirmDialog = MutableStateFlow<ConfirmDialog?>(null)

    /** (message, isWarning) for the "last export" line; refreshed on page open. */
    val latestExportStatus = MutableStateFlow("" to false)

    init {
        refreshLatestExport()
    }

    /** Query the settable directory for the newest export (page open, dir change, export). */
    fun refreshLatestExport() {
        scope.launch(Dispatchers.IO) {
            val dir = exportDir.value
            latestExportStatus.value = when {
                dir.isBlank() -> "エクスポート先が未設定。" to true
                else -> {
                    val newest = ShiroikumaExport.latestExport(dir)
                    if (newest == null) "この場所にはまだエクスポートがない。" to true
                    else "最新エクスポート: ${ShiroikumaExport.formatTimestamp(newest.lastModified())}" to false
                }
            }
        }
    }

    fun openExportImport() {
        refreshLatestExport()
        showExportImportPanel.value = true
    }

    fun closeExportImportPanel() {
        showExportImportPanel.value = false
    }

    fun setExportDir(path: String) {
        uiSettings.exportDir.value = path
        refreshLatestExport()
    }

    fun doExport(cats: Set<ShiroikumaExport.Cat>) {
        if (cats.isEmpty()) {
            notifyError("カテゴリが未選択", "カテゴリを最低ひとつ選択すること。")
            return
        }
        val dir = exportDir.value
        if (dir.isBlank()) {
            notifyError("エクスポート先が未設定", "上の枠をタップして保存先を選択すること。")
            return
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val name = ShiroikumaExport.exportFileName()
                    val target = File(dir, name)
                    target.parentFile?.mkdirs()
                    target.outputStream().use { out ->
                        ShiroikumaExport.export(appContext, cats, out)
                    }
                    name
                }
            }
            result.onSuccess { name ->
                refreshLatestExport()
                infoDialog.value = InfoDialog(
                    title = "✓ エクスポート完了",
                    body = "${cats.size} カテゴリを保存した:\n$name",
                    isImport = false,
                )
            }.onFailure { e ->
                notifyError("エクスポート失敗", e.message ?: "不明なエラー")
            }
        }
    }

    fun doImport(uri: Uri, cats: Set<ShiroikumaExport.Cat>) {
        if (cats.isEmpty()) {
            notifyError("カテゴリが未選択", "カテゴリを最低ひとつ選択すること。")
            return
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("ファイルを読めなかった")
                    require(ShiroikumaExport.categoriesIn(bytes).isNotEmpty()) {
                        "このファイルに 白い熊 取得管理 のエクスポートが見つからない。"
                    }
                    ShiroikumaExport.import(appContext, bytes, cats)
                }
            }
            result.onSuccess { summary ->
                infoDialog.value = InfoDialog(
                    title = "✓ インポート完了",
                    body = "復元した:\n\n$summary\n\n全反映には再起動する。",
                    isImport = true,
                )
            }.onFailure { e ->
                notifyError("インポート失敗", e.message ?: "不明なエラー")
            }
        }
    }

    /**
     * Acknowledge the info dialog (export OK / import 後で): close the whole chain —
     * the info dialog, the panel beneath it, and the UI settings page itself.
     */
    fun acknowledgeInfoDialog() {
        infoDialog.value = null
        showExportImportPanel.value = false
        pageManager.closeShiroikumaUiPage()
    }

    fun restartNow() {
        ShiroikumaExport.restartApp(appContext)
    }

    // ---- 保存復元 automation (token-gated headless export) ----

    /** The abbreviated token, shown on the token row. */
    val automationTokenStatus = AutomationAuth.token.mapStateFlow {
        AutomationAuth.abbreviate(it) to false
    }

    fun copyAutomationToken() {
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard == null) {
            notifyError("コピーできない", "クリップボードを取得できなかった。")
            return
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("自動化トークン", AutomationAuth.token.value))
        notificationSender.sendNotification(
            tag = "shiroikuma-automation",
            title = "トークンをコピーした".asStringSource(),
            description = "自由作業盤の「保存復元の設定」に貼り付けること。".asStringSource(),
            type = NotificationType.Success,
        )
    }

    fun askRegenerateAutomationToken() {
        confirmDialog.value = ConfirmDialog(
            title = "トークンを再生成？",
            body = "今のトークンは無効になる。貼り付け済みの控え（自由作業盤の「保存復元の設定」など）は、" +
                "新しいトークンに更新しないと動かなくなる。",
            confirmLabel = "再生成",
            onConfirm = {
                AutomationAuth.regenerate()
                confirmDialog.value = null
                notificationSender.sendNotification(
                    tag = "shiroikuma-automation",
                    title = "トークンを再生成した".asStringSource(),
                    description = "行をタップしてコピーし、貼り付け済みの控えを更新すること。".asStringSource(),
                    type = NotificationType.Warning,
                )
            },
        )
    }

    fun dismissConfirmDialog() {
        confirmDialog.value = null
    }

    private fun notifyError(title: String, description: String) {
        notificationSender.sendNotification(
            tag = "shiroikuma-eximport",
            title = title.asStringSource(),
            description = description.asStringSource(),
            type = NotificationType.Error,
        )
    }

    // ---- the settings cascade ----

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
        add(Entry.Section("エクスポート / インポート", 0))
        add(
            Entry.Action(
                title = "エクスポート / インポート",
                description = "全設定をカテゴリ別に保存・復元する。",
                status = latestExportStatus,
                onClick = ::openExportImport,
                level = 1,
            )
        )
        add(
            Entry.Item(
                BooleanConfigurable(
                    title = "自動エクスポート".asStringSource(),
                    description = ("姉妹アプリの作業が、トークン付きインテントでこのアプリのエクスポートを" +
                        "起動できるようにする。").asStringSource(),
                    backedBy = AutomationAuth.enabled,
                    describe = { (if (it) "有効" else "無効").asStringSource() },
                ),
                1,
            )
        )
        add(
            Entry.Action(
                title = "自動化トークン",
                description = "タップで全文をコピーし、姉妹アプリの設定に貼り付ける。",
                status = automationTokenStatus,
                onClick = ::copyAutomationToken,
                level = 1,
                trailingLabel = "再生成",
                onTrailingClick = ::askRegenerateAutomationToken,
            )
        )

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
