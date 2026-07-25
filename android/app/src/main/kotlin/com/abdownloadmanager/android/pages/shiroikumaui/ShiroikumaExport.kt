package com.abdownloadmanager.android.pages.shiroikumaui

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.abdownloadmanager.android.BuildConfig
import com.abdownloadmanager.android.storage.AppSettingsStorage
import com.abdownloadmanager.android.storage.BrowserBookmark
import com.abdownloadmanager.android.storage.BrowserBookmarksStorage
import com.abdownloadmanager.android.storage.ShiroikumaUiSettings
import com.abdownloadmanager.android.util.ShiroikumaFonts
import com.abdownloadmanager.shared.storage.SupportedSizeUnits
import com.abdownloadmanager.shared.ui.theme.ThemeManager
import com.abdownloadmanager.shared.util.category.Category
import com.abdownloadmanager.shared.util.category.CategoryManager
import com.abdownloadmanager.shared.util.category.CategoryStorage
import com.abdownloadmanager.shared.util.perhostsettings.IPerHostSettingsStorage
import com.abdownloadmanager.shared.util.perhostsettings.PerHostSettingsItem
import com.abdownloadmanager.shared.util.proxy.IProxyStorage
import com.abdownloadmanager.shared.util.proxy.ProxyData
import ir.amirab.util.enumValueOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 設定のエクスポート／インポート engine, following the Kōjiki (白い熊 考直) design:
 * the export is a ZIP of plain JSON files — one per selectable category — plus the
 * imported font files as real files under `fonts/`. A `manifest.json` lists the format,
 * version and categories present. Import applies only the selected categories, skips any
 * whose file is absent, tolerates unknown keys, and merges — it never wipes device-local
 * state (the export directory itself and the one-time seed flag are never exported).
 */
object ShiroikumaExport : KoinComponent {
    const val FORMAT = "shutokukanri-export"
    const val VERSION = 1

    /**
     * The family-wide backup name: `<english-app-name>_<yyyy-MM-dd_HH-mm-ss>.zip`, no version
     * and no decoration, so every sister app's backups sort and read uniformly in one directory.
     */
    const val EXPORT_PREFIX = "shiroikuma-shutokukanri_"

    /** Pre-2026-07-25 name (`…-<version>-export_…`) — still recognised when looking for the newest. */
    private const val LEGACY_EXPORT_PREFIX = "shiroikuma-shutokukanri-"

    private val appSettings by inject<AppSettingsStorage>()
    private val uiSettings by inject<ShiroikumaUiSettings>()
    private val themeManager by inject<ThemeManager>()
    private val proxyStorage by inject<IProxyStorage>()
    private val perHostStorage by inject<IPerHostSettingsStorage>()
    private val bookmarksStorage by inject<BrowserBookmarksStorage>()
    private val categoryManager by inject<CategoryManager>()
    private val categoryStorage by inject<CategoryStorage>()

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * A selectable category; `id` is the JSON file name (`<id>.json`) inside the ZIP, and the
     * id the automation contract accepts in its `items` extra. A category with a [parentId]
     * is a *sub-option* of that parent — its own independently selectable part of the export.
     */
    enum class Cat(val id: String, val label: String, val parentId: String? = null) {
        APPEARANCE("appearance", "外観（テーマ・色・書体）"),
        // the imported .ttf/.otf files themselves — the one bulky part of the backup
        APPEARANCE_FONTS("appearance.fonts", "書体ファイル", parentId = "appearance"),
        GENERAL("general", "一般（言語・表示・単位）"),
        DOWNLOAD("download", "ダウンロード設定"),
        NOTIFICATIONS("notifications", "通知"),
        SYSTEM("system", "システム（起動・API）"),
        PROXY("proxy", "プロキシ"),
        PER_HOST("perhost", "サイト別設定"),
        CATEGORIES("categories", "取得カテゴリ"),
        BOOKMARKS("bookmarks", "ブックマーク");

        val parent: Cat? get() = parentId?.let(::byId)
        val children: List<Cat> get() = entries.filter { it.parentId == id }

