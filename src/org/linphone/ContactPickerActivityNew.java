/*
ContactPickerActivity.java
Copyright (C) 2010  Belledonne Communications, Grenoble, France

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
package org.linphone;

import java.util.ArrayList;
import java.util.List;

import org.linphone.mediastream.Version;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.text.TextUtils;


/**
 * Activity for retrieving a phone number / SIP address to call.
 * <br />
 *
 * The cinematic is:
 * <ul>
 * <li>Select contact (either through native or custom way)</li>
 * <li>Select phone number or SIP address
 * <li>Back to dialer</li>
 * </ul>
 * 
 * @author Guillaume Beraudo
 *
 */
public class ContactPickerActivityNew extends AbstractContactPickerActivityNew {



	@Override
	public Uri getPhotoUri(String id) {
		return retrievePhotoUriAndSetDisplayName(getContentResolver(), Long.parseLong(id));
	}

	private static Uri retrievePhotoUriAndSetDisplayName(ContentResolver resolver, long id) {
		Uri contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, id);
		Uri photoUri = Uri.withAppendedPath(contactUri, Contacts.Photo.CONTENT_DIRECTORY);
		if (photoUri == null) {
			return null;
		}
		if (ContactHelper.testPhotoUri(resolver, photoUri, ContactsContract.CommonDataKinds.Photo.PHOTO)) {
			return photoUri;
		}
		return null;
	}

	protected List<String> extractPhones(String id) {
		List<String> list = new ArrayList<String>();
		Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
		String[] projection = {ContactsContract.CommonDataKinds.Phone.NUMBER};
		String selection = ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?";
		String[] selArgs = new String[] {id};
		Cursor c = this.getContentResolver().query(uri, projection, selection, selArgs, null);

		int nbId = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);

		while (c.moveToNext()) {
			list.add(c.getString(nbId)); 
		}

		c.close();

		return list;
	}

	protected List<String> extractSipNumbers(String id) {
		List<String> list = new ArrayList<String>();
		Uri uri = ContactsContract.Data.CONTENT_URI;
		String[] projection = {ContactsContract.CommonDataKinds.Im.DATA};
		String selection = new StringBuilder()
			.append(ContactsContract.Data.CONTACT_ID).append(" =  ? AND ")
			.append(ContactsContract.Data.MIMETYPE).append(" = '")
			.append(ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE)
			.append("' AND lower(")
			.append(ContactsContract.CommonDataKinds.Im.CUSTOM_PROTOCOL)
			.append(") = 'sip'")
			.toString();
		Cursor c = getContentResolver().query(uri, projection, selection, new String[]{id}, null);

		int nbId = c.getColumnIndex(ContactsContract.CommonDataKinds.Im.DATA);
		while (c.moveToNext()) {
			list.add("sip:" + c.getString(nbId)); 
		}
		c.close();
		

		// Using the SIP contact field added in SDK 9
		if (Version.sdkAboveOrEqual(Version.API09_GINGERBREAD_23)) {
			selection = new StringBuilder()
				.append(ContactsContract.Data.CONTACT_ID)
				.append(" = ? AND ")
				.append(ContactsContract.Data.MIMETYPE)
				.append(" = '")
				.append(ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE)
				.append("'")
				.toString();
			projection = new String[] {ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS};
			c = this.getContentResolver().query(uri, projection, selection, new String[]{id}, null);

			nbId = c.getColumnIndex(ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS);
			while (c.moveToNext()) {
				list.add("sip:" + c.getString(nbId)); 
			}
			c.close();
		}

		return list;
	}


	@Override
	protected void onNewIntent(Intent intent) {
		// Launch the native contact picker here in spite of onResume
		// in order to avoid a loop that sometime occurs on HTC phones.
		if (useNativePicker) {
			Uri uri = ContactsContract.Contacts.CONTENT_URI;
			//ContactsContract.CommonDataKinds.Phone.CONTENT_URI
			startActivityForResult(new Intent(Intent.ACTION_PICK, uri),	0);
		}
		super.onNewIntent(intent);
	}

	protected void onActivityResult(int reqCode, int resultCode, Intent intent) {
		// If using native picker
		if (reqCode == 0) {
			if (resultCode == RESULT_OK) {
				String id = intent.getData().getLastPathSegment();
				String contactName = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
				if (contactName == null) {
					contactName = retrieveContactName(id);
				}
				choosePhoneNumberAndDial(contactName, id);
			}
		}

		((ContactPicked) getParent()).goToDialer();
	}


	private String retrieveContactName(String id) {
		//Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
		Uri uri = ContactsContract.Contacts.CONTENT_URI;
		String[] projection = new String[] {ContactsContract.Contacts.DISPLAY_NAME};
		String selection = ContactsContract.Contacts._ID + " = ?";
		String[] selArgs = new String[] {id};
		Cursor c = this.getContentResolver().query(uri,	projection, selection, selArgs, null);

		String name = "";
		if (c.moveToFirst()) {
			name = c.getString(c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)); 
		}
		c.close();

		return name;
	}



	public Cursor runQuery(CharSequence constraint) {
		// Run query
		Uri uri = ContactsContract.Contacts.CONTENT_URI;
		String[] projection = new String[] {
				ContactsContract.Contacts._ID,
				ContactsContract.Contacts.DISPLAY_NAME
		};
		String selection =
			ContactsContract.Contacts.IN_VISIBLE_GROUP + " = '1' and "
			+ ContactsContract.Contacts.HAS_PHONE_NUMBER + " = '1'";
		String[] selectionArgs = null;
		if (!TextUtils.isEmpty(constraint)) {
			// FIXME SQL injection - Android doesn't accept '?' in like queries 
			selection += " and " + ContactsContract.Contacts.DISPLAY_NAME + " like '%"+mcontactFilter.getText()+"%'";
		}

		String sortOrder = ContactsContract.Contacts.DISPLAY_NAME + " COLLATE LOCALIZED ASC";

		return managedQuery(uri, projection, selection, selectionArgs, sortOrder);
	}


}
