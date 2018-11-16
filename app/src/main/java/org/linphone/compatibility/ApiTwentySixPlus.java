package org.linphone.compatibility;


import android.annotation.TargetApi;
import android.app.FragmentTransaction;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.ViewTreeObserver;

import org.linphone.R;
import org.linphone.mediastream.Log;
import org.linphone.receivers.NotificationBroadcastReceiver;

import static org.linphone.compatibility.Compatibility.INTENT_ANSWER_CALL_NOTIF_ACTION;
import static org.linphone.compatibility.Compatibility.INTENT_CALL_ID;
import static org.linphone.compatibility.Compatibility.INTENT_HANGUP_CALL_NOTIF_ACTION;
import static org.linphone.compatibility.Compatibility.INTENT_NOTIF_ID;
import static org.linphone.compatibility.Compatibility.INTENT_REPLY_NOTIF_ACTION;
import static org.linphone.compatibility.Compatibility.KEY_TEXT_REPLY;

/*
ApiTwentySixPlus.java
Copyright (C) 2017  Belledonne Communications, Grenoble, France

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

@TargetApi(26)
public class ApiTwentySixPlus {
	public static Notification createRepliedNotification(Context context, String reply) {
		return new Notification.Builder(context, context.getString(R.string.notification_channel_id))
            .setSmallIcon(R.drawable.topbar_chat_notification)
            .setContentText(context.getString(R.string.notification_replied_label).replace("%s", reply))
            .build();
	}

	public static void createServiceChannel(Context context) {
		NotificationManager notificationManager =
				(NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		// Create service/call notification channel
		String id = context.getString(R.string.notification_service_channel_id);
		CharSequence name = context.getString(R.string.content_title_notification_service);
		String description = context.getString(R.string.content_title_notification_service);
		NotificationChannel channel = new NotificationChannel(id, name, NotificationManager.IMPORTANCE_NONE);
		channel.setDescription(description);
		channel.enableVibration(false);
		channel.enableLights(false);
        channel.setShowBadge(false);
		notificationManager.createNotificationChannel(channel);
	}

    public static void createMessageChannel(Context context) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        // Create message notification channel
        String id = context.getString(R.string.notification_channel_id);
        String name = context.getString(R.string.content_title_notification);
        String description = context.getString(R.string.content_title_notification);
        NotificationChannel channel = new NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(description);
        channel.setLightColor(context.getColor(R.color.notification_color_led));
        channel.enableLights(true);
        channel.enableVibration(true);
        channel.setShowBadge(true);
        notificationManager.createNotificationChannel(channel);
    }

	public static Notification createMessageNotification(Context context, int notificationId, int msgCount, String msgSender, String msg, Bitmap contactIcon, PendingIntent intent) {
		String title;
		if (msgCount == 1) {
			title = msgSender;
		} else {
			title = context.getString(R.string.unread_messages).replace("%i", String.valueOf(msgCount));
		}

		String replyLabel = context.getResources().getString(R.string.notification_reply_label);
		RemoteInput remoteInput = new RemoteInput.Builder(KEY_TEXT_REPLY).setLabel(replyLabel).build();

		Intent replyIntent = new Intent(context, NotificationBroadcastReceiver.class);
		replyIntent.setAction(INTENT_REPLY_NOTIF_ACTION);
		replyIntent.putExtra(INTENT_NOTIF_ID, notificationId);

		PendingIntent replyPendingIntent = PendingIntent.getBroadcast(context,
            notificationId, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT);

		Notification.Action action = new Notification.Action.Builder(R.drawable.chat_send_over,
            context.getString(R.string.notification_reply_label), replyPendingIntent)
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build();

		return new Notification.Builder(context, context.getString(R.string.notification_channel_id))
			.setContentTitle(title)
			.setContentText(msg)
			.setSmallIcon(R.drawable.topbar_chat_notification)
			.setAutoCancel(true)
			.setContentIntent(intent)
			.setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE)
			.setLargeIcon(contactIcon)
			.setCategory(Notification.CATEGORY_MESSAGE)
			.setVisibility(Notification.VISIBILITY_PRIVATE)
			.setPriority(Notification.PRIORITY_HIGH)
			.setNumber(msgCount)
			.setWhen(System.currentTimeMillis())
			.setShowWhen(true)
			.setColor(context.getColor(R.color.notification_color_led))
			.addAction(action)
			.build();
	}

	public static Notification createInCallNotification(Context context,
        int callId, boolean showActions, String msg, int iconID, Bitmap contactIcon, String contactName, PendingIntent intent) {

        Notification.Builder builder = new Notification.Builder(context, context.getString(R.string.notification_service_channel_id))
			.setContentTitle(contactName)
			.setContentText(msg)
			.setSmallIcon(iconID)
			.setAutoCancel(false)
			.setContentIntent(intent)
			.setLargeIcon(contactIcon)
			.setCategory(Notification.CATEGORY_CALL)
			.setVisibility(Notification.VISIBILITY_PUBLIC)
			.setPriority(Notification.PRIORITY_HIGH)
			.setWhen(System.currentTimeMillis())
			.setShowWhen(true)
			.setColor(context.getColor(R.color.notification_color_led));

        if (showActions) {

            Intent hangupIntent = new Intent(context, NotificationBroadcastReceiver.class);
            hangupIntent.setAction(INTENT_HANGUP_CALL_NOTIF_ACTION);
            hangupIntent.putExtra(INTENT_CALL_ID, callId);

            PendingIntent hangupPendingIntent = PendingIntent.getBroadcast(context,
                    callId, hangupIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            Intent answerIntent = new Intent(context, NotificationBroadcastReceiver.class);
            answerIntent.setAction(INTENT_ANSWER_CALL_NOTIF_ACTION);
            answerIntent.putExtra(INTENT_CALL_ID, callId);

            PendingIntent answerPendingIntent = PendingIntent.getBroadcast(context,
                    callId, answerIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            builder.addAction(R.drawable.call_hangup, context.getString(R.string.notification_call_hangup_label), hangupPendingIntent);
            builder.addAction(R.drawable.call_audio_start, context.getString(R.string.notification_call_answer_label), answerPendingIntent);
        }
        return builder.build();
	}

	public static Notification createNotification(Context context, String title, String message, int icon, int level,
        Bitmap largeIcon, PendingIntent intent, boolean isOngoingEvent,int priority) {

		if (largeIcon != null) {
			return new Notification.Builder(context, context.getString(R.string.notification_service_channel_id))
				.setContentTitle(title)
				.setContentText(message)
				.setSmallIcon(icon, level)
				.setLargeIcon(largeIcon)
				.setContentIntent(intent)
				.setCategory(Notification.CATEGORY_SERVICE)
				.setVisibility(Notification.VISIBILITY_SECRET)
				.setPriority(priority)
				.setWhen(System.currentTimeMillis())
				.setShowWhen(true)
				.setColor(context.getColor(R.color.notification_color_led))
				.build();
		} else {
			return new Notification.Builder(context, context.getString(R.string.notification_service_channel_id))
				.setContentTitle(title)
				.setContentText(message)
				.setSmallIcon(icon, level)
				.setContentIntent(intent)
				.setCategory(Notification.CATEGORY_SERVICE)
				.setVisibility(Notification.VISIBILITY_SECRET)
				.setPriority(priority)
				.setWhen(System.currentTimeMillis())
				.setShowWhen(true)
				.setColor(context.getColor(R.color.notification_color_led))
				.build();
		}
	}

	public static Notification createMissedCallNotification(Context context, String title, String text, PendingIntent intent) {
		return new Notification.Builder(context, context.getString(R.string.notification_channel_id))
			.setContentTitle(title)
			.setContentText(text)
			.setSmallIcon(R.drawable.call_status_missed)
			.setAutoCancel(true)
			.setContentIntent(intent)
			.setDefaults(Notification.DEFAULT_SOUND
					| Notification.DEFAULT_VIBRATE)
			.setCategory(Notification.CATEGORY_MESSAGE)
			.setVisibility(Notification.VISIBILITY_PRIVATE)
			.setPriority(Notification.PRIORITY_HIGH)
			.setWhen(System.currentTimeMillis())
			.setShowWhen(true)
			.setColor(context.getColor(R.color.notification_color_led))
			.build();
	}

	public static Notification createSimpleNotification(Context context, String title, String text, PendingIntent intent) {
		return new Notification.Builder(context, context.getString(R.string.notification_channel_id))
			.setContentTitle(title)
			.setContentText(text)
			.setSmallIcon(R.drawable.linphone_logo)
			.setAutoCancel(true)
			.setContentIntent(intent)
			.setDefaults(Notification.DEFAULT_SOUND
					| Notification.DEFAULT_VIBRATE)
			.setCategory(Notification.CATEGORY_MESSAGE)
			.setVisibility(Notification.VISIBILITY_PRIVATE)
			.setPriority(Notification.PRIORITY_HIGH)
			.setWhen(System.currentTimeMillis())
			.setShowWhen(true)
			.setColorized(true)
			.setColor(context.getColor(R.color.notification_color_led))
			.build();
	}

	public static void startService(Context context, Intent intent) {
		context.startForegroundService(intent);
	}

	public static void setFragmentTransactionReorderingAllowed(FragmentTransaction transaction, boolean allowed) {
		transaction.setReorderingAllowed(allowed);
	}
}