        companion object {
            fun byId(id: String): Cat? = entries.firstOrNull { it.id == id }
        }
    }

    // ---- field codecs: every settable item, keyed by a stable name ----

    private class Field(
        val name: String,
        val get: () -> JsonElement,
        val set: (JsonElement) -> Unit,
    )

    private fun boolField(name: String, flow: MutableStateFlow<Boolean>) =
        Field(name, { JsonPrimitive(flow.value) }, { flow.value = it.jsonPrimitive.boolean })

    private fun intField(name: String, flow: MutableStateFlow<Int>) =
        Field(name, { JsonPrimitive(flow.value) }, { flow.value = it.jsonPrimitive.int })

    private fun longField(name: String, flow: MutableStateFlow<Long>) =
        Field(name, { JsonPrimitive(flow.value) }, { flow.value = it.jsonPrimitive.long })

    private fun floatField(name: String, flow: MutableStateFlow<Float>) =
        Field(name, { JsonPrimitive(flow.value) }, { flow.value = it.jsonPrimitive.float })

    private fun stringField(name: String, flow: MutableStateFlow<String>) =
        Field(name, { JsonPrimitive(flow.value) }, { flow.value = it.jsonPrimitive.content })

    private fun nullableStringField(name: String, flow: MutableStateFlow<String?>) =
        Field(
            name,
            { flow.value?.let(::JsonPrimitive) ?: JsonNull },
            { flow.value = if (it is JsonNull) null else it.jsonPrimitive.content },
        )

    private fun colorField(name: String, flow: MutableStateFlow<Color?>) =
        Field(
            name,
            { flow.value?.let { c -> JsonPrimitive(c.toArgb().toUInt().toLong()) } ?: JsonNull },
            { flow.value = if (it is JsonNull) null else Color(it.jsonPrimitive.long) },
        )

    private fun sizeUnitField(name: String, flow: MutableStateFlow<SupportedSizeUnits>) =
        Field(
            name,
            { JsonPrimitive(flow.value.name) },
            { el -> el.jsonPrimitive.content.enumValueOrNull<SupportedSizeUnits>()?.let { flow.value = it } },
        )

    /** Theme selections go through the ThemeManager so an import takes effect live. */
    private fun themeField(name: String, current: () -> String, apply: (String) -> Unit) =
        Field(name, { JsonPrimitive(current()) }, { apply(it.jsonPrimitive.content) })

