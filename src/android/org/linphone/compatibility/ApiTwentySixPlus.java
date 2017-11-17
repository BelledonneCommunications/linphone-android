package org.linphone.compatibility;


import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.view.ViewTreeObserver;

import org.linphone.R;

/*
ApiTwentyOnePlus.java
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

	public static void CreateChannel(Context context) {
		NotificationManager notificationManager =
				(NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		// Create service notification channel
		String id = context.getString(R.string.notification_service_channel_id);
		CharSequence name = context.getString(R.string.content_title_notification_service);
		String description = context.getString(R.string.content_title_notification_service);
		int importance = NotificationManager.IMPORTANCE_NONE;
		NotificationChannel mChannel = new NotificationChannel(id, name, importance);
		mChannel.setDescription(description);
		mChannel.enableVibration(false);
		notificationManager.createNotificationChannel(mChannel);
		// Create message/call notification channel
		id = context.getString(R.string.notification_channel_id);
		name = context.getString(R.string.content_title_notification);
		description = context.getString(R.string.content_title_notification);
		importance = NotificationManager.IMPORTANCE_HIGH;
		mChannel = new NotificationChannel(id, name, importance);
		mChannel.setDescription(description);
		mChannel.enableLights(true);
		mChannel.setLightColor(context.getColor(R.color.notification_color_led));
		mChannel.enableLights(true);
		notificationManager.createNotificationChannel(mChannel);
	}

	public static Notification createMessageNotification(Context context,
	                                                     int msgCount, String msgSender, String msg, Bitmap contactIcon,
	                                                     PendingIntent intent) {
		String title;
		if (msgCount == 1) {
			title = msgSender;
		} else {
			title = context.getString(R.string.unread_messages).replace("%i", String.valueOf(msgCount));
		}

		NotificationManager notificationManager =
				(NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		Notification notif = null;
		notif = new Notification.Builder(context, context.getString(R.string.notification_channel_id))
					.setContentTitle(title)
					.setContentText(msg)
					.setSmallIcon(R.drawable.topbar_chat_notification)
					.setAutoCancel(true)
					.setContentIntent(intent)
					.setDefaults(Notification.DEFAULT_SOUND
							| Notification.DEFAULT_VIBRATE)
					.setLargeIcon(contactIcon)
					.setCategory(Notification.CATEGORY_MESSAGE)
					.setVisibility(Notification.VISIBILITY_PRIVATE)
					.setPriority(Notification.PRIORITY_HIGH)
					.setNumber(msgCount)
					.build();

		return notif;
	}

	public static Notification createInCallNotification(Context context,
	                                                    String title, String msg, int iconID, Bitmap contactIcon,
	                                                    String contactName, PendingIntent intent) {

		Notification notif = new Notification.Builder(context, context.getString(R.string.notification_channel_id))
				.setContentTitle(contactName)
				.setContentText(msg)
				.setSmallIcon(iconID)
				.setAutoCancel(false)
				.setContentIntent(intent)
				.setLargeIcon(contactIcon)
				.setCategory(Notification.CATEGORY_CALL)
				.setVisibility(Notification.VISIBILITY_PUBLIC)
				.setPriority(Notification.PRIORITY_HIGH)
				.build();

		return notif;
	}

	public static Notification createNotification(Context context, String title, String message, int icon, int level, Bitmap largeIcon, PendingIntent intent, boolean isOngoingEvent,int priority) {
		Notification notif;

		if (largeIcon != null) {
			notif = new Notification.Builder(context, context.getString(R.string.notification_service_channel_id))
					.setContentTitle(title)
					.setContentText(message)
					.setSmallIcon(icon, level)
					.setLargeIcon(largeIcon)
					.setContentIntent(intent)
					.setCategory(Notification.CATEGORY_SERVICE)
					.setVisibility(Notification.VISIBILITY_SECRET)
					.setPriority(priority)
					.build();
		} else {
			notif = new Notification.Builder(context, context.getString(R.string.notification_service_channel_id))
					.setContentTitle(title)
					.setContentText(message)
					.setSmallIcon(icon, level)
					.setContentIntent(intent)
					.setCategory(Notification.CATEGORY_SERVICE)
					.setVisibility(Notification.VISIBILITY_SECRET)
					.setPriority(priority)
					.build();
		}

		return notif;
	}

	public static void removeGlobalLayoutListener(ViewTreeObserver viewTreeObserver, ViewTreeObserver.OnGlobalLayoutListener keyboardListener) {
		viewTreeObserver.removeOnGlobalLayoutListener(keyboardListener);
	}

	public static Notification createMissedCallNotification(Context context, String title, String text, PendingIntent intent) {
		Notification notif = new Notification.Builder(context, context.getString(R.string.notification_channel_id))
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
				.build();

		return notif;
	}

	public static Notification createSimpleNotification(Context context, String title, String text, PendingIntent intent) {
		Notification notif = new Notification.Builder(context, context.getString(R.string.notification_channel_id))
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
				.build();

		return notif;
	}

	public static void startService(Context context, Intent intent) {
		context.startForegroundService(intent);
	}
}
