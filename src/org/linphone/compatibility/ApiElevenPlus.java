package org.linphone.compatibility;

import java.util.ArrayList;

import org.linphone.R;
import org.linphone.mediastream.Log;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents.Insert;

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

	public static void copyTextToClipboard(Context context, String msg) {
		ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE); 
	    ClipData clip = android.content.ClipData.newPlainText("Message", msg);
	    clipboard.setPrimaryClip(clip);
	}

	public static void setAudioManagerInCallMode(AudioManager manager) {
		if (manager.getMode() == AudioManager.MODE_IN_COMMUNICATION) {
			Log.w("---AudioManager: already in MODE_IN_COMMUNICATION, skipping..."); 
			return;
		}
		Log.d("---AudioManager: set mode to MODE_IN_COMMUNICATION");
		manager.setMode(AudioManager.MODE_IN_COMMUNICATION);
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
}
