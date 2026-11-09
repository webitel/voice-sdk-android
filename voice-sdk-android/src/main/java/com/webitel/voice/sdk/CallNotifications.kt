package com.webitel.voice.sdk

import android.app.PendingIntent
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person


/**
 * Optional helpers that build `CallStyle` call notifications for a [Call]. The SDK doesn't
 * post them or run a service: the app posts them, typically from its call foreground
 * service, and can adjust the returned builders.
 *
 * The app's part:
 * - Pass `PendingIntent.getActivity(...)` for the buttons that open UI: since Android 12 a
 *   receiver or service started from a notification can't start an activity.
 * - On Android 12+ a `CallStyle` notification is shown only as a foreground service
 *   notification or with a full-screen intent.
 * - Request `POST_NOTIFICATIONS` (Android 13+). The full-screen intent needs
 *   `USE_FULL_SCREEN_INTENT`; on Android 14+ check `NotificationManager.canUseFullScreenIntent()`.
 * - To ring until the call is answered, add `Notification.FLAG_INSISTENT` to the built
 *   incoming notification.
 *
 * Texts come from the `webitel_call_*` string resources, which the app can override.
 */
object CallNotifications {

    const val CHANNEL_INCOMING = "webitel_incoming_calls"
    const val CHANNEL_ONGOING = "webitel_ongoing_calls"


    /**
     * Creates the [CHANNEL_INCOMING] channel (high importance, [ringtone], vibration) and the
     * silent [CHANNEL_ONGOING] channel. Safe to call repeatedly; once a channel exists,
     * Android keeps its settings.
     *
     * @param ringtone the incoming call sound, `null` for none.
     */
    @JvmStatic
    @JvmOverloads
    fun createChannels(
        context: Context,
        ringtone: Uri? = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
    ) {
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_INCOMING, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.webitel_call_channel_incoming))
                .setSound(
                    ringtone,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setVibrationEnabled(true)
                .build()
        )
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ONGOING, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.webitel_call_channel_ongoing))
                .setSound(null, null)
                .build()
        )
    }


    /**
     * An incoming call notification on [CHANNEL_INCOMING] with decline and answer buttons.
     *
     * @param fullScreenIntent shows your call screen over the lock screen.
     */
    @JvmStatic
    @JvmOverloads
    fun incoming(
        context: Context,
        call: Call,
        @DrawableRes smallIcon: Int,
        answerIntent: PendingIntent,
        declineIntent: PendingIntent,
        fullScreenIntent: PendingIntent? = null
    ): NotificationCompat.Builder {
        val builder = NotificationCompat.Builder(context, CHANNEL_INCOMING)
            .setSmallIcon(smallIcon)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(caller(context, call), declineIntent, answerIntent))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
        fullScreenIntent?.let { builder.setFullScreenIntent(it, true) }
        return builder
    }


    /**
     * An outgoing or ongoing call notification on [CHANNEL_ONGOING] with a hang-up button.
     * Shows "Calling…" until an outgoing call is answered, "On hold" while held, and the
     * call duration otherwise. Build it again when the call state changes.
     *
     * @param contentIntent opens your call screen when the notification is tapped.
     */
    @JvmStatic
    @JvmOverloads
    fun ongoing(
        context: Context,
        call: Call,
        @DrawableRes smallIcon: Int,
        hangUpIntent: PendingIntent,
        contentIntent: PendingIntent? = null
    ): NotificationCompat.Builder {
        val builder = NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(smallIcon)
            .setStyle(NotificationCompat.CallStyle.forOngoingCall(caller(context, call), hangUpIntent))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
        when {
            call.isOnHold -> builder.setContentText(context.getString(R.string.webitel_call_on_hold))
            call.answeredAt > 0 -> builder.setUsesChronometer(true).setWhen(call.answeredAt)
            call.isOutgoing -> builder.setContentText(context.getString(R.string.webitel_call_outgoing))
        }
        return builder
    }


    private fun caller(context: Context, call: Call): Person {
        val name = call.remoteName?.takeIf { it.isNotBlank() }
            ?: call.remoteNumber?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.webitel_call_unknown_caller)
        return Person.Builder().setName(name).setImportant(true).build()
    }
}
