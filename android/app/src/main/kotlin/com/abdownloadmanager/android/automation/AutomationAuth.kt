package com.abdownloadmanager.android.automation

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The gate in front of every automation entry point — the headless broadcast actions
 * ([StateExportReceiver]) and the data door ([AutomationProvider]) alike.
 *
 * ## What changed in contract v2 (白い熊, 2026-09-04)
 *
 * v1 shipped this app **closed**: the master switch defaulted to off and a caller also had to
 * present a 48-character secret 白い熊 had pasted from here into the calling app. That is the wrong
 * shape for where this is going. **A pasted secret cannot survive a wipe**, and the case the whole
 * family now exists to serve is 応用管理 restoring apps *and their data* onto a clean phone, where
 * nothing has been configured and nobody has pasted anything.
 *
 * So [enabled] now defaults to **true**, and [requireToken] is a new switch defaulting to **false**.
 * The token still exists, still regenerates, still never leaves the phone — it is simply opt-in.
 *
 * ## Idempotent about the token — required, not a nicety
 *
 * **A token handed to an app that does not require one is IGNORED, never refused.** Tokens live in
 * task arguments and workspace variables that outlive the setting they were pasted for; refusing
 * one would turn "白い熊 turned a switch off" into "half the batch mysteriously fails".
 *
 * ## Two ways in, one source of truth
 *
 * The [enabled] / [requireToken] flows back the settings rows and write through to the prefs on
 * every change. Everything that *gates* reads the prefs directly through [refuse], because
 * [AutomationProvider.onCreate] runs **before** `Application.onCreate` — a gate that needed Koin
 * would be a gate with a cold-start race in it.
 *
 * ## Device-local by design
 *
 * These live in their own SharedPreferences file — deliberately *not* in the settings DataStore and
 * never a [ShiroikumaExport] category — so no automation setting, least of all the token, can
 * travel inside an export ZIP to another phone.
 */
object AutomationAuth : KoinComponent {
    private const val PREFS = "shiroikuma_automation"
    private const val KEY_ENABLED = "automation_enabled"
    private const val KEY_REQUIRE_TOKEN = "automation_require_token"
    private const val KEY_TOKEN = "automation_token"

    /** Marks the one-time v2 seed below as done; see [seedV2]. */
    private const val KEY_V2_SEEDED = "automation_v2_seeded"

    /** 24 random bytes, hex-encoded — the renrakusaki family size. */
    private const val TOKEN_BYTES = 24

    private val appContext by inject<Context>()
    private val scope by inject<CoroutineScope>()

    private val prefs by lazy { prefsOf(appContext) }

    private fun prefsOf(context: Context): SharedPreferences =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .also(::seedV2)

    /**
     * Bring an install that already ran v1 onto the v2 defaults, exactly once.
     *
     * A plain change of default would not reach those installs: the v1 switch persisted its value
     * the moment anything read it, so every phone that ever opened the settings page has
     * `automation_enabled=false` written out and would stay closed forever under the new default —
     * which is precisely the app that then fails silently in the middle of a 保存復元 batch.
     *
     * This is the same one-time seed the 白い熊 theme uses for existing installs, and it is a seed
     * rather than a forced value: 白い熊 can turn either switch off afterwards and it stays off.
     */
    @Synchronized
    private fun seedV2(prefs: SharedPreferences) {
        if (prefs.getBoolean(KEY_V2_SEEDED, false)) return
        prefs.edit()
            .putBoolean(KEY_ENABLED, true)
            .putBoolean(KEY_REQUIRE_TOKEN, false)
            .putBoolean(KEY_V2_SEEDED, true)
            .commit()
    }

    /**
     * Master switch — **default ON** since contract v2.
     *
     * Kept as a switch rather than removed: it is the only way to close this app off entirely, and
     * a feature that can be turned on but never off is one 白い熊 cannot retreat from.
     */
    val enabled: MutableStateFlow<Boolean> by lazy { persisted(KEY_ENABLED, true) }

    /** Whether a caller must also present [token] — **default OFF**; the token is opt-in now. */
    val requireToken: MutableStateFlow<Boolean> by lazy { persisted(KEY_REQUIRE_TOKEN, false) }

    /**
     * **`commit()`, never `apply()` — this gate fails OPEN.**
     *
     * v2 flipped [KEY_ENABLED]'s default from false to **true**, so a write that never reaches disk
     * does not fall back to "off": it falls back to **ON**. And 応用管理 force-stops an app the
     * instant it replies to an import, with `Process.killProcess` — a `SIGKILL`, which leaves an
     * in-flight `apply()` nowhere to land. Turning an app off is the one action 白い熊 has for
     * shutting a sister app out, and it is the action most likely to be running near a force-stop;
     * losing it silently reopens the door. These are tiny, infrequent writes, so synchronous is the
     * right trade for every one of them.
     */
    private fun persisted(key: String, default: Boolean): MutableStateFlow<Boolean> =
        MutableStateFlow(prefs.getBoolean(key, default)).also { flow ->
            flow
                .onEach { prefs.edit().putBoolean(key, it).commit() }
                .launchIn(scope)
        }

    /** The token, generated lazily on first read so the settings row always shows a value. */
    val token: MutableStateFlow<String> by lazy { MutableStateFlow(tokenOf(appContext)) }

    /** Throw the old token away; pasted copies in sister apps must be updated afterwards. */
    fun regenerate(): String = newToken(appContext).also { token.value = it }

    /**
     * The whole gate, in the one place every entry point asks.
     *
     * Returns null to proceed, or the exact `ERROR:` string to answer with. Written as one function
     * so no receiver, provider or service can implement the two checks in a subtly different order —
     * which is how "disabled" and "bad token" drift apart across forty-two apps. The two are
     * reported distinctly because they debug differently.
     */
    fun refuse(context: Context, candidate: String?): String? = when {
        !isEnabled(context) -> "ERROR:automation disabled"
        isTokenRequired(context) && !isTokenValid(context, candidate) -> "ERROR:bad token"
        else -> null
    }

    fun isEnabled(context: Context): Boolean =
        prefsOf(context).getBoolean(KEY_ENABLED, true)

    fun isTokenRequired(context: Context): Boolean =
        prefsOf(context).getBoolean(KEY_REQUIRE_TOKEN, false)

    /** Constant-time comparison — never `==` on a secret, even now that it is optional. */
    fun isTokenValid(context: Context, candidate: String?): Boolean {
        if (candidate.isNullOrEmpty()) return false
        val stored = tokenOf(context)
        if (stored.isEmpty()) return false
        return MessageDigest.isEqual(candidate.toByteArray(), stored.toByteArray())
    }

    private fun tokenOf(context: Context): String =
        prefsOf(context).getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() } ?: newToken(context)

    private fun newToken(context: Context): String {
        val bytes = ByteArray(TOKEN_BYTES).also { SecureRandom().nextBytes(it) }
        val hex = bytes.joinToString("") { "%02x".format(it) }
        // commit(): the worst of the writes to lose, because 白い熊 may already have pasted this
        // value into a caller — and nothing surfaces the loss. The caller simply starts failing
        // "bad token" against a secret this app no longer believes in. See [persisted].
        prefsOf(context).edit().putString(KEY_TOKEN, hex).commit()
        return hex
    }

    /** `80922d8c…4c49a87c` — what the settings row shows instead of the whole secret. */
    fun abbreviate(t: String): String =
        if (t.length <= 20) t else t.take(8) + "…" + t.takeLast(8)
}
