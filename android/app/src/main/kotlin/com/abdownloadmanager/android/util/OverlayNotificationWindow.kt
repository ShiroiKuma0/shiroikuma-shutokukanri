package com.abdownloadmanager.android.util

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import androidx.annotation.MainThread
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.abdownloadmanager.android.storage.ShiroikumaUiSettings
import com.abdownloadmanager.android.ui.ABDMOverlayNotificationContent
import com.abdownloadmanager.shared.repository.BaseAppRepository
import com.abdownloadmanager.shared.storage.BaseAppSettingsStorage
import com.abdownloadmanager.shared.ui.theme.ThemeManager
import com.abdownloadmanager.shared.ui.widget.NotificationManager
import ir.amirab.util.compose.IIconResolver
import ir.amirab.util.compose.localizationmanager.LanguageManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * The themed flash notification (black surface, yellow border) hosted in its own window so that it
 * can be drawn *on top of other apps* — the same box the app shows in-UI, instead of the unstylable
 * white system toast.
 *
 * Needs the "display over other apps" permission
 * ([com.abdownloadmanager.android.pages.onboarding.permissions.ABDMPermissions.DisplayOverOtherAppsPermission]);
 * without it [canShow] is false and the caller falls back to the system toast.
 */
class OverlayNotificationWindow(
    private val appContext: Context,
) : KoinComponent {
    private val languageManager: LanguageManager by inject()
    private val themeManager: ThemeManager by inject()
    private val appSettingsStorage: BaseAppSettingsStorage by inject()
    private val shiroikumaUiSettings: ShiroikumaUiSettings by inject()
    private val iconResolver: IIconResolver by inject()
    private val appRepository: BaseAppRepository by inject()
    private val notificationManager: NotificationManager by inject()

    private var shownWindow: OverlayWindowHost? = null

    fun canShow(): Boolean {
        return Settings.canDrawOverlays(appContext)
    }

    fun isShowing(): Boolean {
        return shownWindow != null
    }

    /**
     * @return whether the box is on screen — the caller shows the system toast when it is not.
     */
    @MainThread
    fun show(): Boolean {
        if (shownWindow != null) {
            return true
        }
        if (!canShow()) {
            return false
        }
        // nothing here may ever bring the app down: a window we cannot put up
        // simply means the caller falls back to the system toast
        return runCatching { addWindow() }
            .onFailure { it.printStackTrace() }
            .getOrDefault(false)
    }

    @MainThread
    fun hide() {
        val host = shownWindow ?: return
        shownWindow = null
        runCatching {
            host.view?.let { host.windowManager?.removeView(it) }
        }.onFailure {
            it.printStackTrace()
        }
        host.onDestroy()
    }

    @MainThread
    private fun addWindow(): Boolean {
        // no activity may have run in this process yet (a download started from the browser or
        // resumed by the service), so the theme/language managers are not necessarily booted
        AndroidUi.boot()
        val windowContext = createWindowContext() ?: return false
        val windowManager = windowContext.getSystemService(WindowManager::class.java) ?: return false
        val host = OverlayWindowHost()
        val view = ComposeView(windowContext).apply {
            setViewTreeLifecycleOwner(host)
            setViewTreeViewModelStoreOwner(host)
            setViewTreeSavedStateRegistryOwner(host)
            setContent {
                ABDMOverlayNotificationContent(
                    languageManager = languageManager,
                    themeManager = themeManager,
                    appSettingsStorage = appSettingsStorage,
                    shiroikumaUiSettings = shiroikumaUiSettings,
                    iconResolver = iconResolver,
                    appRepository = appRepository,
                    notificationManager = notificationManager,
                )
            }
        }
        // the composition is created on attach, so the host must already be alive
        host.onCreate()
        val added = runCatching {
            windowManager.addView(view, buildLayoutParams(windowContext))
        }.onFailure {
            it.printStackTrace()
            host.onDestroy()
        }.isSuccess
        if (added) {
            shownWindow = host.also {
                it.windowManager = windowManager
                it.view = view
            }
        }
        return added
    }

    /**
     * A window needs a context that knows which display it is on. The application context does not
     * (asking it for one throws), so we go through the default display.
     */
    private fun createWindowContext(): Context? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return appContext
        }
        val display = appContext.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
            ?: return null
        return appContext
            .createDisplayContext(display)
            .createWindowContext(
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                null,
            )
    }

    private fun buildLayoutParams(context: Context): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // purely informational: never take focus or swallow a touch meant for the app below
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (BOTTOM_MARGIN_DP * context.resources.displayMetrics.density).toInt()
            windowAnimations = android.R.style.Animation_Toast
        }
    }

    /**
     * A window added through [WindowManager] has none of the owners a Compose view expects from an
     * activity, so we play that role ourselves for as long as the box is on screen.
     */
    private class OverlayWindowHost :
        LifecycleOwner,
        ViewModelStoreOwner,
        SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val viewModelStore = ViewModelStore()
        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateRegistryController.savedStateRegistry

        var windowManager: WindowManager? = null
        var view: ComposeView? = null

        fun onCreate() {
            savedStateRegistryController.performAttach()
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }

        fun onDestroy() {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
            viewModelStore.clear()
            windowManager = null
            view = null
        }
    }

    private companion object {
        // matches the in-app flash notification, which floats this far above the bottom edge
        const val BOTTOM_MARGIN_DP = 96
    }
}