    private fun fieldsFor(cat: Cat): List<Field> = when (cat) {
        Cat.APPEARANCE -> listOf(
            themeField("theme", { appSettings.theme.value }, themeManager::setTheme),
            themeField("defaultDarkTheme", { appSettings.defaultDarkTheme.value }, themeManager::setDarkTheme),
            themeField("defaultLightTheme", { appSettings.defaultLightTheme.value }, themeManager::setLightTheme),
            floatField("uiScale", appSettings.uiScale),
            stringField("fontFile", uiSettings.fontFile),
            floatField("textSizeScale", uiSettings.textSizeScale),
            intField("listItemSpacing", uiSettings.listItemSpacing),
            colorField("background", uiSettings.background),
            colorField("onBackground", uiSettings.onBackground),
            colorField("surface", uiSettings.surface),
            colorField("onSurface", uiSettings.onSurface),
            colorField("primary", uiSettings.primary),
            colorField("onPrimary", uiSettings.onPrimary),
            colorField("secondary", uiSettings.secondary),
            colorField("onSecondary", uiSettings.onSecondary),
            colorField("success", uiSettings.success),
            colorField("error", uiSettings.error),
            colorField("warning", uiSettings.warning),
            colorField("info", uiSettings.info),
        )

        Cat.GENERAL -> listOf(
            nullableStringField("language", appSettings.selectedLanguage),
            boolField("showIconLabels", appSettings.showIconLabels),
            boolField("useRelativeDateTime", appSettings.useRelativeDateTime),
            boolField("useAverageSpeed", appSettings.useAverageSpeed),
            sizeUnitField("sizeUnit", appSettings.sizeUnit),
            sizeUnitField("speedUnit", appSettings.speedUnit),
            boolField("browserIconInLauncher", appSettings.browserIconInLauncher),
        )

        Cat.DOWNLOAD -> listOf(
            intField("threadCount", appSettings.threadCount),
            intField("maxConcurrentDownloads", appSettings.maxConcurrentDownloads),
            intField("maxDownloadRetryCount", appSettings.maxDownloadRetryCount),
            boolField("dynamicPartCreation", appSettings.dynamicPartCreation),
            boolField("useServerLastModifiedTime", appSettings.useServerLastModifiedTime),
            boolField("appendExtensionToIncompleteDownloads", appSettings.appendExtensionToIncompleteDownloads),
            boolField("useSparseFileAllocation", appSettings.useSparseFileAllocation),
            longField("speedLimit", appSettings.speedLimit),
            stringField("defaultDownloadFolder", appSettings.defaultDownloadFolder),
            boolField("useCategoryByDefault", appSettings.useCategoryByDefault),
            stringField("userAgent", appSettings.userAgent),
            boolField("ignoreSSLCertificates", appSettings.ignoreSSLCertificates),
            boolField("trackDeletedFilesOnDisk", appSettings.trackDeletedFilesOnDisk),
            boolField("deletePartialFileOnDownloadCancellation", appSettings.deletePartialFileOnDownloadCancellation),
        )

        Cat.NOTIFICATIONS -> listOf(
            boolField("notificationSound", appSettings.notificationSound),
            stringField("generalNotificationSound", appSettings.generalNotificationSound),
            stringField("successNotificationSound", appSettings.successNotificationSound),
            stringField("errorNotificationSound", appSettings.errorNotificationSound),
            boolField("showDownloadProgressDialog", appSettings.showDownloadProgressDialog),
            boolField("showDownloadCompletionDialog", appSettings.showDownloadCompletionDialog),
        )

        Cat.SYSTEM -> listOf(
            boolField("autoStartOnBoot", appSettings.autoStartOnBoot),
            boolField("apiEnabled", appSettings.apiEnabled),
            intField("apiPort", appSettings.apiPort),
            boolField("apiAuthEnabled", appSettings.apiAuthEnabled),
            stringField("apiAuthKey", appSettings.apiAuthKey),
        )

        // data categories are serialized whole, not per field; fonts are real files
        Cat.APPEARANCE_FONTS, Cat.PROXY, Cat.PER_HOST, Cat.CATEGORIES, Cat.BOOKMARKS -> emptyList()
    }

    // ---- export ----

