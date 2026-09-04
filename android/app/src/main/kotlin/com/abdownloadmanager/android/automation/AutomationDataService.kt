package com.abdownloadmanager.android.automation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import com.abdownloadmanager.android.pages.shiroikumaui.ShiroikumaExport
import com.abdownloadmanager.android.pages.shiroikumaui.ShiroikumaExport.Cat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Where a data-door export or import actually runs.
 *
 * ## Why a foreground service and not the provider call
 *
 * The call returns in milliseconds; this can run for minutes. Two hard reasons it cannot be done
 * anywhere cheaper:
 *
 * - **A binder call holds the caller.** 応用管理 is drawing a list; a multi-minute synchronous call
 *   would freeze its UI, report no progress, and refuse cancellation.
 * - **A backgrounded app writing for minutes is frozen mid-stream on this phone**, which yields a
 *   truncated archive underneath a success reply — the worst possible failure, because it is
 *   indistinguishable from a good backup until the day it is restored.
 *
 * ## The descriptor
 *
 * Already duplicated by [AutomationProvider] before it got here, because the original belongs to
 * the binder transaction and is closed the moment `call()` returns. This service owns the copy and
 * closes it in a `finally` — leaking one would hold the caller's file open indefinitely, and a
 * caller cannot checksum or encrypt a file that is still open.
 *
 * ## A note on shape
 *
 * The reply is a `val` holding a lambda and the counting stream is a named class, rather than the
 * local `fun` and anonymous object they would naturally be. AGP's lint (`lintVitalAnalyzeRelease`)
 * crashes on that combination in this project — *FirDeclaration was not found for KtProperty* —
 * taking the whole release build with it. [StateExportReceiver] has always been written this way
 * and lints clean; this file follows it deliberately, so do not "tidy" either back.
 */
