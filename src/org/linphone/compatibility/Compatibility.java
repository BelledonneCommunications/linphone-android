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
import java.util.ArrayList;
import java.util.List;

import org.linphone.Contact;
import org.linphone.core.LinphoneAddress;
import org.linphone.mediastream.Version;

import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.net.Uri;
import android.preference.Preference;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
/**
 * @author Sylvain Berfini
 */
public class Compatibility {
	public static void overridePendingTransition(Activity activity, int idAnimIn, int idAnimOut) {
		if (Version.sdkAboveOrEqual(Version.API05_ECLAIR_20)) {
			ApiFivePlus.overridePendingTransition(activity, idAnimIn, idAnimOut);
		}
	}
	
	public static Intent prepareAddContactIntent(String displayName, String sipUri) {
		if (Version.sdkAboveOrEqual(Version.API11_HONEYCOMB_30)) {
			return ApiElevenPlus.prepareAddContactIntent(displayName, sipUri);
		} else {
			return ApiFivePlus.prepareAddContactIntent(displayName, sipUri);
		}
	}
	
	public static Intent prepareEditContactIntent(int id) {
		if (Version.sdkAboveOrEqual(Version.API05_ECLAIR_20)) {
			return ApiFivePlus.prepareEditContactIntent(id);
		}
		return null;
	}
	
	public static Intent prepareEditContactIntentWithSipAddress(int id, String sipAddress) {
		if (Version.sdkAboveOrEqual(Version.API11_HONEYCOMB_30)) {
			return ApiElevenPlus.prepareEditContactIntentWithSipAddress(id, sipAddress);
		} else {
			return ApiFivePlus.prepareEditContactIntent(id);	
		}
	}
	
	public static List<String> extractContactNumbersAndAddresses(String id, ContentResolver cr) {
		if (Version.sdkAboveOrEqual(Version.API09_GINGERBREAD_23)) {
			return ApiNinePlus.extractContactNumbersAndAddresses(id, cr);
		} else {
			return ApiFivePlus.extractContactNumbersAndAddresses(id, cr);
		}
	}

	public static List<String> extractContactImAddresses(String id, ContentResolver cr) {
		if (Version.sdkAboveOrEqual(Version.API09_GINGERBREAD_23)) {
			return ApiFivePlus.extractContactNumbersAndAddresses(id, cr);
		} else {
			return null;
		}
	}
	
	public static Cursor getContactsCursor(ContentResolver cr, List<String> contactsId) {
		if (Version.sdkAboveOrEqual(Version.API09_GINGERBREAD_23)) {
			return ApiNinePlus.getContactsCursor(cr, null, contactsId);
		} else {
			return ApiFivePlus.getContactsCursor(cr, contactsId);
		}
	}
	
	public static Cursor getContactsCursor(ContentResolver cr, String search, List<String> contactsId) {
		if (Version.sdkAboveOrEqual(Version.API09_GINGERBREAD_23)) {
			return ApiNinePlus.getContactsCursor(cr, search, contactsId);
		} else {
			return ApiFivePlus.getContactsCursor(cr, contactsId);
		}
	}

	public static Cursor getSIPContactsCursor(ContentResolver cr, List<String> contactsId) {
		if (Version.sdkAboveOrEqual(Version.API09_GINGERBREAD_23)) {
			return ApiNinePlus.getSIPContactsCursor(cr, null, contactsId);
		} else {
			return ApiFivePlus.getSIPContactsCursor(cr, contactsId);
		}
	}
	
	public static Cursor getSIPContactsCursor(ContentResolver cr, String search, List<String> contactsId) {
		if (Version.sdkAboveOrEqual(Version.API09_GINGERBREAD_23)) {
			return ApiNinePlus.getSIPContactsCursor(cr, search, contactsId);
		} else {
			return ApiFivePlus.getSIPContactsCursor(cr, contactsId);
		}
	}

	public static Cursor getImContactsCursor(ContentResolver cr) {
		return ApiFivePlus.getSIPContactsCursor(cr,null);
	}
	
	public static int getCursorDisplayNameColumnIndex(Cursor cursor) {
		if (Version.sdkAboveOrEqual(Version.API05_ECLAIR_20)) {
			return ApiFivePlus.getCursorDisplayNameColumnIndex(cursor);
		}
		return -1;
	}

	public static Contact getContact(ContentResolver cr, Cursor cursor, int position) {
		if (Version.sdkAboveOrEqual(Version.API05_ECLAIR_20)) {
			return ApiFivePlus.getContact(cr, cursor, position);
		}
		return null;
	}

	public static InputStream getContactPictureInputStream(ContentResolver cr, String id) {
		if (Version.sdkAboveOrEqual(Version.API05_ECLAIR_20)) {
			return ApiFivePlus.getContactPictureInputStream(cr, id);
		}
		return null;
	}
	
