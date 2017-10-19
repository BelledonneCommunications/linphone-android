package org.linphone.compatibility;

import java.util.ArrayList;

import org.linphone.R;

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents.Insert;
import android.widget.TextView;

/*
ApiElevenPlus.java
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

@TargetApi(11)
public class ApiElevenPlus {

	@SuppressWarnings("deprecation")
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
						.setContentIntent(intent)
						.setSmallIcon(R.drawable.chat)
						.setAutoCancel(true)
						.setDefaults(
								Notification.DEFAULT_LIGHTS
										| Notification.DEFAULT_SOUND
										| Notification.DEFAULT_VIBRATE)
						.setWhen(System.currentTimeMillis())
						.setNumber(msgCount)
						.setLargeIcon(contactIcon).getNotification();

		return notif;
	}

	@SuppressWarnings("deprecation")
	public static Notification createInCallNotification(Context context,
			String title, String msg, int iconID, Bitmap contactIcon,
			String contactName, PendingIntent intent) {

		Notification notif = new Notification.Builder(context).setContentTitle(contactName)
						.setContentText(msg).setSmallIcon(iconID)
						.setAutoCancel(false)
						.setContentIntent(intent)
						.setWhen(System.currentTimeMillis())
						.setLargeIcon(contactIcon).getNotification();
		notif.flags |= Notification.FLAG_ONGOING_EVENT;

		return notif;
	}

	@SuppressWarnings("deprecation")
	public static Notification createNotification(Context context, String title, String message, int icon, int level, Bitmap largeIcon, PendingIntent intent, boolean isOngoingEvent) {
		Notification notif;

		if (largeIcon != null) {
			notif = new Notification.Builder(context)
	        .setContentTitle(title)
	        .setContentText(message)
	        .setSmallIcon(icon, level)
	        .setLargeIcon(largeIcon)
	        .setContentIntent(intent)
	        .setWhen(System.currentTimeMillis())
	        .getNotification();
		} else {
			notif = new Notification.Builder(context)
	        .setContentTitle(title)
	        .setContentText(message)
	        .setSmallIcon(icon, level)
	        .setContentIntent(intent)
	        .setWhen(System.currentTimeMillis())
	        .getNotification();
		}
		if (isOngoingEvent) {
			notif.flags |= Notification.FLAG_ONGOING_EVENT;
		}

		return notif;
	}

	public static Intent prepareAddContactIntent(String displayName, String sipUri) {
		Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
		intent.putExtra(ContactsContract.Intents.Insert.NAME, displayName);

		if (sipUri != null && sipUri.startsWith("sip:")) {
			sipUri = sipUri.substring(4);
		}

		ArrayList<ContentValues> data = new ArrayList<ContentValues>();
		ContentValues sipAddressRow = new ContentValues();
		sipAddressRow.put(Contacts.Data.MIMETYPE, SipAddress.CONTENT_ITEM_TYPE);
		sipAddressRow.put(SipAddress.SIP_ADDRESS, sipUri);
		data.add(sipAddressRow);
		intent.putParcelableArrayListExtra(Insert.DATA, data);

		return intent;
	}

	public static Intent prepareEditContactIntentWithSipAddress(int id, String sipUri) {
		Intent intent = new Intent(Intent.ACTION_EDIT, Contacts.CONTENT_URI);
		Uri contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, id);
		intent.setData(contactUri);

		ArrayList<ContentValues> data = new ArrayList<ContentValues>();
		ContentValues sipAddressRow = new ContentValues();
		sipAddressRow.put(Contacts.Data.MIMETYPE, SipAddress.CONTENT_ITEM_TYPE);
		sipAddressRow.put(SipAddress.SIP_ADDRESS, sipUri);
		data.add(sipAddressRow);
		intent.putParcelableArrayListExtra(Insert.DATA, data);

		return intent;
	}

	@SuppressWarnings("deprecation")
	public static Notification createMissedCallNotification(Context context, String title, String text, PendingIntent intent) {
		Notification notif = new Notification.Builder(context)
		.setContentTitle(title)
		.setContentText(text)
		.setContentIntent(intent)
		.setSmallIcon(R.drawable.call_status_missed)
		.setAutoCancel(true)
		.setDefaults(Notification.DEFAULT_LIGHTS | Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE)
		.setWhen(System.currentTimeMillis()).getNotification();

		return notif;
	}

	@SuppressWarnings("deprecation")
	public static Notification createSimpleNotification(Context context, String title, String text, PendingIntent intent) {
		Notification notif = new Notification.Builder(context)
		.setContentTitle(title)
		.setContentText(text)
		.setContentIntent(intent)
		.setSmallIcon(R.drawable.linphone_logo)
		.setAutoCancel(true)
		.setDefaults(Notification.DEFAULT_LIGHTS | Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE)
		.setWhen(System.currentTimeMillis()).getNotification();

		return notif;
	}

	@SuppressWarnings("deprecation")
	public static void setTextAppearance(TextView textview, Context context, int style) {
		textview.setTextAppearance(context, style);
	}

	public static void scheduleAlarm(AlarmManager alarmManager, int type, long triggerAtMillis, PendingIntent operation) {
		alarmManager.set(type, triggerAtMillis, operation);
	}
}
