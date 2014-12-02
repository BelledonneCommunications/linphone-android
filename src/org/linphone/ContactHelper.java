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

import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.mediastream.Version;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
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
		boolean succeeded = queryContact();
		if (succeeded && !TextUtils.isEmpty(displayName)) {
			address.setDisplayName(displayName);
		}
		return succeeded;
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
	
	private void checkPhotosUris(ContentResolver resolver, Cursor c, String idCol, String nameCol) {
		displayName = c.getString(c.getColumnIndex(nameCol));
		
		long id = c.getLong(c.getColumnIndex(idCol));
		Uri contactUri = ContentUris.withAppendedId(android.provider.ContactsContract.Contacts.CONTENT_URI, id);
		Uri photoUri = Uri.withAppendedPath(contactUri, android.provider.ContactsContract.Contacts.Photo.CONTENT_DIRECTORY);

		if (photoUri != null) {
			String[] projection = { android.provider.ContactsContract.CommonDataKinds.Photo.PHOTO };
	    	Cursor photoCursor = resolver.query(photoUri, projection, null, null, null);
			
	    	boolean isPhotoValid = testPhotoUriAndCloseCursor(photoCursor);
			if (isPhotoValid) {
				foundPhotoUri = photoUri;
			}
		}
	}
	
	private boolean checkSIPQueryResult(Cursor c, String columnSip) {
		boolean contactFound = false;
		
		if (c != null) {
			while (!contactFound && c.moveToNext()) {
				String contact = c.getString(c.getColumnIndex(columnSip));
				if (contact.equals(username + "@" + domain) || contact.equals(username)) {
					contactFound = true;
					checkPhotosUris(resolver, c, android.provider.ContactsContract.Data.CONTACT_ID, android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
				} else {
					String normalizedUsername = null;
					LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
					if (lc != null) {
						LinphoneProxyConfig lpc = lc.getDefaultProxyConfig();
						if (lpc != null) {
							if (contact.contains("@")) {
								normalizedUsername = lpc.normalizePhoneNumber(contact.split("@")[0]);
							} else {
								normalizedUsername = lpc.normalizePhoneNumber(contact);
							}
						}
					}
					if (normalizedUsername != null && normalizedUsername.equals(username)) {
						contactFound = true;
						checkPhotosUris(resolver, c, android.provider.ContactsContract.Data.CONTACT_ID, android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
					}
				}
			}
			c.close();
		}
		
		return contactFound;
	}
	
	private boolean checkPhoneQueryResult(Cursor c, String columnPhone) {
		boolean contactFound = false;
		
		if (c != null) {
			while (!contactFound && c.moveToNext()) {
				String contact = c.getString(c.getColumnIndex(columnPhone));
				if (contact.equals(username)) {
					contactFound = true;
					checkPhotosUris(resolver, c, android.provider.ContactsContract.PhoneLookup._ID, android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME);
				} else {
					String normalizedUsername = null;
					LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
					if (lc != null) {
						LinphoneProxyConfig lpc = lc.getDefaultProxyConfig();
						if (lpc != null) {
							normalizedUsername = lpc.normalizePhoneNumber(contact);
						}
					}
					if (normalizedUsername != null && normalizedUsername.equals(username)) {
						contactFound = true;
						checkPhotosUris(resolver, c, android.provider.ContactsContract.PhoneLookup._ID, android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME);
					}
				}
			}
			c.close();
		}
		
		return contactFound;
	}
	
	@SuppressLint("InlinedApi")
	private final boolean queryContact() {
		boolean contactFound = false;
		
		Uri uri = android.provider.ContactsContract.Data.CONTENT_URI;
		String[] projection = { android.provider.ContactsContract.Data.CONTACT_ID, android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, android.provider.ContactsContract.CommonDataKinds.Im.DATA };
		
		String selection = new StringBuilder()
			.append(android.provider.ContactsContract.Data.MIMETYPE)
			.append(" = '")
			.append(android.provider.ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE)
			.append("' AND lower(")
			.append(android.provider.ContactsContract.CommonDataKinds.Im.CUSTOM_PROTOCOL)
			.append(") = 'sip'").toString();
		
		Cursor c = resolver.query(uri, projection, selection, null, null);
		contactFound = checkSIPQueryResult(c, android.provider.ContactsContract.CommonDataKinds.Im.DATA);
		if (contactFound) {
			return true;
		}
		
		if (Version.sdkAboveOrEqual(Version.API09_GINGERBREAD_23)) {
			selection = new StringBuilder()
				.append(android.provider.ContactsContract.Data.MIMETYPE)
				.append(" = '")
				.append(android.provider.ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE)
				.append("'")
				.toString();
			c = resolver.query(uri, projection, selection, null, null);
			contactFound = checkSIPQueryResult(c, android.provider.ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS);
			if (contactFound) {
				return true;
			}
		}
		
		Uri lookupUri = Uri.withAppendedPath(android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(username));
		projection = new String[] {
			android.provider.ContactsContract.PhoneLookup._ID,
			android.provider.ContactsContract.PhoneLookup.NUMBER,
			android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME };
		c = resolver.query(lookupUri, projection, null, null, null);
		contactFound = checkPhoneQueryResult(c, android.provider.ContactsContract.PhoneLookup.NUMBER);
		
		return contactFound;
	}
}