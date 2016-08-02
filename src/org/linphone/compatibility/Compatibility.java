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
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/
import java.util.List;

import org.linphone.mediastream.Version;

import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Html;
import android.text.Spanned;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
/**
 * @author Sylvain Berfini
 */
public class Compatibility {
	public static Cursor getContactsCursor(ContentResolver cr, List<String> contactsId) {
		if (Version.sdkAboveOrEqual(Version.API09_GINGERBREAD_23)) {
			return ApiNinePlus.getContactsCursor(cr, null, contactsId);
		} else {
			return ApiFivePlus.getContactsCursor(cr, contactsId);
		}
	}
	
	public static Notification createSimpleNotification(Context context, String title, String text, PendingIntent intent) {
		Notification notif = null;
		
		if (Version.sdkAboveOrEqual(Version.API21_LOLLIPOP_50)) {
			return ApiTwentyOnePlus.createSimpleNotification(context, title, text, intent);
		} else if (Version.sdkAboveOrEqual(Version.API16_JELLY_BEAN_41)) {
			notif = ApiSixteenPlus.createSimpleNotification(context, title, text, intent);
		} else if (Version.sdkAboveOrEqual(Version.API11_HONEYCOMB_30)) {
			notif = ApiElevenPlus.createSimpleNotification(context, title, text, intent);
		} else {
			notif = ApiFivePlus.createSimpleNotification(context, title, text, intent);
		}
		return notif;
	}
	
	public static Notification createMessageNotification(Context context, int msgCount, String msgSender, String msg, Bitmap contactIcon, PendingIntent intent) {
		Notification notif = null;
		String title;
		if (msgCount == 1) {
			title = "Unread message from %s".replace("%s", msgSender);
		} else {
			title = "%i unread messages".replace("%i", String.valueOf(msgCount));
		}
		
		if (Version.sdkAboveOrEqual(Version.API21_LOLLIPOP_50)) {
			return ApiTwentyOnePlus.createMessageNotification(context, msgCount, msgSender, msg, contactIcon, intent);
		} else if (Version.sdkAboveOrEqual(Version.API16_JELLY_BEAN_41)) {
			notif = ApiSixteenPlus.createMessageNotification(context, msgCount, msgSender, msg, contactIcon, intent);
		} else if (Version.sdkAboveOrEqual(Version.API11_HONEYCOMB_30)) {
			notif = ApiElevenPlus.createMessageNotification(context, msgCount, msgSender, msg, contactIcon, intent);
		} else {
			notif = ApiFivePlus.createMessageNotification(context, title, msg, intent);
		}
		return notif;
	}
	
	public static Notification createInCallNotification(Context context, String title, String msg, int iconID, Bitmap contactIcon, String contactName, PendingIntent intent) {
		Notification notif = null;
		
		if (Version.sdkAboveOrEqual(Version.API21_LOLLIPOP_50)) {
			return ApiTwentyOnePlus.createInCallNotification(context, title, msg, iconID, contactIcon, contactName, intent);
		} else if (Version.sdkAboveOrEqual(Version.API16_JELLY_BEAN_41)) {
			notif = ApiSixteenPlus.createInCallNotification(context, title, msg, iconID, contactIcon, contactName, intent);
		} else if (Version.sdkAboveOrEqual(Version.API11_HONEYCOMB_30)) {
			notif = ApiElevenPlus.createInCallNotification(context, title, msg, iconID, contactIcon, contactName, intent);
		} else {
			notif = ApiFivePlus.createInCallNotification(context, title, msg, iconID, intent);
		}
		return notif;
	}

