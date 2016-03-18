/*
LinphoneContact.java
Copyright (C) 2016  Belledonne Communications, Grenoble, France

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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneFriend;
import org.linphone.mediastream.Log;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

public class LinphoneContact implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 9015568163905205244L;

	private transient LinphoneFriend friend;
	private String fullName, androidId;
	private transient Uri photoUri, thumbnailUri;
	private List<LinphoneNumberOrAddress> addresses;
	
	public LinphoneContact() {
		addresses = new ArrayList<LinphoneNumberOrAddress>();
		androidId = null;
		thumbnailUri = null;
		photoUri = null;
	}

	public void setFullName(String name) {
		if (name == null) return;
		
		fullName = name;
		if (friend != null) {
			friend.setName(name);
		}
	}
	
	public String getFullName() {
		return fullName;
	}
	
	public boolean hasPhoto() {
		return photoUri != null;
	}
	
	public void setPhotoUri(Uri uri) {
		photoUri = uri;
	}
	
	public Uri getPhotoUri() {
		return photoUri;
	}
	
	public void setThumbnailUri(Uri uri) {
		thumbnailUri = uri;
	}

	public Uri getThumbnailUri() {
		return thumbnailUri;
	}

	public void addNumberOrAddress(LinphoneNumberOrAddress noa) {
		if (noa == null) return;
		addresses.add(noa);
	}

	public List<LinphoneNumberOrAddress> getNumbersOrAddresses() {
		return addresses;
	}

	public boolean hasAddress(String address) {
		for (LinphoneNumberOrAddress noa : getNumbersOrAddresses()) {
			if (noa.isSIPAddress()) {
				String value = noa.getValue();
				if (value.equals(address) || value.equals("sip:" + address)) {
					return true;
				}
			}
		}
		return false;
	}
	
	public boolean hasAddress() {
		for (LinphoneNumberOrAddress noa : getNumbersOrAddresses()) {
			if (noa.isSIPAddress()) {
				return true;
			}
		}
		return false;
	}
	
	public void setAndroidId(String id) {
		androidId = id;
	}

	public String getAndroidId() {
		return androidId;
	}

	public void delete() {
		if (isAndroidContact()) {
			String select = ContactsContract.Data.CONTACT_ID + " = ?";
			String[] args = new String[] { getAndroidId() };
	
			ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
			ops.add(ContentProviderOperation.newDelete(ContactsContract.RawContacts.CONTENT_URI).withSelection(select, args).build());
	
			ContentResolver cr = ContactsManager.getInstance().getContentResolver();
			try {
				cr.applyBatch(ContactsContract.AUTHORITY, ops);
			} catch (Exception e) {
				Log.e(e);
			}
		}
		deleteFriend();
	}
	
	public void deleteFriend() {
		if (friend != null) {
			LinphoneManager.getLcIfManagerNotDestroyedOrNull().removeFriend(friend);
		}
	}

	public void refresh() {
		addresses = new ArrayList<LinphoneNumberOrAddress>();
		if (friend != null) {
			LinphoneAddress addr = friend.getAddress();
			if (addr != null) {
				addresses.add(new LinphoneNumberOrAddress(addr.asStringUriOnly(), true));
			}
		}
		
		if (!isAndroidContact()) {
			fullName = friend.getName();
			thumbnailUri = null;
			photoUri = null;
		} else {
			String id = getAndroidId();
			String name = getName(id);
			Uri thumbnail = getContactPictureUri(id);
			Uri photo = getContactPhotoUri(id);
			
			setFullName(name);
			setThumbnailUri(thumbnail);
			setPhotoUri(photo);
			for (LinphoneNumberOrAddress noa : getAddressesAndNumbersForAndroidContact(id)) {
				addresses.add(noa);
			}
		}
	}
	
	public boolean isAndroidContact() {
		return androidId != null;
	}

	public static LinphoneContact createLinphoneFriend(String name, String address) {
		LinphoneContact contact = new LinphoneContact();
		LinphoneFriend friend = LinphoneCoreFactory.instance().createLinphoneFriend();
		contact.friend = friend;
		
		if (name != null) {
			contact.setFullName(name);
		}
		if (address != null) {
			contact.addNumberOrAddress(new LinphoneNumberOrAddress(address, true));
		}
		
		return contact;
	}
	
	private Uri getContactUri(String id) {
		Uri person = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, id);
		return person;
	}
	
	private Uri getContactPictureUri(String id) {
		Uri person = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, Long.parseLong(id));
		return Uri.withAppendedPath(person, ContactsContract.Contacts.Photo.CONTENT_DIRECTORY);
	}

	private Uri getContactPhotoUri(String id) {
		Uri person = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, Long.parseLong(id));
		return Uri.withAppendedPath(person, ContactsContract.Contacts.Photo.DISPLAY_PHOTO);
	}
	
	private String getName(String id) {
		ContentResolver resolver = ContactsManager.getInstance().getContentResolver();
		Cursor cursor = resolver.query(getContactUri(id), null, null, null, null);
		String name = null;
		if (cursor != null) {
	        if (cursor.moveToFirst()) {
	        	name = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
	        }
	        cursor.close();
		}
		return name;
	}
	
	private List<LinphoneNumberOrAddress> getAddressesAndNumbersForAndroidContact(String id) {
		List<LinphoneNumberOrAddress> result = new ArrayList<LinphoneNumberOrAddress>();
		ContentResolver resolver = ContactsManager.getInstance().getContentResolver();
		
		Uri uri = ContactsContract.Data.CONTENT_URI;
		String[] projection;

		// SIP addresses
		String selection2 = new StringBuilder().append(ContactsContract.Data.CONTACT_ID).append(" = ? AND ").append(ContactsContract.Data.MIMETYPE).append(" = '").append(ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE).append("'").toString();
		projection = new String[] { ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS };
		Cursor c = resolver.query(uri, projection, selection2, new String[]{ id }, null);
		if (c != null) {
			while (c.moveToNext()) {
				result.add(new LinphoneNumberOrAddress("sip:" + c.getString(c.getColumnIndex(ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS)), true)); 
			}
			c.close();
		}

		// Phone Numbers
		c = resolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, new String[] { ContactsContract.CommonDataKinds.Phone.NUMBER }, ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = " + id, null, null);
		if (c != null) {
			while (c.moveToNext()) {
				String number = c.getString(c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
				result.add(new LinphoneNumberOrAddress(number, false));
			}
			c.close();
		}
		
		return result;
	}
}
