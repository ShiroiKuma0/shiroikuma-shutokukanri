package com.abdownloadmanager.android.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import java.io.File

/**
 * External font support, mirroring the sister repos (白い熊 電話 / メッセージ):
 * fonts are .ttf/.otf files imported into the app's private fonts dir; every imported
 * font is selectable app-wide, and pickers render each font's name in its own glyphs.
 */
object ShiroikumaFonts {
    /** Sentinel value for the built-in monospace family (can't collide with a filename). */
    const val MONOSPACE = "@monospace"
    const val DEFAULT_DISPLAY_NAME = "既定"
    private const val MONOSPACE_DISPLAY_NAME = "等幅"

    private val FONT_EXTENSIONS = setOf("ttf", "otf")
    private val familyCache = HashMap<String, FontFamily>()

    /** One pickable font; an empty fileName means "system default". */
    data class FontOption(val displayName: String, val fileName: String)

    fun fontsDir(context: Context): File =
        File(context.filesDir, "fonts").apply { mkdirs() }

    /** Built-in families + every font the user has imported. */
    fun availableFonts(context: Context): List<FontOption> = buildList {
        add(FontOption(DEFAULT_DISPLAY_NAME, ""))
        add(FontOption(MONOSPACE_DISPLAY_NAME, MONOSPACE))
        fontsDir(context).listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in FONT_EXTENSIONS }
            ?.sortedBy { it.name.lowercase() }
            ?.forEach { add(FontOption(it.nameWithoutExtension, it.name)) }
    }

    fun displayName(fileName: String): String = when {
        fileName.isEmpty() -> DEFAULT_DISPLAY_NAME
        fileName == MONOSPACE -> MONOSPACE_DISPLAY_NAME
        else -> File(fileName).nameWithoutExtension
    }

    /** FontFamily for a stored value (null = system default); a bad font file falls back to default. */
    fun fontFamily(context: Context, fileName: String): FontFamily? {
        if (fileName.isEmpty()) {
            return null
        }
        if (fileName == MONOSPACE) {
            return FontFamily.Monospace
        }
        familyCache[fileName]?.let { return it }
        val family = runCatching {
            FontFamily(Font(File(fontsDir(context), fileName)))
        }.getOrNull() ?: return null
        familyCache[fileName] = family
        return family
    }

    /** Copy a picked font file into the fonts dir; returns its filename, or null if invalid. */
    fun import(context: Context, uri: Uri): String? {
        val name = fileName(context, uri) ?: return null
        if (name.substringAfterLast('.', "").lowercase() !in FONT_EXTENSIONS) {
            return null
        }
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return null
        val target = File(fontsDir(context), name)
        runCatching { target.writeBytes(bytes) }.getOrElse { return null }
        // the new file must actually parse as a font
        if (runCatching { android.graphics.Typeface.createFromFile(target) }.isFailure) {
            target.delete()
            return null
        }
        familyCache.remove(name)
        return name
    }

    private fun fileName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        return cursor.getString(index)
                    }
                }
            }
        return uri.lastPathSegment?.substringAfterLast('/')
    }
}
