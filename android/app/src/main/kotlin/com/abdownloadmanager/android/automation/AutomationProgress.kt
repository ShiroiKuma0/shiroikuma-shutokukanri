package com.abdownloadmanager.android.automation

import android.content.Context
import android.content.Intent
import android.os.SystemClock

/** How this app names itself in a progress line the caller renders. */
internal const val AUTOMATION_APP_LABEL = "白い熊 取得管理"

/** What the counts count. This app walks categories, so: 区分. */
internal const val AUTOMATION_UNIT = "区分"

/**
 * The one progress sender, shared by both automation doors.
 *
 * Numbers, never a percentage — 白い熊's explicit requirement — throttled to one broadcast per
 * 500 ms with a forced final one at completion. It is also the **heartbeat**: a caller presumes an
 * app that has gone quiet for two minutes is dead and fails its slot.
 *
 * ## Why one class and not two
 *
 * The §1 receiver and the §2a data door report the same thing and differ only in what the caller
 * calls the correlation id — `reply_id` for the broadcast contract, `job_id` for the data door.
 * That is [idExtras], and it is the whole reason this is parameterised rather than copied: two
 * implementations of the same watchdog drift, and the one that drifts is always the one nobody is
 * looking at (contract v2, §2a).
 *
 * A null [action] or [replyPackage] silently sends nothing — a caller that asked for no progress
 * gets none, which is exactly what "purely additive" means here.
 */
internal class AutomationProgressSender(
    private val context: Context,
    private val action: String?,
    private val replyPackage: String?,
    private val correlationId: String,
    private val idExtras: List<String>,
) {
    private var lastSentAt = 0L
    private var lastDone = 0
    private var lastTotal = 0
    private var lastLabel = ""

    /**
     * Re-send the last line if this door has gone quiet.
     *
     * **A throttle is not a heartbeat.** The throttle below stops a fast walk from flooding the
     * caller; it does nothing for the opposite failure, a single step that takes minutes — writing
     * one large category, or writing into a pipe the caller is slow to drain. §3 presumes an app
     * silent for two minutes is dead and fails its slot, so silence has to be broken even when the
     * numbers have not moved. Cheap: nothing is sent while progress is flowing normally.
     */
    fun heartbeat() {
        if (lastSentAt == 0L) return
        if (SystemClock.elapsedRealtime() - lastSentAt < HEARTBEAT_AFTER_MS) return
        send(lastDone, lastTotal, lastLabel, force = true)
    }

    fun send(done: Int, total: Int, label: String, force: Boolean = false) {
        if (action.isNullOrBlank() || replyPackage.isNullOrBlank()) return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastSentAt < MIN_INTERVAL_MS) return
        lastSentAt = now
        lastDone = done
        lastTotal = total
        lastLabel = label
        context.sendBroadcast(
            Intent(action).apply {
                setPackage(replyPackage)
                // without this a backgrounded or freshly installed caller never hears a thing
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                idExtras.forEach { putExtra(it, correlationId) }
                putExtra("app", AUTOMATION_APP_LABEL)
                putExtra("text", "$AUTOMATION_UNIT $done/$total — $label")
                // the POSITION of the category being written, never a count of finished ones
                putExtra("current", done.toLong())
                putExtra("total", total.toLong())
                putExtra("unit", AUTOMATION_UNIT)
            }
        )
    }

    companion object {
        private const val MIN_INTERVAL_MS = 500L

        /** Break the silence well inside §3's two-minute presumption of death. */
        private const val HEARTBEAT_AFTER_MS = 25_000L

        /** The broadcast contract's correlation id (§1). */
        val REPLY_ID_ONLY = listOf("reply_id")

        /**
         * The data door's (§2a): the job id goes out under **both** names, so one progress reader
         * on the caller's side serves both doors.
         */
        val JOB_AND_REPLY_ID = listOf("job_id", "reply_id")
    }
}
