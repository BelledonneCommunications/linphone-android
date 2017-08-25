package org.linphone.compatibility;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.Bitmap;
import android.widget.TextView;
import android.annotation.TargetApi;

import org.linphone.R;

/*
ApiTwentyThreePlus.java
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
/**
 * @author Erwan Croze
 */
@TargetApi(26)
public class ApiTwentySixPlus {
	public static Notification createMessageNotification(Context context,
	                                                     int msgCount, String msgSender, String msg, Bitmap contactIcon,
	                                                     PendingIntent intent) {
		String title;
		if (msgCount == 1) {
			title = msgSender;
		} else {
			title = context.getString(R.string.unread_messages)
					.replace("%i", String.valueOf(msgCount));
		}

		Notification notif = new Notification.Builder(context)
				.setContentTitle(title)
				.setContentText(msg)
				.setSmallIcon(R.drawable.topbar_chat_notification)
				.setAutoCancel(true)
				.setContentIntent(intent)
				.setDefaults(
						Notification.DEFAULT_LIGHTS
								| Notification.DEFAULT_SOUND
								| Notification.DEFAULT_VIBRATE)
				.setWhen(System.currentTimeMillis())
				.setLargeIcon(contactIcon)
				.setNumber(msgCount)
				.build();

		return notif;
	}
}
