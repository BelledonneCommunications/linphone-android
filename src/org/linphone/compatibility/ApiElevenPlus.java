package org.linphone.compatibility;

import org.linphone.R;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;

/*
ApiElevenPlus.java
Copyright (C) 2012  Belledonne Communications, Grenoble, France

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
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
*/
/**
 * @author Sylvain Berfini
 */
@TargetApi(11)
public class ApiElevenPlus {

	@SuppressWarnings("deprecation")
	public static Notification createMessageNotification(Context context,
			int msgCount, String msgSender, String msg, Bitmap contactIcon,
			PendingIntent intent) {
		String title;
		if (msgCount == 1) {
			title = "Unread message from %s".replace("%s", msgSender);
		} else {
			title = "%i unread messages"
					.replace("%i", String.valueOf(msgCount));
		}

		Notification notif = new Notification.Builder(context)
						.setContentTitle(title)
						.setContentText(msg)
						.setSmallIcon(R.drawable.chat_icon_over)
						.setAutoCancel(true)
						.setDefaults(
								Notification.DEFAULT_LIGHTS
										| Notification.DEFAULT_SOUND
										| Notification.DEFAULT_VIBRATE)
						.setWhen(System.currentTimeMillis())
						.setLargeIcon(contactIcon).getNotification();
		notif.contentIntent = intent;

		return notif;
	}

	@SuppressWarnings("deprecation")
	public static Notification createInCallNotification(Context context,
			String title, String msg, int iconID, Bitmap contactIcon,
			String contactName, PendingIntent intent) {

		Notification notif = new Notification.Builder(context).setContentTitle(contactName)
						.setContentText(msg).setSmallIcon(iconID)
						.setAutoCancel(false)
						.setWhen(System.currentTimeMillis())
						.setLargeIcon(contactIcon).getNotification();
		notif.contentIntent = intent;

		return notif;
	}

	@SuppressWarnings("deprecation")
	public static void setNotificationLatestEventInfo(Notification notif,
			Context context, String title, String content, PendingIntent intent) {
		notif.setLatestEventInfo(context, title, content, intent);
	}

	public static void copyTextToClipboard(Context context, String msg) {
		ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE); 
	    ClipData clip = android.content.ClipData.newPlainText("Message", msg);
	    clipboard.setPrimaryClip(clip);
	}
}
