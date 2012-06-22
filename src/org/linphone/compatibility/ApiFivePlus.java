package org.linphone.compatibility;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.linphone.mediastream.Version;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Contacts.Data;
import android.provider.ContactsContract.Intents.Insert;

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
public class ApiFivePlus {
	public static void overridePendingTransition(Activity activity, int idAnimIn, int idAnimOut) {
		activity.overridePendingTransition(idAnimIn, idAnimOut);
	}
	
	public static Intent prepareAddContactIntent(String displayName, String sipUri) {
		Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
		intent.putExtra(ContactsContract.Intents.Insert.NAME, displayName);
		
		if (Version.sdkAboveOrEqual(Version.API09_GINGERBREAD_23)) {
			ArrayList<ContentValues> data = new ArrayList<ContentValues>();
			ContentValues sipAddressRow = new ContentValues();
			sipAddressRow.put(Data.MIMETYPE, SipAddress.CONTENT_ITEM_TYPE);
			sipAddressRow.put(SipAddress.SIP_ADDRESS, sipUri);
			data.add(sipAddressRow);
			intent.putParcelableArrayListExtra(Insert.DATA, data);
		} else {
			// VoIP field not available, we store the address in the IM field
			intent.putExtra(ContactsContract.Intents.Insert.IM_HANDLE, sipUri);
			intent.putExtra(ContactsContract.Intents.Insert.IM_PROTOCOL, "sip");
		}
		  
		return intent;
	}
	
	public static List<String> extractContactNumbersAndAddresses(String id, ContentResolver cr) {
		List<String> list = new ArrayList<String>();

		Uri uri = ContactsContract.Data.CONTENT_URI;
		String[] projection = {ContactsContract.CommonDataKinds.Im.DATA};

		// SIP addresses
		if (Version.sdkAboveOrEqual(Version.API09_GINGERBREAD_23)) {
			String selection = new StringBuilder()
				.append(ContactsContract.Data.CONTACT_ID)
				.append(" = ? AND ")
				.append(ContactsContract.Data.MIMETYPE)
				.append(" = '")
				.append(ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE)
				.append("'")
				.toString();
			projection = new String[] {ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS};
			Cursor c = cr.query(uri, projection, selection, new String[]{id}, null);

			int nbId = c.getColumnIndex(ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS);
			while (c.moveToNext()) {
				list.add("sip:" + c.getString(nbId)); 
			}
			c.close();
		} else {
			String selection = new StringBuilder()
				.append(ContactsContract.Data.CONTACT_ID).append(" =  ? AND ")
				.append(ContactsContract.Data.MIMETYPE).append(" = '")
				.append(ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE)
				.append("' AND lower(")
				.append(ContactsContract.CommonDataKinds.Im.CUSTOM_PROTOCOL)
				.append(") = 'sip'")
				.toString();
			Cursor c = cr.query(uri, projection, selection, new String[]{id}, null);

			int nbId = c.getColumnIndex(ContactsContract.CommonDataKinds.Im.DATA);
			while (c.moveToNext()) {
				list.add("sip:" + c.getString(nbId)); 
			}
			c.close();
		}
		
		// Phone Numbers
		Cursor c = cr.query(Phone.CONTENT_URI, null, Phone.CONTACT_ID + " = " + id, null, null);
        while (c.moveToNext()) {
            String number = c.getString(c.getColumnIndex(Phone.NUMBER));
            list.add(number); 
        }
        c.close();

		return list;
	}
	
	public static Cursor getContactsCursor(ContentResolver cr) {
		return cr.query(ContactsContract.Contacts.CONTENT_URI, null, ContactsContract.Contacts.DISPLAY_NAME + " IS NOT NULL", null, ContactsContract.Contacts.DISPLAY_NAME + " ASC");
	}
	
	public static String getContactDisplayName(Cursor cursor) {
		return cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
	}
	
	public static Uri getContactPictureUri(Cursor cursor, String id) {
		Uri person = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, Long.parseLong(id));
        return Uri.withAppendedPath(person, ContactsContract.Contacts.Photo.CONTENT_DIRECTORY);
	}
	
	public static InputStream getContactPictureInputStream(ContentResolver cr, String id) {
		Uri person = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, Long.parseLong(id));
		return ContactsContract.Contacts.openContactPhotoInputStream(cr, person);
	}
	
	public static int getCursorDisplayNameColumnIndex(Cursor cursor) {
		return cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);
	}
}
