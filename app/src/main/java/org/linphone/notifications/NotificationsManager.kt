/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service.STOP_FOREGROUND_REMOVE
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.RequiresPermission
import androidx.annotation.WorkerThread
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.contacts.getPerson
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreForegroundService
import org.linphone.core.CoreListenerStub
import org.linphone.core.Friend
import org.linphone.core.tools.Log
import org.linphone.ui.voip.VoipActivity
import org.linphone.utils.ImageUtils
import org.linphone.utils.LinphoneUtils

class NotificationsManager @MainThread constructor(private val context: Context) {
    companion object {
        private const val TAG = "[Notifications Manager]"

        const val INTENT_HANGUP_CALL_NOTIF_ACTION = "org.linphone.HANGUP_CALL_ACTION"
        const val INTENT_ANSWER_CALL_NOTIF_ACTION = "org.linphone.ANSWER_CALL_ACTION"

        const val INTENT_CALL_ID = "CALL_ID"
        const val INTENT_NOTIF_ID = "NOTIFICATION_ID"
    }

    private val notificationManager: NotificationManagerCompat by lazy {
        NotificationManagerCompat.from(context)
    }

    private val coreListener = object : CoreListenerStub() {
        @WorkerThread
        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State?,
            message: String
        ) {
            when (state) {
                Call.State.IncomingReceived, Call.State.IncomingEarlyMedia -> {
                    showCallNotification(call, true)
                }
                Call.State.Connected -> {
                    showCallNotification(call, false)
                }
                Call.State.End, Call.State.Error -> {
                    dismissCallNotification(call)
                }
                else -> {
                }
            }
        }

