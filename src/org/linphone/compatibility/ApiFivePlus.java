package org.linphone.compatibility;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.linphone.Contact;
import org.linphone.R;
import org.linphone.core.LinphoneAddress;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.net.Uri;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.text.ClipboardManager;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;

/*
ApiFivePlus.java
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
@SuppressWarnings("deprecation")
@TargetApi(5)
public class ApiFivePlus {
	public static void overridePendingTransition(Activity activity, int idAnimIn, int idAnimOut) {
		activity.overridePendingTransition(idAnimIn, idAnimOut);
	}
	
	public static Intent prepareAddContactIntent(String displayName, String sipUri) {
		Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
		intent.putExtra(ContactsContract.Intents.Insert.NAME, displayName);
		
		// VoIP field not available, we store the address in the IM field
		intent.putExtra(ContactsContract.Intents.Insert.IM_HANDLE, sipUri);
		intent.putExtra(ContactsContract.Intents.Insert.IM_PROTOCOL, "sip");
		  
		return intent;
	}
	
	public static Intent prepareEditContactIntent(int id) {
		Intent intent = new Intent(Intent.ACTION_EDIT, Contacts.CONTENT_URI);
		Uri contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, id);
		intent.setData(contactUri);
		
		return intent;
	}
	
	public static Intent prepareEditContactIntentWithSipAddress(int id, String sipUri) {
		Intent intent = new Intent(Intent.ACTION_EDIT, Contacts.CONTENT_URI);
		Uri contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, id);
		intent.setData(contactUri);
		
		// VoIP field not available, we store the address in the IM field
		intent.putExtra(ContactsContract.Intents.Insert.IM_HANDLE, sipUri);
		intent.putExtra(ContactsContract.Intents.Insert.IM_PROTOCOL, "sip");
		
		return intent;
	}
	
	public static List<String> extractContactNumbersAndAddresses(String id, ContentResolver cr) {
		List<String> list = new ArrayList<String>();

		Uri uri = Data.CONTENT_URI;
		String[] projection = {ContactsContract.CommonDataKinds.Im.DATA};

		// Phone Numbers
		Cursor c = cr.query(Phone.CONTENT_URI, new String[] { Phone.NUMBER }, Phone.CONTACT_ID + " = " + id, null, null);
		if (c != null) {
	        while (c.moveToNext()) {
	            String number = c.getString(c.getColumnIndex(Phone.NUMBER));
	            list.add(number); 
	        }
	        c.close();
		}
		
		// SIP addresses
		String selection = new StringBuilder()
			.append(Data.CONTACT_ID).append(" =  ? AND ")
			.append(Data.MIMETYPE).append(" = '")
			.append(ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE)
			.append("' AND lower(")
			.append(ContactsContract.CommonDataKinds.Im.CUSTOM_PROTOCOL)
			.append(") = 'sip'")
			.toString();
		c = cr.query(uri, projection, selection, new String[]{id}, null);
		if (c != null) {
			int nbId = c.getColumnIndex(ContactsContract.CommonDataKinds.Im.DATA);
			while (c.moveToNext()) {
				list.add("sip:" + c.getString(nbId)); 
			}
			c.close();
		}

		return list;
	}
	
	public static Cursor getContactsCursor(ContentResolver cr) {
		String req = Data.MIMETYPE + " = '" + CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                + "' AND " + CommonDataKinds.Phone.NUMBER + " IS NOT NULL";
		
    	req += " OR (" + Contacts.Data.MIMETYPE + " = '" + CommonDataKinds.Im.CONTENT_ITEM_TYPE 
                + "' AND lower(" + CommonDataKinds.Im.CUSTOM_PROTOCOL + ") = 'sip')";
		
		return getGeneralContactCursor(cr, req, true);
	}

	public static Cursor getSIPContactsCursor(ContentResolver cr) {
		String req = null;
    	req = Contacts.Data.MIMETYPE + " = '" + CommonDataKinds.Im.CONTENT_ITEM_TYPE 
                + "' AND lower(" + CommonDataKinds.Im.CUSTOM_PROTOCOL + ") = 'sip'";
		
		return getGeneralContactCursor(cr, req, true);
	}
	
	private static Cursor getSIPContactCursor(ContentResolver cr, String id) {
		String req = null;
    	req = Contacts.Data.MIMETYPE + " = '" + CommonDataKinds.Im.CONTENT_ITEM_TYPE 
                + " AND lower(" + CommonDataKinds.Im.CUSTOM_PROTOCOL + ") = 'sip' AND "
                + android.provider.ContactsContract.CommonDataKinds.Im.DATA + " LIKE '" + id + "'";
		
		return getGeneralContactCursor(cr, req, false);
	}
	
	public static Cursor getGeneralContactCursor(ContentResolver cr, String select, boolean shouldGroupBy) {
		
		String[] projection = new String[] { Data.CONTACT_ID, Data.DISPLAY_NAME };
		
		String query = Data.DISPLAY_NAME + " IS NOT NULL AND (" + select + ")";
		Cursor cursor = cr.query(Data.CONTENT_URI, projection, query, null, Data.DISPLAY_NAME + " COLLATE NOCASE ASC");
		
		if (!shouldGroupBy || cursor == null) {
			return cursor;
		}
		
		MatrixCursor result = new MatrixCursor(cursor.getColumnNames());
		Set<String> groupBy = new HashSet<String>();
		while (cursor.moveToNext()) {
		    String name = cursor.getString(getCursorDisplayNameColumnIndex(cursor));
		    if (!groupBy.contains(name)) {
		    	groupBy.add(name);
		    	Object[] newRow = new Object[cursor.getColumnCount()];
		    	
		    	int contactID = cursor.getColumnIndex(Data.CONTACT_ID);
		    	int displayName = cursor.getColumnIndex(Data.DISPLAY_NAME);
		    	
		    	newRow[contactID] = cursor.getString(contactID);
		    	newRow[displayName] = cursor.getString(displayName);
		    	
		        result.addRow(newRow);
	    	}
	    }
		cursor.close();
		return result;
	}
	
	public static int getCursorDisplayNameColumnIndex(Cursor cursor) {
		return cursor.getColumnIndex(Data.DISPLAY_NAME);
	}

	public static Contact getContact(ContentResolver cr, Cursor cursor, int position) {
		try {
			cursor.moveToFirst();
			boolean success = cursor.move(position);
			if (!success)
				return null;
			
			String id = cursor.getString(cursor.getColumnIndex(Data.CONTACT_ID));
	    	String name = getContactDisplayName(cursor);
	        Uri photo = getContactPictureUri(id);
	        InputStream input = getContactPictureInputStream(cr, id);
	        
	        Contact contact;
	        if (input == null) {
	        	contact = new Contact(id, name);
	        }
	        else {
	        	Bitmap bm = null;
	        	try {
	        		bm = BitmapFactory.decodeStream(input);
	        	} catch (OutOfMemoryError oome) {}
	        	contact = new Contact(id, name, photo, bm);
	        }
	        
	        return contact;
		} catch (Exception e) {
			
		}
		return null;
	}
	
	public static InputStream getContactPictureInputStream(ContentResolver cr, String id) {
		Uri person = getContactPictureUri(id);
		return Contacts.openContactPhotoInputStream(cr, person);
	}
	
	private static String getContactDisplayName(Cursor cursor) {
		return cursor.getString(cursor.getColumnIndex(Data.DISPLAY_NAME));
	}
	
	private static Uri getContactPictureUri(String id) {
		return ContentUris.withAppendedId(Contacts.CONTENT_URI, Long.parseLong(id));
	}
	
	public static Uri findUriPictureOfContactAndSetDisplayName(LinphoneAddress address, ContentResolver cr) {
		String username = address.getUserName();
		String domain = address.getDomain();
		String sipUri = username + "@" + domain;
		
		Cursor cursor = getSIPContactCursor(cr, sipUri);
		Contact contact = getContact(cr, cursor, 0);
		if (contact != null && contact.getNumerosOrAddresses().contains(sipUri)) {
			address.setDisplayName(contact.getName());
			cursor.close();
			return contact.getPhotoUri();
		}

		cursor.close();
		return null;
	}

	public static String refreshContactName(ContentResolver cr, String id) {
		Cursor cursor = getGeneralContactCursor(cr, Data.CONTACT_ID + " = '" + id + "'", false);
		if (cursor != null && cursor.moveToFirst()) {
			String contactDisplayName = getContactDisplayName(cursor);
			cursor.close();
			return contactDisplayName;
		}
		
		cursor.close();
		return null;
	}
	
	public static Notification createMessageNotification(Context context, String title, String msg, PendingIntent intent) {
		Notification notif = new Notification();
		notif.icon = R.drawable.chat_icon_over;
		notif.iconLevel = 0;
		notif.when = System.currentTimeMillis();
		notif.flags &= Notification.FLAG_ONGOING_EVENT;
		
		notif.defaults |= Notification.DEFAULT_VIBRATE;
		notif.defaults |= Notification.DEFAULT_SOUND;
		notif.defaults |= Notification.DEFAULT_LIGHTS;
		
		notif.setLatestEventInfo(context, title, msg, intent);
		
		return notif;
	}
	
	public static Notification createInCallNotification(Context context,
			String title, String msg, int iconID, PendingIntent intent) {
		Notification notif = new Notification();
		notif.icon = iconID;
		notif.iconLevel = 0;
		notif.when = System.currentTimeMillis();
		notif.flags &= Notification.FLAG_ONGOING_EVENT;
		
		notif.setLatestEventInfo(context, title, msg, intent);

		return notif;
	}

	public static void setPreferenceChecked(Preference preference, boolean checked) {
		((CheckBoxPreference) preference).setChecked(checked);
	}
	
	public static boolean isPreferenceChecked(Preference preference) {
		return ((CheckBoxPreference) preference).isChecked();
	}

	public static void copyTextToClipboard(Context context, String msg) {
	    ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
	    clipboard.setText(msg);
	}
	
	public static void addSipAddressToContact(Context context, ArrayList<ContentProviderOperation> ops, String sipAddress) {
		ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)        
	        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
	        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE)
	        .withValue(ContactsContract.CommonDataKinds.Im.DATA, sipAddress)
	        .withValue(ContactsContract.CommonDataKinds.Im.TYPE,  ContactsContract.CommonDataKinds.Im.TYPE_CUSTOM)
	        .withValue(ContactsContract.CommonDataKinds.Im.LABEL, context.getString(R.string.addressbook_label))
	        .build()
	    );
	}
	
	public static void addSipAddressToContact(Context context, ArrayList<ContentProviderOperation> ops, String sipAddress, String rawContactID) {
		ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)         
		    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactID)       
	        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE)
	        .withValue(ContactsContract.CommonDataKinds.Im.DATA, sipAddress)
	        .withValue(ContactsContract.CommonDataKinds.Im.TYPE,  ContactsContract.CommonDataKinds.Im.TYPE_CUSTOM)
	        .withValue(ContactsContract.CommonDataKinds.Im.LABEL, context.getString(R.string.addressbook_label))
	        .build()
	    );
	}
	
	public static void updateSipAddressForContact(ArrayList<ContentProviderOperation> ops, String oldSipAddress, String newSipAddress, String contactID) {
		String select = ContactsContract.Data.CONTACT_ID + "=? AND " 
				+ ContactsContract.Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE +  "' AND " 
				+ ContactsContract.CommonDataKinds.Im.DATA + "=?"; 
		String[] args = new String[] { String.valueOf(contactID), oldSipAddress };   
		
        ops.add(ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI) 
    		.withSelection(select, args) 
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.Im.DATA, newSipAddress)
            .build()
        );
	}
	
	public static void deleteSipAddressFromContact(ArrayList<ContentProviderOperation> ops, String oldSipAddress, String contactID) {
		String select = ContactsContract.Data.CONTACT_ID + "=? AND " 
				+ ContactsContract.Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE +  "' AND " 
				+ ContactsContract.CommonDataKinds.Im.DATA + "=?"; 
		String[] args = new String[] { String.valueOf(contactID), oldSipAddress };   
		
        ops.add(ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI) 
    		.withSelection(select, args) 
            .build()
        );
	}

	public static void removeGlobalLayoutListener(ViewTreeObserver viewTreeObserver, OnGlobalLayoutListener keyboardListener) {
		viewTreeObserver.removeGlobalOnLayoutListener(keyboardListener);
	}

	public static void setAudioManagerInCallMode(AudioManager manager) {
		/* Do not use MODE_IN_CALL, because it is reserved to GSM. This is causing conflicts on audio system resulting in silenced audio.*/
		//manager.setMode(AudioManager.MODE_IN_CALL);
	}

	public static Notification createNotification(Context context, String title, String message, int icon, int level, PendingIntent intent) {
		Notification notif = new Notification();
		notif.icon = icon;
		notif.iconLevel = level;
		notif.when = System.currentTimeMillis();
		notif.flags |= Notification.FLAG_ONGOING_EVENT;
		notif.setLatestEventInfo(context, title, message, intent);
		
		return notif;
	}
}
