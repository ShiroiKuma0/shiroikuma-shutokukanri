package com.abdownloadmanager.android.automation

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import com.abdownloadmanager.android.pages.shiroikumaui.ShiroikumaExport
import com.abdownloadmanager.android.pages.shiroikumaui.ShiroikumaExport.Cat
import com.abdownloadmanager.android.util.ShiroikumaFonts
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The data door: export this app's own state, and put it back, for a caller we can identify.
 *
 * ## Why a provider and not the broadcast receiver next to it
 *
 * **A broadcast cannot tell you who sent it.** v1's answer to that was a shared secret, which
 * cannot survive the wipe this feature exists to recover from. A provider gets the caller's
 * identity from the framework — see [AutomationCallers] for what is actually checked, and why a
 * `shiroikuma.*` prefix would have been weaker than the token it replaced.
 *
 * **And a list needs a synchronous answer.** 応用管理 draws a row per installed app before any
 * export exists; a broadcast round trip per app to fill a list is the wrong shape.
 *
 * ## What does NOT happen here
 *
 * The payload. `call()` validates, starts a foreground service and returns — megabytes over minutes
 * inside a binder call would block the caller, report no progress, refuse cancellation and die
 * silently if this process were killed. The bytes go through a descriptor the caller opened, and
 * the terminal answer comes back on the broadcast the family already proved on EMUI.
 *
 * ## What this app's backup actually is
 *
 * **The settings and the queue's shape — never the downloads themselves.** Every [Cat] is
 * configuration: appearance, the download *settings*, the download categories, per-host rules,
 * proxy, bookmarks. No category walks the download list or the files on disk, so a part-finished
 * multi-gigabyte download cannot be swept into a backup, and [SIZE_FLOOR_BYTES] plus the imported
 * font files is the honest whole of what an export weighs.
 *
 * The other half of that: **in-progress downloads do not survive a restore.** A restored install
 * comes back with 白い熊's settings, categories and bookmarks, and an empty queue — the bytes
 * already fetched belong to the old device's storage, and half a download whose server state is
 * long gone is worth less than the honesty of not claiming to have kept it.
 */
class AutomationProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    /**
     * Every method answers a [Bundle] with [KEY_RESULT] — `OK…` or `ERROR:…`, the same vocabulary
     * the broadcast contract uses, so a caller has one grammar to parse rather than two.
     *
     * A refusal is returned, never thrown: an exception across a binder reaches the caller as a
     * `RuntimeException` with our stack trace in it, which tells 白い熊 nothing and tells a
     * misbehaving caller rather more than it should.
     */
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val ctx = context ?: return fail("ERROR:not ready")

        // WHO, before WHAT. A caller we cannot identify gets the same answer whatever it asked for.
        when (val verdict = AutomationCallers.verify(ctx, callingPackage)) {
            is AutomationCallers.Verdict.Refused -> return fail(verdict.why)
            AutomationCallers.Verdict.Allowed -> Unit
        }
        // Then this app's own switches — a token is ignored unless this app asks for one.
        AutomationAuth.refuse(ctx, extras?.getString(KEY_TOKEN))?.let { return fail(it) }

        return runCatching {
            when (method) {
                METHOD_DESCRIBE -> ok(describe(ctx))
                METHOD_EXPORT -> start(ctx, extras, importing = false)
                METHOD_IMPORT -> start(ctx, extras, importing = true)
                METHOD_CANCEL -> {
                    AutomationJobs.cancel(extras?.getString(KEY_JOB_ID))
                    ok("OK:cancelled")
                }

                else -> fail("ERROR:unknown method: $method")
            }
        }.getOrElse { fail("ERROR:${it.message ?: it::class.java.simpleName}") }
    }

    /**
     * What this app would export, answered without exporting anything.
     *
     * Returned from the call rather than written into the archive, deliberately: 応用管理 must draw
     * a row before an export exists, and at restore must judge compatibility **before** streaming
     * tens of megabytes into an app that would reject them — which it cannot do if the header is
     * buried inside an encrypted archive.
     */
    private fun describe(ctx: Context): String {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        val cats = Cat.entries.filter { it.defaultOn }
        val header = buildJsonObject {
            put("app_id", JsonPrimitive(ctx.packageName))
            @Suppress("DEPRECATION")
            put("version_code", JsonPrimitive(info.versionCode))
            put("version_name", JsonPrimitive(info.versionName.orEmpty()))
            put("format", JsonPrimitive(FORMAT))
            put("min_format_readable", JsonPrimitive(MIN_FORMAT_READABLE))
            // Koin is up by the time anything calls us, but an import here only writes settings —
            // there is no first-run state for it to merge badly against.
            put("requires_launch_first", JsonPrimitive(false))
            put("size_estimate", JsonPrimitive(sizeEstimate(ctx)))
            put("contains", JsonArray(cats.map { JsonPrimitive(it.label) }))
        }
        return "OK:" + json.encodeToString(JsonObject.serializer(), header)
    }

    /**
     * Roughly what an export of this app weighs, so 応用管理 can size a backup before it runs one.
     *
     * The imported `.ttf`/`.otf` files are the only bulk this app's backup has; everything else is
     * serialized settings, covered by the floor. **Downloaded files are deliberately not counted,
     * because they are deliberately not exported** — see the class comment.
     */
    private fun sizeEstimate(ctx: Context): Long =
        runCatching {
            SIZE_FLOOR_BYTES +
                ShiroikumaFonts.fontsDir(ctx).listFiles().orEmpty()
                    .filter { it.isFile }
                    .sumOf { it.length() }
        }.getOrDefault(SIZE_FLOOR_BYTES)

    /**
     * Hand the descriptor to a foreground service and get out of the way.
     *
     * The descriptor is **duplicated** before it leaves this method. The one in [extras] belongs to
     * the binder transaction and is closed when `call()` returns; a service reading it afterwards
     * would find it shut. That is a bug you only see under load, so it is not left to the service
     * to remember.
     */
    private fun start(ctx: Context, extras: Bundle?, importing: Boolean): Bundle {
        @Suppress("DEPRECATION")
        val fd = extras?.getParcelable<ParcelFileDescriptor>(KEY_FD)
            ?: return fail("ERROR:no descriptor")
        val dup = runCatching { fd.dup() }.getOrNull() ?: return fail("ERROR:descriptor unusable")
        val jobId = AutomationJobs.begin()
        // A refused start is answered, not thrown. AutomationDataService.start closes the
        // descriptor itself on that path, so there is deliberately no dup.close() here — closing
        // it twice would be a different bug in place of the leak.
        AutomationDataService.start(ctx, jobId, dup, importing, extras)?.let { why ->
            AutomationJobs.finish(jobId)
            return fail(why)
        }
        return ok("OK:$jobId")
    }

    private fun ok(result: String) = Bundle().apply { putString(KEY_RESULT, result) }
    private fun fail(why: String) = Bundle().apply { putString(KEY_RESULT, why) }

    // A provider that is only ever `call()`ed still has to answer these. Refusing loudly beats
    // returning an empty cursor, which reads downstream as "there is no data" rather than "wrong
    // door".
    override fun query(u: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor =
        throw UnsupportedOperationException("automation is call() only")

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri =
        throw UnsupportedOperationException("automation is call() only")

    override fun delete(uri: Uri, s: String?, a: Array<String>?): Int =
        throw UnsupportedOperationException("automation is call() only")

    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<String>?): Int =
        throw UnsupportedOperationException("automation is call() only")

    companion object {
        private val json = Json { encodeDefaults = true }

        const val METHOD_DESCRIBE = "describe"
        const val METHOD_EXPORT = "export"
        const val METHOD_IMPORT = "import"
        const val METHOD_CANCEL = "cancel"

        const val KEY_RESULT = "result"
        const val KEY_FD = "fd"
        const val KEY_TOKEN = "token"
        const val KEY_JOB_ID = "job_id"
        const val KEY_ITEMS = "items"
        const val KEY_REPLY_ACTION = "reply_action"
        const val KEY_REPLY_PACKAGE = "reply_package"
        const val KEY_PROGRESS_ACTION = "progress_action"

        /** This app's archive format — [ShiroikumaExport.VERSION], stated where a caller can see it. */
        const val FORMAT = ShiroikumaExport.VERSION

        /**
         * The oldest archive this build can still read.
         *
         * Version skew has a direction: old data into a newer app is normally fine, because an app
         * migrates its own storage; newer data into an older app is not. This field is what lets a
         * caller refuse the second case at discovery time, before anything is streamed.
         */
        const val MIN_FORMAT_READABLE = 1

        /** Every settings category serialized, rounded generously up. Fonts are counted for real. */
        private const val SIZE_FLOOR_BYTES = 64L * 1024
    }
}