	public static Uri findUriPictureOfContactAndSetDisplayName(LinphoneAddress address, ContentResolver cr) {
		if (Version.sdkAboveOrEqual(Version.API09_GINGERBREAD_23)) {
			return ApiNinePlus.findUriPictureOfContactAndSetDisplayName(address, cr);
		} else {
			return ApiFivePlus.findUriPictureOfContactAndSetDisplayName(address, cr);
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

	public static String refreshContactName(ContentResolver cr, String id) {
		if (Version.sdkAboveOrEqual(Version.API05_ECLAIR_20)) {
			return ApiFivePlus.refreshContactName(cr, id);
		}
		return null;
	}

	public static CompatibilityScaleGestureDetector getScaleGestureDetector(Context context, CompatibilityScaleGestureListener listener) {
		if (Version.sdkAboveOrEqual(Version.API08_FROYO_22)) {
			CompatibilityScaleGestureDetector csgd = new CompatibilityScaleGestureDetector(context);
			csgd.setOnScaleListener(listener);
			return csgd;
		}
		return null;
	}
	
	
	public static void setPreferenceChecked(Preference preference, boolean checked) {
		if (Version.sdkAboveOrEqual(Version.API14_ICE_CREAM_SANDWICH_40)) {
			ApiFourteenPlus.setPreferenceChecked(preference, checked);
		} else {
			ApiFivePlus.setPreferenceChecked(preference, checked);
		}
	}
	
	public static boolean isPreferenceChecked(Preference preference) {
		if (Version.sdkAboveOrEqual(Version.API14_ICE_CREAM_SANDWICH_40)) {
			return ApiFourteenPlus.isPreferenceChecked(preference);
		} else {
			return ApiFivePlus.isPreferenceChecked(preference);
		}
	}
	
	public static void initPushNotificationService(Context context) {
		if (Version.sdkAboveOrEqual(Version.API08_FROYO_22)) {
			ApiEightPlus.initPushNotificationService(context);
		}
	}

	public static void copyTextToClipboard(Context context, String msg) {
		if(Version.sdkAboveOrEqual(Version.API11_HONEYCOMB_30)) {
			ApiElevenPlus.copyTextToClipboard(context, msg);
		} else {
		    ApiFivePlus.copyTextToClipboard(context, msg);
		}
	}
	
	public static void addSipAddressToContact(Context context, ArrayList<ContentProviderOperation> ops, String sipAddress) {
		if (Version.sdkAboveOrEqual(Version.API09_GINGERBREAD_23)) {
			ApiNinePlus.addSipAddressToContact(context, ops, sipAddress);
		} else {
			ApiFivePlus.addSipAddressToContact(context, ops, sipAddress);
		}
	}
	
	public static void addSipAddressToContact(Context context, ArrayList<ContentProviderOperation> ops, String sipAddress, String rawContactID) {
		if (Version.sdkAboveOrEqual(Version.API09_GINGERBREAD_23)) {
			ApiNinePlus.addSipAddressToContact(context, ops, sipAddress, rawContactID);
		} else {
			ApiFivePlus.addSipAddressToContact(context, ops, sipAddress, rawContactID);
		}
	}
	
	public static void updateSipAddressForContact(ArrayList<ContentProviderOperation> ops, String oldSipAddress, String newSipAddress, String contactID) {
		if (Version.sdkAboveOrEqual(Version.API09_GINGERBREAD_23)) {
			ApiNinePlus.updateSipAddressForContact(ops, oldSipAddress, newSipAddress, contactID);
		} else {
			ApiFivePlus.updateSipAddressForContact(ops, oldSipAddress, newSipAddress, contactID);
		}
	}
	
	public static void deleteSipAddressFromContact(ArrayList<ContentProviderOperation> ops, String oldSipAddress, String contactID) {
		if (Version.sdkAboveOrEqual(Version.API09_GINGERBREAD_23)) {
			ApiNinePlus.deleteSipAddressFromContact(ops, oldSipAddress, contactID);
		} else {
			ApiFivePlus.deleteSipAddressFromContact(ops, oldSipAddress, contactID);
		}
	}

	public static void deleteImAddressFromContact(ArrayList<ContentProviderOperation> ops, String oldSipAddress, String contactID) {
		ApiFivePlus.deleteSipAddressFromContact(ops, oldSipAddress, contactID);
	}

	//Linphone Contacts Tag
	public static void addLinphoneContactTag(Context context, ArrayList<ContentProviderOperation> ops, String newSipAddress, String rawContactId) {
		if (Version.sdkAboveOrEqual(Version.API09_GINGERBREAD_23)) {
			ApiNinePlus.addLinphoneContactTag(context, ops, newSipAddress, rawContactId);
		}
	}

	public static void updateLinphoneContactTag(Context context, ArrayList<ContentProviderOperation> ops, String newSipAddress, String oldSipAddress, String rawContactId) {
		if (Version.sdkAboveOrEqual(Version.API09_GINGERBREAD_23)) {
			ApiNinePlus.updateLinphoneContactTag(context, ops, newSipAddress, oldSipAddress, rawContactId);
		}
	}

	public static void deleteLinphoneContactTag(ArrayList<ContentProviderOperation> ops, String oldSipAddress, String rawContactId) {
		if (Version.sdkAboveOrEqual(Version.API09_GINGERBREAD_23)) {
			ApiNinePlus.deleteLinphoneContactTag(ops, oldSipAddress, rawContactId);
		}
	}

	public static void createLinphoneContactTag(Context context, ContentResolver contentResolver, Contact contact, String rawContactId) {
		if (Version.sdkAboveOrEqual(Version.API09_GINGERBREAD_23)) {
			ApiNinePlus.createLinphoneContactTag(context, contentResolver, contact, rawContactId);
		}
	}
	//End of Linphone Contact Tag

	public static void removeGlobalLayoutListener(ViewTreeObserver viewTreeObserver, OnGlobalLayoutListener keyboardListener) {
		if (Version.sdkAboveOrEqual(Version.API16_JELLY_BEAN_41)) {
			ApiSixteenPlus.removeGlobalLayoutListener(viewTreeObserver, keyboardListener);
		} else {
			ApiFivePlus.removeGlobalLayoutListener(viewTreeObserver, keyboardListener);
		}
	}
	
	public static void hideNavigationBar(Activity activity)
	{
		if (Version.sdkAboveOrEqual(Version.API14_ICE_CREAM_SANDWICH_40)) {
			ApiFourteenPlus.hideNavigationBar(activity);
		}
	}
	
	public static void showNavigationBar(Activity activity)
	{
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
}