        @WorkerThread
        override fun onLastCallEnded(core: Core) {
            Log.i("$TAG Last call ended, stopping foreground service")
            stopCallForeground()
        }
    }

    private var coreService: CoreForegroundService? = null

    private val callNotificationsMap: HashMap<String, Notifiable> = HashMap()

    init {
        createServiceChannel()
        createIncomingCallNotificationChannel()

        for (notification in notificationManager.activeNotifications) {
            if (notification.tag.isNullOrEmpty()) {
                Log.w(
                    "$TAG Found existing (call?) notification [${notification.id}] without tag, cancelling it"
                )
                notificationManager.cancel(notification.id)
            }
        }
    }

    @MainThread
    fun onServiceStarted(service: CoreForegroundService) {
        Log.i("$TAG Service has been started")
        coreService = service

        coreContext.postOnCoreThread { core ->
            if (core.callsNb == 0) {
                Log.w("$TAG No call anymore, stopping service")
                stopCallForeground()
            } else {
                startCallForeground()
            }
        }
    }

    @MainThread
    fun onServiceDestroyed() {
        Log.i("$TAG Service has been destroyed")
        coreService = null
    }

    @WorkerThread
    fun onCoreStarted(core: Core) {
        Log.i("$TAG Core has been started")
        core.addListener(coreListener)
    }

    @WorkerThread
    fun onCoreStopped(core: Core) {
        Log.i("$TAG Getting destroyed, clearing foreground Service & call notifications")
        core.removeListener(coreListener)
    }

    @WorkerThread
    private fun showCallNotification(call: Call, isIncoming: Boolean) {
        val notifiable = getNotifiableForCall(call)

        val callNotificationIntent = Intent(context, VoipActivity::class.java)
        callNotificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            callNotificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = createCallNotification(
            context,
            call,
            notifiable,
            pendingIntent,
            isIncoming
        )
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            notify(notifiable.notificationId, notification)
        }
    }

    @WorkerThread
    private fun startCallForeground() {
        val channelId = context.getString(R.string.notification_channel_service_id)
        val channel = notificationManager.getNotificationChannel(channelId)
        val importance = channel?.importance ?: NotificationManagerCompat.IMPORTANCE_NONE
        if (importance == NotificationManagerCompat.IMPORTANCE_NONE) {
            Log.e("$TAG Service channel has been disabled, can't start foreground service!")
            return
        }

        val notifiable = getNotifiableForCall(
            coreContext.core.currentCall ?: coreContext.core.calls.first()
        )
        val notif = notificationManager.activeNotifications.find {
            it.id == notifiable.notificationId
        }
        notif ?: return

        val service = coreService
        if (service != null) {
            Log.i("$TAG Service found, starting it as foreground using notification")
            // TODO FIXME: API LEVEL, add compatibility
            try {
                service.startForeground(
                    notifiable.notificationId,
                    notif.notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                        or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                        or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                )
            } catch (e: Exception) {
                Log.e("$TAG Can't start service as foreground! $e")
            }
        } else {
            Log.w("$TAG Core Foreground Service hasn't started yet...")
        }
    }

    @WorkerThread
    private fun stopCallForeground() {
        val service = coreService
        if (service != null) {
            Log.i("$TAG Stopping foreground service")
            service.stopForeground(STOP_FOREGROUND_REMOVE)
            service.stopSelf()
        } else {
            Log.w("$TAG Can't stop foreground service & notif, no service was found")
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    @WorkerThread
    private fun notify(id: Int, notification: Notification, tag: String? = null) {
        Log.i("$TAG Notifying [$id] with tag [$tag]")
        try {
            notificationManager.notify(tag, id, notification)
        } catch (iae: IllegalArgumentException) {
            if (coreService == null && tag == null) {
                // We can't notify using CallStyle if there isn't a foreground service running
                Log.w(
                    "$TAG Foreground service hasn't started yet, can't display a CallStyle notification until then: $iae"
                )
            } else {
                Log.e("$TAG Illegal Argument Exception occurred: $iae")
            }
        } catch (e: Exception) {
            Log.e("$TAG Exception occurred: $e")
        }
    }

    @WorkerThread
    fun cancelNotification(id: Int, tag: String? = null) {
        Log.i("$TAG Canceling [$id] with tag [$tag]")
        notificationManager.cancel(tag, id)
    }

    @WorkerThread
    private fun getNotificationIdForCall(call: Call): Int {
        return call.callLog.startDate.toInt()
    }

    @WorkerThread
    private fun getNotifiableForCall(call: Call): Notifiable {
        val address = call.remoteAddress.asStringUriOnly()
        var notifiable: Notifiable? = callNotificationsMap[address]
        if (notifiable == null) {
            notifiable = Notifiable(getNotificationIdForCall(call))
            notifiable.callId = call.callLog.callId

            callNotificationsMap[address] = notifiable
        }
        return notifiable
    }

    @WorkerThread
    private fun createCallNotification(
        context: Context,
        call: Call,
        notifiable: Notifiable,
        pendingIntent: PendingIntent?,
        isIncoming: Boolean
    ): Notification {
        val declineIntent = getCallDeclinePendingIntent(notifiable)
        val answerIntent = getCallAnswerPendingIntent(notifiable)

        val contact =
            coreContext.contactsManager.findContactByAddress(call.remoteAddress)
        val contactPicture = contact?.photo
        val roundPicture = if (!contactPicture.isNullOrEmpty()) {
            ImageUtils.getRoundBitmapFromUri(context, Uri.parse(contactPicture))
        } else {
            null
        }
        val displayName = contact?.name ?: LinphoneUtils.getDisplayName(call.remoteAddress)

        val person = getPerson(contact, displayName, roundPicture)
        val caller = Person.Builder()
            .setName(person.name)
            .setIcon(person.icon)
            .setUri(person.uri)
            .setKey(person.key)
            .setImportant(person.isImportant)
            .build()

        val isVideo = if (isIncoming) {
            call.remoteParams?.isVideoEnabled ?: false
        } else {
            call.currentParams.isVideoEnabled
        }

        val smallIcon = if (isVideo) {
            R.drawable.video_camera
        } else {
            R.drawable.phone
        }

        val style = if (isIncoming) {
            NotificationCompat.CallStyle.forIncomingCall(
                caller,
                declineIntent,
                answerIntent
            )
        } else {
            NotificationCompat.CallStyle.forOngoingCall(
                caller,
                declineIntent
            )
        }

        val channel = if (isIncoming) {
            context.getString(R.string.notification_channel_incoming_call_id)
        } else {
            context.getString(R.string.notification_channel_service_id)
        }

        val builder = NotificationCompat.Builder(
            context,
            channel
        ).apply {
            try {
                style.setIsVideo(isVideo)
                style.setAnswerButtonColorHint(
                    context.resources.getColor(R.color.green_online, context.theme)
                )
                style.setDeclineButtonColorHint(
                    context.resources.getColor(R.color.red_danger, context.theme)
                )
                setStyle(style)
            } catch (iae: IllegalArgumentException) {
                Log.e(
                    "[Api31 Compatibility] Can't use notification call style: $iae, using API 26 notification instead"
                )
            }
            setSmallIcon(smallIcon)
            setCategory(NotificationCompat.CATEGORY_CALL)
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            setWhen(System.currentTimeMillis())
            setAutoCancel(false)
            setShowWhen(true)
            setOngoing(true)
            color = ContextCompat.getColor(context, R.color.primary_color)
            setFullScreenIntent(pendingIntent, true)
        }

        return builder.build()
    }

    @WorkerThread
    private fun dismissCallNotification(call: Call) {
        val address = call.remoteAddress.asStringUriOnly()
        val notifiable: Notifiable? = callNotificationsMap[address]
        if (notifiable != null) {
            cancelNotification(notifiable.notificationId)
            callNotificationsMap.remove(address)
        } else {
            Log.w("$TAG No notification found for call with remote address [$address]")
        }
    }

    @AnyThread
    fun getCallDeclinePendingIntent(notifiable: Notifiable): PendingIntent {
        val hangupIntent = Intent(context, NotificationBroadcastReceiver::class.java)
        hangupIntent.action = INTENT_HANGUP_CALL_NOTIF_ACTION
        hangupIntent.putExtra(INTENT_NOTIF_ID, notifiable.notificationId)
        hangupIntent.putExtra(INTENT_CALL_ID, notifiable.callId)

        return PendingIntent.getBroadcast(
            context,
            notifiable.notificationId,
            hangupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    @AnyThread
    fun getCallAnswerPendingIntent(notifiable: Notifiable): PendingIntent {
        val answerIntent = Intent(context, NotificationBroadcastReceiver::class.java)
        answerIntent.action = INTENT_ANSWER_CALL_NOTIF_ACTION
        answerIntent.putExtra(INTENT_NOTIF_ID, notifiable.notificationId)
        answerIntent.putExtra(INTENT_CALL_ID, notifiable.callId)

        return PendingIntent.getBroadcast(
            context,
            notifiable.notificationId,
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    @WorkerThread
    private fun getPerson(friend: Friend?, displayName: String, picture: Bitmap?): Person {
        return friend?.getPerson()
            ?: Person.Builder()
                .setName(displayName)
                .setIcon(
                    if (picture != null) {
                        IconCompat.createWithAdaptiveBitmap(picture)
                    } else {
                        coreContext.contactsManager.contactAvatar
                    }
                )
                .setKey(displayName)
                .build()
    }

    @MainThread
    private fun createIncomingCallNotificationChannel() {
        val id = context.getString(R.string.notification_channel_incoming_call_id)
        val name = context.getString(R.string.notification_channel_incoming_call_name)

        val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH)
        channel.description = name
        channel.lightColor = context.getColor(R.color.primary_color)
        channel.lockscreenVisibility
        channel.enableVibration(true)
        channel.enableLights(true)
        channel.setShowBadge(false)
        notificationManager.createNotificationChannel(channel)
    }

    @MainThread
    private fun createServiceChannel() {
        val id = context.getString(R.string.notification_channel_service_id)
        val name = context.getString(R.string.notification_channel_service_name)

        val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_LOW)
        channel.description = name
        channel.enableVibration(false)
        channel.enableLights(false)
        channel.setShowBadge(false)
        notificationManager.createNotificationChannel(channel)
    }

    class Notifiable(val notificationId: Int) {
        var myself: String? = null
        var callId: String? = null
    }
}
