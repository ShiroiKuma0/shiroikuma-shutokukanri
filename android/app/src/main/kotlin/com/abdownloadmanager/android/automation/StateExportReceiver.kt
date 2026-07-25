package com.abdownloadmanager.android.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.util.Log
import com.abdownloadmanager.android.pages.shiroikumaui.ShiroikumaExport
import com.abdownloadmanager.android.pages.shiroikumaui.ShiroikumaExport.Cat
import com.abdownloadmanager.android.storage.ShiroikumaUiSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Survives the individual receiver instances; the work outlives a single onReceive. */
private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * The 保存復元 automation contract: a sister app (白い熊 自由作業盤) fires a token-gated
 * broadcast, this app exports itself headlessly — no Activity, no interaction — reports
 * progress with real counts, and answers with the written path and size.
 *
 * `<pkg>.action.EXPORT_STATE` — run the normal category-ZIP export, honouring the `path`
 * and `items` extras; `<pkg>.action.LIST_CATEGORIES` — answer with the selectable ids.
 *
 * The reply is always a **fresh broadcast** (EMUI drops live Binders — no ResultReceiver, no
 * PendingIntent — and severs the ordered-broadcast result between third-party apps), carries
 * `FLAG_INCLUDE_STOPPED_PACKAGES` so a backgrounded caller still hears it, and is sent
 * **exactly once** per request.
 */
class StateExportReceiver : BroadcastReceiver(), KoinComponent {
    private val uiSettings by inject<ShiroikumaUiSettings>()

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val action = intent.action ?: return
        val replyAction = intent.getStringExtra(EXTRA_REPLY_ACTION)?.takeIf { it.isNotBlank() }
        val replyPackage = intent.getStringExtra(EXTRA_REPLY_PACKAGE)?.takeIf { it.isNotBlank() }
        val replyId = intent.getStringExtra(EXTRA_REPLY_ID).orEmpty()
        val ordered = isOrderedBroadcast
        val pending = goAsync()

        if (replyAction == null || replyPackage == null) {
            Log.w(TAG, "$action without reply_action/reply_package — nowhere to answer, ignoring")
            pending.finish()
            return
        }

        val replied = AtomicBoolean(false)
        val reply: (String) -> Unit = { result ->
            if (replied.compareAndSet(false, true)) {
                try {
                    Log.i(TAG, "reply[$replyId] $result")
                    // correct AOSP behaviour, but never the only channel — EMUI severs it
                    if (ordered) runCatching { pending.setResultData(result) }
                    appContext.sendBroadcast(
                        Intent(replyAction).apply {
                            setPackage(replyPackage)
                            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                            putExtra(EXTRA_REPLY_ID, replyId)
                            putExtra("result", result)
                        }
                    )
                } finally {
                    pending.finish()
                }
            }
        }