class AutomationDataService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Jobs actually in flight on this service instance.
     *
     * Android delivers every start to the SAME service object, so a second `onStartCommand` — a
     * caller retrying, or a stale job id — lands while the first export is still writing. Without
     * this count, that second start's teardown would `stopForeground` and `stopSelf` **underneath
     * the job still running**: the notification vanishes, the process loses its foreground
     * protection mid-write, and the archive is truncated under a caller that was told nothing.
     * Only the last job out turns off the lights.
     *
     * Mutated on the main thread when a job is admitted (service callbacks are serialised there)
     * and atomically when one finishes on the IO dispatcher.
     */
    private val activeJobs = AtomicInteger(0)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val startedJob = intent?.getStringExtra(EXTRA_JOB)
        val jobId = startedJob.orEmpty()
        val fd = startedJob?.let { HANDOVER.remove(it) }
        val importing = intent?.getBooleanExtra(EXTRA_IMPORTING, false) ?: false
        val replyAction = intent?.getStringExtra(AutomationProvider.KEY_REPLY_ACTION)
        val replyPackage = intent?.getStringExtra(AutomationProvider.KEY_REPLY_PACKAGE)

        val replied = AtomicBoolean(false)
        val reply: (String) -> Unit = { result ->
            // Exactly one terminal answer per job, whatever path got here — a synchronous failure
            // and an asynchronous success must never both fire. The same guard the broadcast
            // contract has carried since the first sister app.
            if (replied.compareAndSet(false, true)) {
                Log.i(TAG, "reply[$jobId] $result")
                if (jobId.isNotEmpty()) AutomationJobs.finish(jobId)
                if (!replyAction.isNullOrEmpty() && !replyPackage.isNullOrEmpty()) {
                    sendBroadcast(
                        Intent(replyAction).apply {
                            setPackage(replyPackage)
                            // Without this a caller that has been backgrounded never hears the
                            // answer, and on a clean phone it may not have been launched at all.
                            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                            putExtra(AutomationProvider.KEY_JOB_ID, jobId)
                            // the same id under the broadcast contract's name, one reader for both
                            putExtra("reply_id", jobId)
                            putExtra(AutomationProvider.KEY_RESULT, result)
                        }
                    )
                }
            }
        }

        // ALWAYS, and BEFORE any early return. Once startForegroundService() has been called the
        // platform requires startForeground() whatever this service then decides to do, and kills
        // the process with ForegroundServiceDidNotStartInTimeException otherwise — so a caller
        // retrying with a stale job id would crash the very app it is trying to back up. It also
        // comes after `reply` exists, so a foreground failure is answered rather than silent.
        try {
            goForeground(importing)
        } catch (t: Throwable) {
            Log.e(TAG, "job[$jobId] cannot go foreground", t)
            runCatching { fd?.close() }
            reply("ERROR:cannot go foreground: ${t.javaClass.simpleName}")
            return idleStop()
        }

        // A start with no job id, or one whose descriptor has already been collected — a stale
        // retry. Nothing to run, and no descriptor to answer about; the foreground obligation
        // above has been met, so stopping here is safe.
        if (startedJob == null || fd == null) {
            Log.w(TAG, "stale start (job=$startedJob, descriptor=${fd != null}) — nothing to do")
            return idleStop()
        }

        val progress = AutomationProgressSender(
            context = this,
            action = intent.getStringExtra(AutomationProvider.KEY_PROGRESS_ACTION)
                ?.takeIf { it.isNotBlank() },
            replyPackage = replyPackage,
            correlationId = jobId,
            idExtras = AutomationProgressSender.JOB_AND_REPLY_ID,
        )
        val items = intent.getStringExtra(AutomationProvider.KEY_ITEMS)

        // admitted, so this one counts — before the coroutine, on the main thread
        activeJobs.incrementAndGet()
        scope.launch {
            // A throttle is not a heartbeat. The export core calls back once per category, and a
            // category writing into a caller-supplied PIPE can block for as long as the caller is
            // slow to drain it — §3 presumes an app silent for two minutes is dead and fails its
            // slot. This re-sends the last line whenever the door has gone quiet.
            val watchdog = launch {
                while (isActive) {
                    delay(HEARTBEAT_INTERVAL_MS)
                    runCatching { progress.heartbeat() }
                }
            }
            try {
                fd.use { open ->
                    if (importing) runImport(jobId, open, reply)
                    else runExport(jobId, open, items, progress, reply)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "job[$jobId] failed", t)
                if (AutomationJobs.isCancelled(jobId)) reply("ERROR:cancelled")
                else reply("ERROR:${shortReason(t)}")
            } finally {
                watchdog.cancel()
                finishOne()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun runExport(
        jobId: String,
        fd: ParcelFileDescriptor,
        items: String?,
        progress: AutomationProgressSender,
        reply: (String) -> Unit,
    ) {
        val cats = resolve(items)
        if (cats == null) {
            reply("ERROR:unknown category in items: $items")
            return
        }
        val counting = ParcelFileDescriptor.AutoCloseOutputStream(fd).let(::CountingOutputStream)
        counting.use { out ->
            ShiroikumaExport.export(
                context = this,
                cats = cats,
                out = out,
                isCancelled = { AutomationJobs.isCancelled(jobId) },
            ) { done, total, label ->
                progress.send(done, total, label)
            }
        }
        // a cancel landing after the last entry still counts — nothing is delivered
        if (AutomationJobs.isCancelled(jobId)) {
            reply("ERROR:cancelled")
            return
        }
        progress.send(cats.size, cats.size, "完了", force = true)
        reply("OK:${counting.written}|${cats.size} categories")
    }

    /**
     * Read the whole archive before touching anything.
     *
     * [ShiroikumaExport.import] wants the bytes, and that is the right shape here for a reason
     * beyond convenience: a partial read that failed halfway would otherwise import half an
     * archive, and a half-restored app is worse than one that refused.
     *
     * **Spooled to disk first, and capped.** The far end of that descriptor is the caller's file
     * and its length is the caller's claim, not ours. Grown straight into a `ByteArrayOutputStream`
     * an unbounded stream peaks at several times the archive — before a single byte has been
     * validated — whereas a file grows at exactly its own size and can be cut off as it goes.
     */
    private suspend fun runImport(jobId: String, fd: ParcelFileDescriptor, reply: (String) -> Unit) {
        val spool = File(cacheDir, "automation-import-$jobId.zip")
        val bytes = try {
            val read = spoolTo(spool, ParcelFileDescriptor.AutoCloseInputStream(fd))
            when {
                read < 0 -> {
                    reply("ERROR:archive over ${ShiroikumaExport.humanSize(MAX_IMPORT_BYTES)}")
                    null
                }

                read == 0L -> {
                    reply("ERROR:empty archive")
                    null
                }

                else -> spool.readBytes()
            }
        } finally {
            runCatching { spool.delete() }
        } ?: return
        // Every category the archive actually carries, not every category we know about: asking
        // for one the archive lacks is how a restore ends up reporting success over nothing.
        val present = ShiroikumaExport.categoriesIn(bytes)
        if (present.isEmpty()) {
            reply("ERROR:archive carries no categories")
            return
        }
        val summary = ShiroikumaExport.import(this, bytes, present)
        // MUST come before the reply. 応用管理 force-stops this app the instant it hears OK, with
        // SIGKILL — and an import here does not reach disk synchronously: every settings category
        // is written by setting a MutableStateFlow whose persistence runs through a 500 ms
        // `debounce` before DataStore is even asked to write
        // (shared/app/src/commonMain/kotlin/com/abdownloadmanager/shared/util/BaseSettings.kt:32).
        // Replying immediately would report a restore that a SIGKILL then erases, which is the
        // worst shape of failure: success over missing data. The font files are already durable —
        // importFonts writes them with File.writeBytes — so this wait is only for the settings.
        delay(SETTINGS_FLUSH_MS)
        reply("OK:${summary.lines().count { it.isNotBlank() }} restored")
    }

    /** Bytes written, or -1 when the source ran past [MAX_IMPORT_BYTES] and was abandoned. */
    private fun spoolTo(target: File, source: InputStream): Long {
        var total = 0L
        source.use { input ->
            target.outputStream().buffered().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    total += n
                    if (total > MAX_IMPORT_BYTES) return -1
                    out.write(buf, 0, n)
                }
            }
        }
        return total
    }

    /** `items` absent = this app's default set; every id must be known or nothing is written. */
    private fun resolve(items: String?): Set<Cat>? {
        if (items.isNullOrBlank()) return Cat.entries.filter { it.defaultOn }.toSet()
        val wanted = items.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val found = wanted.mapNotNull { Cat.byId(it) }
        return if (found.size == wanted.size) found.toSet() else null
    }

    /**
     * `specialUse` is an API 34 value, so the typed overload is asked for only where it exists.
     *
     * This is the one place EMUI's `SDK_INT = 31` on an Android 13-based platform is a live hazard
     * rather than a curiosity: a version-derived guess is wrong in both directions, so the caller
     * catches this rather than trusting either branch. 白い熊's phone takes the plain branch, which
     * is exactly what `DownloadSystemService` has always done here.
     */
    private fun goForeground(importing: Boolean) {
        val notification = notification(importing)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notification(importing: Boolean): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(CHANNEL, "自動化データ", NotificationManager.IMPORTANCE_LOW)
        )
        return Notification.Builder(this, CHANNEL)
            .setContentTitle(if (importing) "設定を戻している" else "設定を書き出している")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
    }

    private fun shortReason(t: Throwable): String =
        (t.message ?: t.javaClass.simpleName)
            .replace('\n', ' ')
            .take(160)

    /** One job finished. The service goes down only when it was the last one. */
    private fun finishOne() {
        if (activeJobs.decrementAndGet() <= 0) shutDown()
    }

    /**
     * A start that will run nothing — a stale job id, or a foreground refusal.
     *
     * It stops the service **only when nothing else is running**. `stopSelf()` without a startId is
     * deliberate: with concurrent starts, stopping by startId is precisely what goes wrong, because
     * the stale start always carries the newest id and would satisfy the check that is supposed to
     * protect the running job.
     */
    private fun idleStop(): Int {
        if (activeJobs.get() == 0) shutDown()
        return START_NOT_STICKY
    }

    private fun shutDown() {
        // safe whether or not we ever made it to the foreground
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    override fun onDestroy() {
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    /**
     * Counted as it goes rather than stat'ed afterwards: the caller owns the file and this app may
     * not be able to see it at all — it can be an anonymous pipe, or a descriptor into a directory
     * this app cannot list.
     */
    private class CountingOutputStream(private val out: OutputStream) : OutputStream() {
        var written = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            written++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            written += len
        }

        override fun flush() = out.flush()

        override fun close() = out.close()
    }

    companion object {
        private const val TAG = "AutomationDataService"
        private const val CHANNEL = "automation_data"
        private const val NOTIFICATION_ID = 9714
        private const val EXTRA_JOB = "job"
        private const val EXTRA_IMPORTING = "importing"

        /**
         * The ceiling on an incoming archive. This app's backup is settings plus whatever font
         * files 白い熊 imported — nothing near this — so the cap only ever catches a caller handing
         * us something that is not our backup at all.
         */
        private const val MAX_IMPORT_BYTES = 256L * 1024 * 1024

        /** How often the watchdog re-sends the last progress line; well inside §3's two minutes. */
        private const val HEARTBEAT_INTERVAL_MS = 10_000L

        /**
         * How long to let an import settle before answering OK — the settings write-back's 500 ms
         * debounce plus DataStore's own write, with room to spare. Four times the debounce, because
         * being a second late with a reply costs nothing and being early loses the whole restore.
         */
        private const val SETTINGS_FLUSH_MS = 2_000L

        /**
         * The descriptor's way across, because an Intent is the wrong vehicle for one.
         *
         * A `ParcelFileDescriptor` in an Intent extra is duplicated by the system on delivery and
         * the copy's lifetime stops being ours to reason about. Handing it through a map keyed by
         * the job id keeps exactly one open descriptor with exactly one owner — this service, which
         * closes it in a `finally`.
         */
        private val HANDOVER = ConcurrentHashMap<String, ParcelFileDescriptor>()

        /**
         * Start the work. Returns null when the service is on its way, or the `ERROR:` line to
         * answer the caller with — **a refusal, never a throw**, so the provider does not have to
         * turn an exception back into the one grammar the family parses.
         *
         * On that failure path nothing will ever come to collect the descriptor, so this closes it
         * and the provider deliberately does not: closing it twice would be a different bug in
         * place of the leak.
         */
        fun start(
            context: Context,
            jobId: String,
            fd: ParcelFileDescriptor,
            importing: Boolean,
            extras: Bundle?,
        ): String? {
            HANDOVER[jobId] = fd
            return runCatching {
                context.startForegroundService(
                    Intent(context, AutomationDataService::class.java).apply {
                        putExtra(EXTRA_JOB, jobId)
                        putExtra(EXTRA_IMPORTING, importing)
                        putExtra(
                            AutomationProvider.KEY_ITEMS,
                            extras?.getString(AutomationProvider.KEY_ITEMS),
                        )
                        putExtra(
                            AutomationProvider.KEY_REPLY_ACTION,
                            extras?.getString(AutomationProvider.KEY_REPLY_ACTION),
                        )
                        putExtra(
                            AutomationProvider.KEY_REPLY_PACKAGE,
                            extras?.getString(AutomationProvider.KEY_REPLY_PACKAGE),
                        )
                        putExtra(
                            AutomationProvider.KEY_PROGRESS_ACTION,
                            extras?.getString(AutomationProvider.KEY_PROGRESS_ACTION),
                        )
                    }
                )
                null
            }.getOrElse { t ->
                Log.e(TAG, "job[$jobId] could not start the service", t)
                HANDOVER.remove(jobId)?.let { held -> runCatching { held.close() } }
                "ERROR:cannot start: ${t.javaClass.simpleName}"
            }
        }
    }
}
