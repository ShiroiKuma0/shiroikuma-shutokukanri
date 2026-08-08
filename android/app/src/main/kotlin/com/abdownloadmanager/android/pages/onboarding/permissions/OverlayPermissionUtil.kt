package com.abdownloadmanager.android.pages.onboarding.permissions

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import ir.amirab.util.ifThen

fun requestDisplayOverOtherAppsPermission(
    context: Context,
    startNewTask: Boolean = false,
) {
    try {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            data = ("package:" + context.packageName).toUri()
        }.ifThen(startNewTask) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            .ifThen(startNewTask) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        context.startActivity(intent)
    }
}

fun canDisplayOverOtherApps(
    context: Context
): Boolean {
    return Settings.canDrawOverlays(context)
}
