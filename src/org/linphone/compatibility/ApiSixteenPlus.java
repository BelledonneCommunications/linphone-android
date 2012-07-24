package org.linphone.compatibility;

import org.linphone.R;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.Bitmap;

@TargetApi(16)
public class ApiSixteenPlus {

	public static Notification createMessageNotification(Context context, int msgCount, String msgSender, String msg, Bitmap contactIcon, PendingIntent intent) {
		String title, summary;
		if (msgCount == 1) {
			title = "Unread message from %s".replace("%s", msgSender);
			summary = "";
		} else {
			title = "%i unread messages".replace("%i", String.valueOf(msgCount));
			summary = "+" + (msgCount - 1) + " more";
		}
		
		Notification notif = new Notification.BigPictureStyle(
	      new Notification.Builder(context)
	         .setContentTitle(title)
	         .setContentText(msg)
	         .setSmallIcon(R.drawable.chat_icon_default)
	         .setAutoCancel(true)
	         .setDefaults(Notification.DEFAULT_LIGHTS | Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE)
	         .setWhen(System.currentTimeMillis())
	         .setLargeIcon(contactIcon))
	      .setSummaryText(summary)
	      .build();
		notif.contentIntent = intent;
		
		return notif;
	}

	public static Notification createInCallNotification(Context context,
			String title, String msg, int iconID, Bitmap contactIcon,
			PendingIntent intent) {
		
		Notification notif = new Notification.BigPictureStyle(
	      new Notification.Builder(context)
	         .setContentTitle(title)
	         .setContentText(msg)
	         .setSmallIcon(iconID)
	         .setAutoCancel(false)
	         .setWhen(System.currentTimeMillis())
	         .setLargeIcon(contactIcon))
	      .build();
		notif.contentIntent = intent;
		
		return notif;
	}
}
