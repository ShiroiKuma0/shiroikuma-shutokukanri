package com.abdownloadmanager.android.pages.shiroikumaui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.abdownloadmanager.android.pages.directorypicker.rememberAndroidDirectoryPickerLauncher
import com.abdownloadmanager.android.ui.SheetHeader
import com.abdownloadmanager.android.ui.SheetTitle
import com.abdownloadmanager.android.ui.SheetUI
import com.abdownloadmanager.shared.ui.widget.CheckBox
import com.abdownloadmanager.shared.ui.widget.Text
import com.abdownloadmanager.shared.util.OnFullyDismissed
import com.abdownloadmanager.shared.util.ResponsiveDialog
import com.abdownloadmanager.shared.util.div
import com.abdownloadmanager.shared.util.rememberResponsiveDialogState
import com.abdownloadmanager.shared.util.ui.myColors
import com.abdownloadmanager.shared.util.ui.theme.myTextSizes
import ir.amirab.util.compose.asStringSource

/**
 * The Export/Import panel (Kōjiki flow): settable target directory + latest-export status,
 * one category checklist shared by both directions, and an Arcanechat-style pill button
 * row — Cancel alone on the left, Import + Export grouped on the right.
 */
@Composable
fun ExportImportSheet(component: ShiroikumaUiComponent) {
    val state = rememberResponsiveDialogState(false)
    LaunchedEffect(Unit) {
        state.show()
    }
    state.OnFullyDismissed {
        component.closeExportImportPanel()
    }

    val exportDir by component.exportDir.collectAsState()
    val latestStatus by component.latestExportStatus.collectAsState()

    // seeded from the same flag the automation picker is told about, so both start ticked alike
    val selection = remember {
        mutableStateMapOf<ShiroikumaExport.Cat, Boolean>().apply {
            ShiroikumaExport.Cat.entries.forEach { put(it, it.defaultOn) }
        }
    }

    fun selected(): Set<ShiroikumaExport.Cat> =
        selection.filterValues { it }.keys

    val dirPicker = rememberAndroidDirectoryPickerLauncher(
        initialDirectory = exportDir.takeIf { it.isNotBlank() },
        title = "エクスポート先".asStringSource(),
        onDirectorySelected = { path ->
            path?.let(component::setExportDir)
        },
    )
    val importPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { component.doImport(it, selected()) }
    }

    ResponsiveDialog(state, state::hide) {
        SheetUI(
            header = {
                SheetHeader(
                    headerTitle = {
                        SheetTitle("エクスポート / インポート")
                    }
                )
            }
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    "全設定をカテゴリ別に保存・復元する。",
                    fontSize = myTextSizes.sm,
                    color = myColors.onSurface / 0.75f,
                )
                Spacer(Modifier.height(12.dp))

                // the settable export directory (tap to change)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .border(2.dp, myColors.primary, RoundedCornerShape(10.dp))
                        .clickable { dirPicker.launch() }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        "エクスポート先（タップで選択）",
                        fontSize = myTextSizes.xs,
                        color = myColors.primary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        exportDir.ifBlank { "未設定 — タップして選択" },
                        fontSize = myTextSizes.base,
                        fontWeight = FontWeight.Bold,
                        color = if (exportDir.isBlank()) myColors.error else myColors.onSurface,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    latestStatus.first,
                    fontSize = myTextSizes.sm,
                    color = if (latestStatus.second) myColors.error else myColors.onSurface / 0.8f,
                )

                Spacer(Modifier.height(12.dp))
                ThinDivider()
                Spacer(Modifier.height(8.dp))

                val allSelected = ShiroikumaExport.Cat.entries.all { selection[it] == true }
                CheckRow(
                    label = "すべて選択",
                    bold = true,
                    checked = allSelected,
                    onChange = { checked ->
                        ShiroikumaExport.Cat.entries.forEach { selection[it] = checked }
                    },
                )
                for (cat in ShiroikumaExport.Cat.entries) {
                    CheckRow(
                        label = cat.label,
                        bold = false,
                        checked = selection[cat] == true,
                        // sub-options sit indented under their parent and follow its toggle
                        indent = if (cat.parentId != null) 26.dp else 0.dp,
                        onChange = { checked ->
                            selection[cat] = checked
                            cat.children.forEach { selection[it] = checked }
                        },
                    )
                }

                Spacer(Modifier.height(8.dp))
                ThinDivider()
                Spacer(Modifier.height(12.dp))

                // Arcanechat row: Cancel alone on the left, actions grouped on the right
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PillButton("キャンセル") { state.hide() }
                    Spacer(Modifier.weight(1f))
                    PillButton("インポート") {
                        importPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                    }
                    Spacer(Modifier.width(8.dp))
                    PillButton("エクスポート") { component.doExport(selected()) }
                }
            }
        }
    }
}

/** The acknowledged info dialog: black surface, yellow border, pill buttons. */
@Composable
fun ExportImportInfoDialog(
    info: ShiroikumaUiComponent.InfoDialog,
    component: ShiroikumaUiComponent,
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(2.dp, myColors.primary, RoundedCornerShape(16.dp))
                .background(myColors.background)
                .padding(20.dp)
        ) {
            Text(
                info.title,
                fontSize = myTextSizes.xl,
                fontWeight = FontWeight.Bold,
                color = myColors.primary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                info.body,
                fontSize = myTextSizes.base,
                color = myColors.primary,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                if (info.isImport) {
                    PillButton("後で") { component.acknowledgeInfoDialog() }
                    Spacer(Modifier.width(8.dp))
                    PillButton("今すぐ再起動") { component.restartNow() }
                } else {
                    PillButton("OK") { component.acknowledgeInfoDialog() }
                }
            }
        }
    }
}

/** The yes/no dialog: same black surface + yellow border, Cancel left, action right. */
@Composable
fun ShiroikumaConfirmDialog(
    confirm: ShiroikumaUiComponent.ConfirmDialog,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(2.dp, myColors.primary, RoundedCornerShape(16.dp))
                .background(myColors.background)
                .padding(20.dp)
        ) {
            Text(
                confirm.title,
                fontSize = myTextSizes.xl,
                fontWeight = FontWeight.Bold,
                color = myColors.primary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                confirm.body,
                fontSize = myTextSizes.base,
                color = myColors.primary,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PillButton("キャンセル", onDismiss)
                Spacer(Modifier.weight(1f))
                PillButton(confirm.confirmLabel, confirm.onConfirm)
            }
        }
    }
}

@Composable
private fun CheckRow(
    label: String,
    bold: Boolean,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    indent: Dp = 0.dp,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(start = indent)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckBox(
            value = checked,
            onValueChange = onChange,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            fontSize = myTextSizes.base,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = myColors.onSurface,
        )
    }
}

/** kxkb-style thin spacer between the panel's blocks. */
@Composable
private fun ThinDivider() {
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(myColors.primary / 0.4f)
    )
}

/** Arcanechat pill: black fill, thin accent border, accent text. */
@Composable
private fun PillButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .border(1.5.dp, myColors.primary, RoundedCornerShape(50))
            .background(myColors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = myTextSizes.base,
            fontWeight = FontWeight.Bold,
            color = myColors.primary,
        )
    }
}