        receiverScope.launch {
            try {
                val pkg = appContext.packageName
                when {
                    !AutomationAuth.enabled.value -> reply("ERROR:automation disabled")
                    !AutomationAuth.matches(intent.getStringExtra(EXTRA_TOKEN)) -> reply("ERROR:bad token")
                    action == "$pkg.$ACTION_LIST_CATEGORIES" -> reply(listCategories())
                    action == "$pkg.$ACTION_EXPORT_STATE" ->
                        runExport(appContext, intent, replyId, replyPackage, reply)

                    else -> reply("ERROR:unknown action: $action")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "$action failed", t)
                reply("ERROR:${shortReason(t)}")
            }
        }
    }

    /** `id<TAB>label` per line, sub-options carrying their parent's id as a third field. */
    private fun listCategories(): String =
        "OK:" + Cat.entries.joinToString("\n") { cat ->
            buildString {
                append(cat.id).append('\t').append(cat.label)
                cat.parentId?.let { append('\t').append(it) }
            }
        }

    private suspend fun runExport(
        context: Context,
        intent: Intent,
        replyId: String,
        replyPackage: String,
        reply: (String) -> Unit,
    ) {
        // items: absent/empty = everything; every id must be known or nothing is written
        val requested = intent.getStringExtra(EXTRA_ITEMS)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        val cats: Set<Cat>
        if (requested.isEmpty()) {
            cats = Cat.entries.toSet()
        } else {
            val unknown = requested.filter { Cat.byId(it) == null }
            if (unknown.isNotEmpty()) {
                reply("ERROR:unknown category in items: ${unknown.joinToString(",")}")
                return
            }
            cats = requested.mapNotNull { Cat.byId(it) }.toSet()
        }

        // the path extra overrides the app's own configured export directory
        val dirPath = intent.getStringExtra(EXTRA_PATH)?.trim()?.takeIf { it.isNotEmpty() }
            ?: uiSettings.exportDir.value.takeIf { it.isNotBlank() }
        if (dirPath == null) {
            reply("ERROR:no-directory")
            return
        }
        val dir = File(dirPath)
        if (!dir.isDirectory && !dir.mkdirs()) {
            reply(
                if (!hasAllFilesAccess()) "ERROR:no-storage-access"
                else "ERROR:cannot create directory: $dirPath"
            )
            return
        }

        val progress = ProgressSender(
            context = context,
            action = intent.getStringExtra(EXTRA_PROGRESS_ACTION)?.takeIf { it.isNotBlank() },
            replyPackage = replyPackage,
            replyId = replyId,
        )
        val target = File(dir, ShiroikumaExport.exportFileName())
        try {
            target.outputStream().use { out ->
                ShiroikumaExport.export(context, cats, out) { done, total, label ->
                    progress.send(done, total, label)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "export failed", t)
            runCatching { target.delete() }
            reply(
                if (!hasAllFilesAccess()) "ERROR:no-storage-access"
                else "ERROR:${shortReason(t)}"
            )
            return
        }
        val bytes = target.length()
        progress.send(cats.size, cats.size, "完了", force = true)
        reply("OK:${target.absolutePath}|$bytes|${ShiroikumaExport.humanSize(bytes)}|${cats.size} categories")
    }

    private fun hasAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    private fun shortReason(t: Throwable): String =
        (t.message ?: t::class.simpleName ?: "unknown error")
            .replace('\n', ' ')
            .take(160)

    /** Numbers, never a percentage — throttled to one broadcast per 500 ms. */
    private class ProgressSender(
        private val context: Context,
        private val action: String?,
        private val replyPackage: String,
        private val replyId: String,
    ) {
        private var lastSentAt = 0L

        fun send(done: Int, total: Int, label: String, force: Boolean = false) {
            if (action == null) return
            val now = SystemClock.elapsedRealtime()
            if (!force && now - lastSentAt < MIN_INTERVAL_MS) return
            lastSentAt = now
            context.sendBroadcast(
                Intent(action).apply {
                    setPackage(replyPackage)
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    putExtra(EXTRA_REPLY_ID, replyId)
                    putExtra("app", APP_LABEL)
                    putExtra("text", "$UNIT $done/$total — $label")
                    putExtra("current", done.toLong())
                    putExtra("total", total.toLong())
                    putExtra("unit", UNIT)
                }
            )
        }
    }

    companion object {
        private const val TAG = "StateExportReceiver"

        private const val ACTION_EXPORT_STATE = "action.EXPORT_STATE"
        private const val ACTION_LIST_CATEGORIES = "action.LIST_CATEGORIES"

        private const val EXTRA_TOKEN = "token"
        private const val EXTRA_PATH = "path"
        private const val EXTRA_ITEMS = "items"
        private const val EXTRA_PROGRESS_ACTION = "progress_action"
        private const val EXTRA_REPLY_ACTION = "reply_action"
        private const val EXTRA_REPLY_PACKAGE = "reply_package"
        private const val EXTRA_REPLY_ID = "reply_id"

        private const val APP_LABEL = "白い熊 取得管理"
        private const val UNIT = "区分"
        private const val MIN_INTERVAL_MS = 500L
    }
}
