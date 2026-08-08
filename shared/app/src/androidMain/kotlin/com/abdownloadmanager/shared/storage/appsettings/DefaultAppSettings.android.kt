package com.abdownloadmanager.shared.storage.appsettings

actual object PlatformDefaultSettings : DefaultAppSettings() {
    override val theme: String get() = "shiroikuma"
    override val defaultDarkTheme: String get() = "shiroikuma"
    override val useSparseFileAllocation: Boolean get() = false

    val browserIconInLauncher: Boolean get() = false
    // a short buzz when a download finishes
    val notificationVibration: Boolean get() = true
}