	public static Notification createNotification(Context context, String title, String message, int icon, int iconLevel, Bitmap largeIcon, PendingIntent intent, boolean isOngoingEvent,int priority) {
		if (Version.sdkAboveOrEqual(Version.API21_LOLLIPOP_50)) {
			return ApiTwentyOnePlus.createNotification(context, title, message, icon, iconLevel, largeIcon, intent, isOngoingEvent,priority);
		} else if (Version.sdkAboveOrEqual(Version.API16_JELLY_BEAN_41)) {
			return ApiSixteenPlus.createNotification(context, title, message, icon, iconLevel, largeIcon, intent, isOngoingEvent,priority);
		} else if (Version.sdkAboveOrEqual(Version.API11_HONEYCOMB_30)) {
			return ApiElevenPlus.createNotification(context, title, message, icon, iconLevel, largeIcon, intent, isOngoingEvent);
		} else {
			return ApiFivePlus.createNotification(context, title, message, icon, iconLevel, intent, isOngoingEvent);
		}
	}

	public static CompatibilityScaleGestureDetector getScaleGestureDetector(Context context, CompatibilityScaleGestureListener listener) {
		if (Version.sdkAboveOrEqual(Version.API08_FROYO_22)) {
			CompatibilityScaleGestureDetector csgd = new CompatibilityScaleGestureDetector(context);
			csgd.setOnScaleListener(listener);
			return csgd;
		}
		return null;
	}

	public static void copyTextToClipboard(Context context, String msg) {
		if(Version.sdkAboveOrEqual(Version.API11_HONEYCOMB_30)) {
			ApiElevenPlus.copyTextToClipboard(context, msg);
		} else {
		    ApiFivePlus.copyTextToClipboard(context, msg);
		}
	}

	public static void removeGlobalLayoutListener(ViewTreeObserver viewTreeObserver, OnGlobalLayoutListener keyboardListener) {
		if (Version.sdkAboveOrEqual(Version.API16_JELLY_BEAN_41)) {
			ApiSixteenPlus.removeGlobalLayoutListener(viewTreeObserver, keyboardListener);
		} else {
			ApiFivePlus.removeGlobalLayoutListener(viewTreeObserver, keyboardListener);
		}
	}
	
	public static void hideNavigationBar(Activity activity) {
		if (Version.sdkAboveOrEqual(Version.API14_ICE_CREAM_SANDWICH_40)) {
			ApiFourteenPlus.hideNavigationBar(activity);
		}
	}
	
	public static void showNavigationBar(Activity activity) {
		if (Version.sdkAboveOrEqual(Version.API14_ICE_CREAM_SANDWICH_40)) {
			ApiFourteenPlus.showNavigationBar(activity);
		}
	}
	
	public static void setAudioManagerInCallMode(AudioManager manager) {
		if (Version.sdkAboveOrEqual(Version.API11_HONEYCOMB_30)) {
			ApiElevenPlus.setAudioManagerInCallMode(manager);
		} else {
			ApiFivePlus.setAudioManagerInCallMode(manager);
		}
	}
	
	public static String getAudioManagerEventForBluetoothConnectionStateChangedEvent() {
		if (Version.sdkAboveOrEqual(Version.API14_ICE_CREAM_SANDWICH_40)) {
			return ApiFourteenPlus.getAudioManagerEventForBluetoothConnectionStateChangedEvent();
		} else {
			return ApiEightPlus.getAudioManagerEventForBluetoothConnectionStateChangedEvent();
		}
	}

	public static boolean canDrawOverlays(Context context) {
		if (Version.sdkAboveOrEqual(Version.API23_MARSHMALLOW_60)) {
			return Settings.canDrawOverlays(context);
		}
		return true;
	}

	@SuppressWarnings("deprecation")
	public static boolean isScreenOn(PowerManager pm) {
		if (Version.sdkAboveOrEqual(20)) {
			return pm.isInteractive();
		} else {
			return pm.isScreenOn();
		}
	}

	@SuppressWarnings("deprecation")
	public static Spanned fromHtml(String text) {
		if (Version.sdkAboveOrEqual(Version.API24_NOUGAT_70)) {
		    return Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY);
		} else {
		    return Html.fromHtml(text);
		}
	}
}
