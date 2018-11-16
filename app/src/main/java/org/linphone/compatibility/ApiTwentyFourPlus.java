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
import org.linphone.receivers.NotificationBroadcastReceiver;

import static org.linphone.compatibility.Compatibility.INTENT_NOTIF_ID;
import static org.linphone.compatibility.Compatibility.KEY_TEXT_REPLY;

/*
ApiTwentyFourPlus.java
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

@TargetApi(24)
public class ApiTwentyFourPlus {

	public static Notification createRepliedNotification(Context context, String reply) {
		Notification repliedNotification = new Notification.Builder(context)
            .setSmallIcon(R.drawable.topbar_chat_notification)
            .setContentText(context.getString(R.string.notification_replied_label).replace("%s", reply))
            .build();

		return repliedNotification;
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
		replyIntent.setAction(context.getPackageName() + ".REPLY_ACTION");
		replyIntent.putExtra(INTENT_NOTIF_ID, notificationId);

		PendingIntent replyPendingIntent = PendingIntent.getBroadcast(context,
            notificationId, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT);

		Notification.Action action = new Notification.Action.Builder(R.drawable.chat_send_over,
            context.getString(R.string.notification_reply_label), replyPendingIntent)
            .addRemoteInput(remoteInput)
            .build();

		Notification notif;
		notif = new Notification.Builder(context)
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

		return notif;
	}
}
