package com.abdownloadmanager.android.automation

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The gate in front of the headless automation intents ([StateExportReceiver]): a master
 * switch that is OFF until 白い熊 turns it on, plus a per-install secret token that sister
 * apps must present on every request.
 *
 * Both live in their own SharedPreferences file — deliberately *not* in the settings
 * DataStore — so the token can never travel inside an export ZIP.
 */
object AutomationAuth : KoinComponent {
    private const val PREFS = "shiroikuma_automation"
    private const val KEY_ENABLED = "automation_enabled"
    private const val KEY_TOKEN = "automation_token"

    /** 24 random bytes, hex-encoded — the renrakusaki family size. */
    private const val TOKEN_BYTES = 24

    private val appContext by inject<Context>()
    private val scope by inject<CoroutineScope>()

    private val prefs by lazy {
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /** Master switch — default OFF; nothing is reachable until it is turned on. */
    val enabled: MutableStateFlow<Boolean> by lazy {
        MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false)).also { flow ->
            flow
                .onEach { prefs.edit().putBoolean(KEY_ENABLED, it).apply() }
                .launchIn(scope)
        }
    }

    /** The token, generated lazily on first read so the settings row always shows a value. */
    val token: MutableStateFlow<String> by lazy {
        MutableStateFlow(prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() } ?: newToken())
    }

    /** Throw the old token away; pasted copies in sister apps must be updated afterwards. */
    fun regenerate(): String = newToken().also { token.value = it }

    private fun newToken(): String {
        val bytes = ByteArray(TOKEN_BYTES).also { SecureRandom().nextBytes(it) }
        val hex = bytes.joinToString("") { "%02x".format(it) }
        prefs.edit().putString(KEY_TOKEN, hex).apply()
        return hex
    }

    /** Constant-time comparison — never `==` on a secret. */
    fun matches(candidate: String?): Boolean {
        if (candidate.isNullOrEmpty()) return false
        val stored = token.value
        if (stored.isEmpty()) return false
        return MessageDigest.isEqual(candidate.toByteArray(), stored.toByteArray())
    }

    /** `80922d8c…4c49a87c` — what the settings row shows instead of the whole secret. */
    fun abbreviate(t: String): String =
        if (t.length <= 20) t else t.take(8) + "…" + t.takeLast(8)
}
