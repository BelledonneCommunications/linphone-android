/*
ContactHelper.java
Copyright (C) 2011  Belledonne Communications, Grenoble, France

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

import org.linphone.core.LinphoneAddress;
import org.linphone.mediastream.Version;
import org.linphone.ui.AddressText;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Contacts.Data;
import android.provider.ContactsContract.Intents.Insert;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;

public final class ContactHelper {

	private String username;
	private String domain;
	private ContentResolver resolver;

	private Uri foundPhotoUri;
	public Uri getUri() {
		return foundPhotoUri;
	}

	private String displayName;
	public String getDisplayName() {
		return displayName;
	}

	private LinphoneAddress address;
	public ContactHelper(LinphoneAddress address, ContentResolver resolver) {
		username = address.getUserName();
		domain = address.getDomain();
		this.resolver = resolver;
		this.address = address;
	}

	public boolean query() {
		boolean succeeded;
		if (Version.sdkAboveOrEqual(Version.API05_ECLAIR_20)) {
			ContactHelperNew helper = new ContactHelperNew();
			succeeded = helper.queryNewContactAPI();
		} else {
			succeeded = queryOldContactAPI();
		}
		if (succeeded && !TextUtils.isEmpty(displayName)) {
			address.setDisplayName(displayName);
		}
		return succeeded;
	}

	public static Intent prepareAddContactIntent(AddressText address) {
		return prepareAddContactIntent(address.getDisplayedName(), address.getText().toString());
	}
	
	 //FIXME : Require API 5+
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
	
	public static Intent prepareEditContactIntent(int id) {
		Intent intent = new Intent(Intent.ACTION_EDIT, Contacts.CONTENT_URI);
		Uri contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, id);
		intent.setData(contactUri);
		
		return intent;
	}

	public static boolean testPhotoUri(Cursor c) {
		if (c == null) return false;
		if (!c.moveToNext()) {
			return false;
		}
		byte[] data = c.getBlob(0);
		if (data == null) {
			// TODO: simplify all this stuff
			// which is here only to check that the
			// photoUri really points to some data.
			// Not retrieving the data now would be better.
			return false;
		}   
		return true;
	}

	public static boolean testPhotoUriAndCloseCursor(Cursor c) {
		boolean valid = testPhotoUri(c);
		if (c != null) c.close();
		return valid;
	}
		
	public static boolean testPhotoUri(ContentResolver resolver, Uri photoUriToTest, String photoCol) {
    	Cursor cursor = resolver.query(photoUriToTest, new String[]{photoCol}, null, null, null);
    	return testPhotoUriAndCloseCursor(cursor);
    }

	// OLD API
	@SuppressWarnings("deprecation")
	private final boolean queryOldContactAPI() {
		String normalizedNumber = PhoneNumberUtils.getStrippedReversed(username);
		if (TextUtils.isEmpty(normalizedNumber)) {
			// non phone username
			return false;
		}
		String[] projection = {android.provider.Contacts.Phones.PERSON_ID, android.provider.Contacts.Phones.DISPLAY_NAME};
		String selection = android.provider.Contacts.Phones.NUMBER_KEY + "=" + normalizedNumber;
		Cursor c = resolver.query(android.provider.Contacts.Phones.CONTENT_URI, projection, selection, null, null);
		if (c == null) return false;

		while (c.moveToNext()) {
			long id = c.getLong(c.getColumnIndex(android.provider.Contacts.Phones.PERSON_ID));
			Uri personUri = ContentUris.withAppendedId(android.provider.Contacts.People.CONTENT_URI, id);
			Uri potentialPictureUri = Uri.withAppendedPath(personUri, android.provider.Contacts.Photos.CONTENT_DIRECTORY);
			boolean valid = testPhotoUri(resolver, potentialPictureUri, android.provider.Contacts.Photos.DATA);
			if (valid) {
				displayName = c.getString(c.getColumnIndex(android.provider.Contacts.Phones.DISPLAY_NAME));
				foundPhotoUri = personUri; // hack (not returning pictureUri as it crashes when reading from it)
				c.close();
				return true;
			}
		}
		c.close();
		return false;
	}

	// END OLD API
	
	// START NEW CONTACT API

	private class ContactHelperNew {

		private final boolean checkPhotosUris(ContentResolver resolver, Cursor c, String idCol, String nameCol) {
			if (c == null) return false;
			while (c.moveToNext()) {
				long id = c.getLong(c.getColumnIndex(idCol));
				Uri contactUri = ContentUris.withAppendedId(android.provider.ContactsContract.Contacts.CONTENT_URI, id);
				Uri photoUri = Uri.withAppendedPath(contactUri, android.provider.ContactsContract.Contacts.Photo.CONTENT_DIRECTORY);
				if (photoUri == null) {
					return false;
				}
				String[] projection = {android.provider.ContactsContract.CommonDataKinds.Photo.PHOTO};
		    	Cursor photoCursor = resolver.query(photoUri, projection, null, null, null);
				boolean valid = testPhotoUriAndCloseCursor(photoCursor);
				if (valid) {
					foundPhotoUri = photoUri;
					displayName = c.getString(c.getColumnIndex(nameCol));
					c.close();
					return true;
				}
			}
			c.close();
			return false;
		}

		private final boolean queryNewContactAPI() {
			String sipUri = username + "@" + domain;

			// Try first using sip field
			Uri uri = android.provider.ContactsContract.Data.CONTENT_URI;
			String[] projection = {
					android.provider.ContactsContract.Data.CONTACT_ID,
					android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME};

			// Then using custom SIP field
			if (Version.sdkAboveOrEqual(Version.API09_GINGERBREAD_23)) {
				String selection = new StringBuilder()
					.append(android.provider.ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS)
					.append(" = ? AND ")
					.append(android.provider.ContactsContract.Data.MIMETYPE)
					.append(" = '")
					.append(android.provider.ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE)
					.append("'")
					.toString();
				Cursor c = resolver.query(uri, projection, selection, new String[] {sipUri}, null);
				boolean valid = checkPhotosUris(resolver, c,
						android.provider.ContactsContract.Data.CONTACT_ID,
						android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
				if (valid) return true;
			}
			
			String selection = new StringBuilder()
				.append(android.provider.ContactsContract.CommonDataKinds.Im.DATA).append(" =  ? AND ")
				.append(android.provider.ContactsContract.Data.MIMETYPE)
				.append(" = '")
				.append(android.provider.ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE)
				.append("' AND lower(")
				.append(android.provider.ContactsContract.CommonDataKinds.Im.CUSTOM_PROTOCOL)
				.append(") = 'sip'").toString();
			Cursor c = resolver.query(uri, projection, selection, new String[] {sipUri}, null);
			boolean valid = checkPhotosUris(resolver, c,
					android.provider.ContactsContract.Data.CONTACT_ID,
					android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
			if (valid) return true;

			// Finally using phone number
			String normalizedNumber = PhoneNumberUtils.getStrippedReversed(username);
			if (TextUtils.isEmpty(normalizedNumber)) {
				// non phone username
				return false;
			}
			Uri lookupUri = Uri.withAppendedPath(android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(username));
			projection = new String[]{
					android.provider.ContactsContract.PhoneLookup._ID,
					android.provider.ContactsContract.PhoneLookup.NUMBER,
					android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME};
			c = resolver.query(lookupUri, projection, null, null, null);
			while (c.moveToNext()) {
				long id = c.getLong(c.getColumnIndex(android.provider.ContactsContract.PhoneLookup._ID));
				String enteredNumber = c.getString(c.getColumnIndex(android.provider.ContactsContract.PhoneLookup.NUMBER));
				if (!normalizedNumber.equals(PhoneNumberUtils.getStrippedReversed(enteredNumber))) {
					continue;
				}
				
				Uri contactUri = ContentUris.withAppendedId(android.provider.ContactsContract.Contacts.CONTENT_URI, id);
				Uri photoUri = Uri.withAppendedPath(contactUri, android.provider.ContactsContract.Contacts.Photo.CONTENT_DIRECTORY);
				if (photoUri == null) {
					continue;
				}
				String[] photoProj = {android.provider.ContactsContract.CommonDataKinds.Photo.PHOTO};
				Cursor cursor = resolver.query(photoUri, photoProj, null, null, null);
				valid = testPhotoUriAndCloseCursor(cursor);
				if (valid) {
					displayName = c.getString(c.getColumnIndex(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME));
					foundPhotoUri = photoUri;
					c.close();
					return true;
				}
			}
			c.close();
			return false;
		}
	}
	
	public static List<String> extractContactNumbersAndAddresses(String id, ContentResolver cr) {
		List<String> list = new ArrayList<String>();

		Uri uri = ContactsContract.Data.CONTENT_URI;
		String[] projection = {ContactsContract.CommonDataKinds.Im.DATA};

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

		return list;
	}
}