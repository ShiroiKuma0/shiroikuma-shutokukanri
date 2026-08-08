package com.abdownloadmanager.android.util

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The short buzz that accompanies a finished download.
 *
 * It is sent with notification audio attributes, so the system silences it under
 * Do Not Disturb the same way it silences the notification sound.
 */
class NotificationVibrator(
    private val context: Context,
) {
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
    }

    private val attributes by lazy {
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }

    fun vibrate() {
        val vibrator = vibrator?.takeIf { it.hasVibrator() } ?: return
        runCatching {
            vibrator.vibrate(
                VibrationEffect.createWaveform(FINISHED_PATTERN, NO_REPEAT),
                attributes,
            )
        }.onFailure {
            it.printStackTrace()
        }
    }

    private companion object {
        // wait, buzz, gap, buzz — two short taps, unmistakable but not a ringtone
        val FINISHED_PATTERN = longArrayOf(0, 60, 90, 60)
        const val NO_REPEAT = -1
    }
}
