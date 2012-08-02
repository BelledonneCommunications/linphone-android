package org.linphone.compatibility;
/*
Compatibility.java
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
import java.io.InputStream;
import java.util.List;

import org.linphone.Contact;
import org.linphone.R;
import org.linphone.core.LinphoneAddress;
import org.linphone.mediastream.Version;

import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.Display;
/**
 * @author Sylvain Berfini
 */
public class Compatibility {
	public static void overridePendingTransition(Activity activity, int idAnimIn, int idAnimOut) {
		if (Version.sdkAboveOrEqual(5)) {
			ApiFivePlus.overridePendingTransition(activity, idAnimIn, idAnimOut);
		}
	}
	
	public static Intent prepareAddContactIntent(String displayName, String sipUri) {
		if (Version.sdkAboveOrEqual(5)) {
			return ApiFivePlus.prepareAddContactIntent(displayName, sipUri);
		}
		return null;
	}
	
	public static Intent prepareEditContactIntent(int id) {
		if (Version.sdkAboveOrEqual(5)) {
			return ApiFivePlus.prepareEditContactIntent(id);
		}
		return null;
	}
	
	public static List<String> extractContactNumbersAndAddresses(String id, ContentResolver cr) {
		if (Version.sdkAboveOrEqual(5)) {
			return ApiFivePlus.extractContactNumbersAndAddresses(id, cr);
		}
		return null;
	}
	
	public static Cursor getContactsCursor(ContentResolver cr) {
		if (Version.sdkAboveOrEqual(5)) {
			return ApiFivePlus.getContactsCursor(cr);
		}
		return null;
	}
	
	public static Cursor getSIPContactsCursor(ContentResolver cr) {
		if (Version.sdkAboveOrEqual(5)) {
			return ApiFivePlus.getSIPContactsCursor(cr);
		}
		return null;
	}
	
	public static int getCursorDisplayNameColumnIndex(Cursor cursor) {
		if (Version.sdkAboveOrEqual(5)) {
			return ApiFivePlus.getCursorDisplayNameColumnIndex(cursor);
		}
		return -1;
	}

	public static Contact getContact(ContentResolver cr, Cursor cursor, int position) {
		if (Version.sdkAboveOrEqual(5)) {
			return ApiFivePlus.getContact(cr, cursor, position);
		}
		return null;
	}

	public static InputStream getContactPictureInputStream(ContentResolver cr, String id) {
		if (Version.sdkAboveOrEqual(5)) {
			return ApiFivePlus.getContactPictureInputStream(cr, id);
		}
		return null;
	}
	
	public static Uri findUriPictureOfContactAndSetDisplayName(LinphoneAddress address, ContentResolver cr) {
		if (Version.sdkAboveOrEqual(5)) {
			return ApiFivePlus.findUriPictureOfContactAndSetDisplayName(address, cr);
		}
		return null;
	}
	
	public static Notification createMessageNotification(Context context, int msgCount, String msgSender, String msg, Bitmap contactIcon, PendingIntent intent) {
		Notification notif = null;
		String title;
		if (msgCount == 1) {
			title = "Unread message from %s".replace("%s", msgSender);
		} else {
			title = "%i unread messages".replace("%i", String.valueOf(msgCount));
		}
		
		if (Version.sdkAboveOrEqual(16)) {
			notif = ApiSixteenPlus.createMessageNotification(context, msgCount, msgSender, msg, contactIcon, intent);
		} else {
			notif = new Notification();
			notif.icon = R.drawable.chat_icon_over;
			notif.iconLevel = 0;
			notif.when = System.currentTimeMillis();
			notif.flags &= Notification.FLAG_ONGOING_EVENT;
			
			notif.defaults |= Notification.DEFAULT_VIBRATE;
			notif.defaults |= Notification.DEFAULT_SOUND;
			notif.defaults |= Notification.DEFAULT_LIGHTS;
			
			notif.setLatestEventInfo(context, title, msg, intent);
		}
		return notif;
	}
	
	public static Notification createInCallNotification(Context context, String title, String msg, int iconID, Bitmap contactIcon, String contactName, PendingIntent intent) {
		Notification notif = null;
		
		if (Version.sdkAboveOrEqual(16)) {
			notif = ApiSixteenPlus.createInCallNotification(context, title, msg, iconID, contactIcon, contactName, intent);
		} else {
			notif = new Notification();
			notif.icon = iconID;
			notif.iconLevel = 0;
			notif.when = System.currentTimeMillis();
			notif.flags &= Notification.FLAG_ONGOING_EVENT;
			
			notif.setLatestEventInfo(context, title, msg, intent);
		}
		return notif;
	}

	public static String refreshContactName(ContentResolver cr, String id) {
		if (Version.sdkAboveOrEqual(5)) {
			return ApiFivePlus.refreshContactName(cr, id);
		} else {
			//TODO
		}
		return null;
	}

	public static int getRotation(Display display) {
		if (Version.sdkStrictlyBelow(8)) {
			return ApiFivePlus.getRotation(display);
		} else {
			return ApiEightPlus.getRotation(display);
		}
	}
}