    fun exportFileName(): String =
        EXPORT_PREFIX + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date()) + ".zip"

    /** The newest export in [dirPath], by file mtime; null when none (or the dir is unreadable). */
    fun latestExport(dirPath: String): File? =
        runCatching {
            File(dirPath).listFiles()
                ?.filter {
                    it.isFile && it.name.endsWith(".zip") &&
                        (it.name.startsWith(EXPORT_PREFIX) || it.name.startsWith(LEGACY_EXPORT_PREFIX))
                }
                ?.maxByOrNull { it.lastModified() }
        }.getOrNull()

    fun formatTimestamp(t: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(t))

    /** `4.6 MB` / `1.20 GB` — for display next to the real byte count. */
    fun humanSize(bytes: Long): String {
        val k = 1024.0
        return when {
            bytes < k -> "$bytes B"
            bytes < k * k -> String.format(Locale.ROOT, "%.1f KB", bytes / k)
            bytes < k * k * k -> String.format(Locale.ROOT, "%.1f MB", bytes / (k * k))
            else -> String.format(Locale.ROOT, "%.2f GB", bytes / (k * k * k))
        }
    }

    /**
     * Write a ZIP of the selected categories to [out] — the headless export core, shared by
     * the Export/Import panel and the automation receiver.
     *
     * [onProgress] is called once per category with `(done, total, label)`, counting real
     * categories (never a percentage).
     */
    suspend fun export(
        context: Context,
        cats: Set<Cat>,
        out: OutputStream,
        onProgress: (done: Int, total: Int, label: String) -> Unit = { _, _, _ -> },
    ) {
        // deterministic, parent-before-child order regardless of how the set was built
        val ordered = Cat.entries.filter { it in cats }
        ZipOutputStream(out).use { zip ->
            val manifest = buildJsonObject {
                put("format", JsonPrimitive(FORMAT))
                put("version", JsonPrimitive(VERSION))
                put("app", JsonPrimitive(context.packageName))
                put("appVersion", JsonPrimitive(BuildConfig.VERSION_NAME))
                put("createdTs", JsonPrimitive(System.currentTimeMillis()))
                put("categories", JsonArray(ordered.map { JsonPrimitive(it.id) }))
            }
            writeEntry(zip, "manifest.json", json.encodeToString(JsonObject.serializer(), manifest))
            ordered.forEachIndexed { index, cat ->
                onProgress(index + 1, ordered.size, cat.label)
                if (cat == Cat.APPEARANCE_FONTS) {
                    exportFonts(context, zip)
                    return@forEachIndexed
                }
                val payload = when (cat) {
                    Cat.PROXY -> json.encodeToString(ProxyData.serializer(), proxyStorage.proxyDataFlow.value)
                    Cat.PER_HOST -> json.encodeToString(
                        ListSerializer(PerHostSettingsItem.serializer()),
                        perHostStorage.perHostSettingsFlow.value,
                    )

                    Cat.CATEGORIES -> json.encodeToString(
                        ListSerializer(Category.serializer()),
                        // item ids reference downloads of THIS install — meaningless elsewhere
                        currentCategories().map { it.copy(items = emptyList()) },
                    )

                    Cat.BOOKMARKS -> json.encodeToString(
                        ListSerializer(BrowserBookmark.serializer()),
                        bookmarksStorage.bookmarksFlow.value,
                    )

                    else -> json.encodeToString(
                        JsonObject.serializer(),
                        buildJsonObject { fieldsFor(cat).forEach { put(it.name, it.get()) } },
                    )
                }
                writeEntry(zip, "${cat.id}.json", payload)
            }
        }
    }

    /**
     * The download categories. The CategoryManager only fills its list once the download
     * system boots, which a headless export does not wait for — fall back to the file.
     */
    private suspend fun currentCategories(): List<Category> {
        categoryManager.getCategories().takeIf { it.isNotEmpty() }?.let { return it }
        return runCatching {
            if (categoryStorage.isCategoriesSet()) categoryStorage.getCategories() else emptyList()
        }.getOrDefault(emptyList())
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray())
        zip.closeEntry()
    }

    private fun exportFonts(context: Context, zip: ZipOutputStream) {
        ShiroikumaFonts.fontsDir(context).listFiles()?.forEach { f ->
            if (!f.isFile) return@forEach
            zip.putNextEntry(ZipEntry("fonts/${f.name}"))
            zip.write(f.readBytes())
            zip.closeEntry()
        }
    }

    // ---- import ----

    /** Categories present in a ZIP (from its manifest, falling back to the files found). */
    fun categoriesIn(zipBytes: ByteArray): Set<Cat> {
        val files = readZip(zipBytes)
        files["manifest.json"]?.let { mf ->
            val cats = runCatching {
                json.parseToJsonElement(mf.decodeToString()).jsonObject["categories"]?.jsonArray
            }.getOrNull()
            if (cats != null) {
                val set = cats.mapNotNull { Cat.byId(it.jsonPrimitive.content) }.toMutableSet()
                // pre-sub-option exports listed only "appearance" but still carry the fonts
                if (files.keys.any { it.startsWith("fonts/") }) set.add(Cat.APPEARANCE_FONTS)
                if (set.isNotEmpty()) return set
            }
        }
        return Cat.entries.filter {
            when (it) {
                Cat.APPEARANCE_FONTS -> files.keys.any { name -> name.startsWith("fonts/") }
                else -> files.containsKey("${it.id}.json")
            }
        }.toSet()
    }

    /**
     * Apply the selected categories from a ZIP; absent files are skipped, a failing
     * category never fails the whole import. Returns the per-category summary lines.
     */
    suspend fun import(context: Context, zipBytes: ByteArray, cats: Set<Cat>): String {
        val files = readZip(zipBytes)
        val parts = mutableListOf<String>()
        for (cat in Cat.entries.filter { it in cats }) {
            if (cat == Cat.APPEARANCE_FONTS) {
                val written = importFonts(context, files)
                if (written > 0) parts.add("${cat.label}: $written")
                continue
            }
            val data = files["${cat.id}.json"] ?: continue
            val n = try {
                when (cat) {
                    Cat.PROXY -> {
                        proxyStorage.proxyDataFlow.value =
                            json.decodeFromString(ProxyData.serializer(), data.decodeToString())
                        1
                    }

                    Cat.PER_HOST -> {
                        val imported = json.decodeFromString(
                            ListSerializer(PerHostSettingsItem.serializer()),
                            data.decodeToString(),
                        )
                        // upsert by host — imported rows win, unrelated rows survive
                        val kept = perHostStorage.perHostSettingsFlow.value
                            .filter { existing -> imported.none { it.host == existing.host } }
                        perHostStorage.perHostSettingsFlow.value = kept + imported
                        imported.size
                    }

                    Cat.CATEGORIES -> {
                        val imported = json.decodeFromString(
                            ListSerializer(Category.serializer()),
                            data.decodeToString(),
                        )
                        // keep this install's item lists where a category of the same name exists
                        val itemsByName = currentCategories().associateBy({ it.name }, { it.items })
                        val merged = imported.map { it.copy(items = itemsByName[it.name].orEmpty()) }
                        categoryManager.setCategories(merged)
                        // the manager only persists once the download system has booted
                        runCatching { categoryStorage.setCategories(merged) }
                        imported.size
                    }

                    Cat.BOOKMARKS -> {
                        val imported = json.decodeFromString(
                            ListSerializer(BrowserBookmark.serializer()),
                            data.decodeToString(),
                        )
                        // upsert by url
                        val kept = bookmarksStorage.bookmarksFlow.value
                            .filter { existing -> imported.none { it.url == existing.url } }
                        bookmarksStorage.bookmarksFlow.value = kept + imported
                        imported.size
                    }

                    else -> {
                        val obj = json.parseToJsonElement(data.decodeToString()).jsonObject
                        var applied = 0
                        for (field in fieldsFor(cat)) {
                            val el = obj[field.name] ?: continue
                            if (runCatching { field.set(el) }.isSuccess) applied++
                        }
                        applied
                    }
                }
            } catch (e: Exception) {
                -1
            }
            if (n >= 0) parts.add("${cat.label}: $n")
        }
        return if (parts.isEmpty()) "（何も取り込めなかった）" else parts.joinToString("\n")
    }

    /** Restore the font files; returns how many were written. */
    private fun importFonts(context: Context, files: Map<String, ByteArray>): Int {
        val dir = ShiroikumaFonts.fontsDir(context)
        var written = 0
        for ((path, bytes) in files) {
            if (!path.startsWith("fonts/")) continue
            // basename only — no path traversal
            val name = File(path).name
            if (name.substringAfterLast('.', "").lowercase() !in setOf("ttf", "otf")) continue
            if (runCatching { File(dir, name).writeBytes(bytes) }.isSuccess) written++
        }
        return written
    }

    private fun readZip(bytes: ByteArray): Map<String, ByteArray> {
        val out = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    out[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
            }
        }
        return out
    }

    // ---- restart ----

    /** Relaunch the task and hard-exit, so every imported setting is picked up from scratch. */
    fun restartApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        context.startActivity(Intent.makeRestartActivityTask(intent.component))
        Runtime.getRuntime().exit(0)
    }
}
