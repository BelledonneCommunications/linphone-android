package org.linphone;
/*
CallManager.java
Copyright (C) 2015  Belledonne Communications, Grenoble, France

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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.ContactsContract;

import org.linphone.compatibility.Compatibility;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneFriend;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.mediastream.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ContactsManager {
	private static ContactsManager instance;
	private List<Contact> contactList, sipContactList;
	private Cursor contactCursor, sipContactCursor;
	private Account mAccount;
	private boolean preferLinphoneContacts = false, isContactPresenceDisabled = true, hasContactAccess = false;
	private ContentResolver contentResolver;
	private Context context;

	private ContactsManager() {}

	public static final synchronized ContactsManager getInstance() {
		if (instance == null) instance = new ContactsManager();
		return instance;
	}

	public List<Contact> getAllContacts() {
		return contactList;
	}

	public List<Contact> getSIPContacts() {
		return sipContactList;
	}

	public Cursor getAllContactsCursor() {
		return contactCursor;
	}

	public Cursor getSIPContactsCursor() {
		return sipContactCursor;
	}
	
	public void enabledContactsAccess(){
		hasContactAccess = true;
	}

	public void setLinphoneContactsPrefered(boolean isPrefered) {
		preferLinphoneContacts = isPrefered;
	}

	public boolean isLinphoneContactsPrefered() {
		return preferLinphoneContacts;
	}

	public boolean isContactPresenceDisabled() {
		return isContactPresenceDisabled;
	}

	public void initializeContactManager(Context context, ContentResolver contentResolver){
		this.context = context;
		this.contentResolver = contentResolver;
	}

	public void initializeSyncAccount(Context context, ContentResolver contentResolver) {
		initializeContactManager(context,contentResolver);
		AccountManager accountManager = (AccountManager) context.getSystemService(Context.ACCOUNT_SERVICE);

		Account[] accounts = accountManager.getAccountsByType(context.getPackageName());

		if(accounts != null && accounts.length == 0) {
			Account newAccount = new Account(context.getString(R.string.sync_account_name), context.getPackageName());
			try {
				accountManager.addAccountExplicitly(newAccount, null, null);
				mAccount = newAccount;
			} catch (Exception e) {
				Log.e(e);
				mAccount = null;
			}
		} else {
			mAccount = accounts[0];
		}
		initializeContactManager(context,contentResolver);
	}

	public String getDisplayName(String firstName, String lastName) {
		String displayName = null;
		if (firstName.length() > 0 && lastName.length() > 0)
			displayName = firstName + " " + lastName;
		else if (firstName.length() > 0)
			displayName = firstName;
		else if (lastName.length() > 0)
			displayName = lastName.toString();
		return displayName;
	}

	//Contacts
	public void createNewContact(ArrayList<ContentProviderOperation> ops, String firstName, String lastName) {
		int contactID = 0;

		ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
			.withValue(ContactsContract.RawContacts.AGGREGATION_MODE, ContactsContract.RawContacts.AGGREGATION_MODE_DEFAULT)
			.withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
			.withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
			.build()
		);

		if (getDisplayName(firstName, lastName) != null) {
			ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
				.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, contactID)
				.withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
				.withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, firstName)
				.withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, lastName)
				.withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, getDisplayName(firstName, lastName))
				.build()
			);
		}
	}

	public void updateExistingContact(ArrayList<ContentProviderOperation> ops, Contact contact, String firstName, String lastName) {
		if (getDisplayName(firstName, lastName) != null) {
			String select = ContactsContract.Data.CONTACT_ID + "=? AND " + ContactsContract.Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE + "'";
			String[] args = new String[]{String.valueOf(contact.getID())};

			ops.add(ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
				.withSelection(select, args)
				.withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
				.withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, firstName)
				.withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, lastName)
				.build()
			);
		}
	}

	public void updateExistingContactPicture(ArrayList<ContentProviderOperation> ops, Contact contact, String path){
		String select = ContactsContract.Data.CONTACT_ID + "=? AND " + ContactsContract.Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE + "'";
		String[] args =new String[]{String.valueOf(contact.getID())};

		ops.add(ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
						.withSelection(select, args)
						.withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
						.withValue(ContactsContract.CommonDataKinds.Photo.PHOTO_FILE_ID, path)
								//.withValue(ContactsContract.CommonDataKinds.Photo.PHOTO_FILE_ID, )
						.build()
		);
	}

//Manage Linphone Friend if we cannot use Sip address
	public boolean createNewFriend(Contact contact, String sipUri) {
		if (!sipUri.startsWith("sip:")) {
			sipUri = "sip:" + sipUri;
		}

		LinphoneFriend friend = LinphoneCoreFactory.instance().createLinphoneFriend(sipUri);
		if (friend != null) {
			friend.edit();
			friend.enableSubscribes(false);
			friend.setRefKey(contact.getID());
			friend.done();
			try {
				LinphoneManager.getLcIfManagerNotDestroyedOrNull().addFriend(friend);
				return true;
			} catch (LinphoneCoreException e) {
				e.printStackTrace();
				return false;
			}
		} else {
			return false;
		}
	}

	public void updateFriend(String oldSipUri, String newSipUri) {
		if (!newSipUri.startsWith("sip:")) {
			newSipUri = "sip:" + newSipUri;
		}

		if (!oldSipUri.startsWith("sip:")) {
			oldSipUri = "sip:" + oldSipUri;
		}

		LinphoneFriend friend = LinphoneManager.getLcIfManagerNotDestroyedOrNull().findFriendByAddress(oldSipUri);
		if (friend != null) {
			friend.edit();
			try {
				friend.setAddress(LinphoneCoreFactory.instance().createLinphoneAddress(newSipUri));
			} catch (LinphoneCoreException e) {
				e.printStackTrace();
			}
			friend.done();
		}
	}

	public boolean removeFriend(String sipUri) {
		if (!sipUri.startsWith("sip:")) {
			sipUri = "sip:" + sipUri;
		}

		LinphoneFriend friend = LinphoneManager.getLcIfManagerNotDestroyedOrNull().findFriendByAddress(sipUri);
		if (friend != null) {
			LinphoneManager.getLcIfManagerNotDestroyedOrNull().removeFriend(friend);
			return true;
		}
		return false;
	}

	public void removeAllFriends(Contact contact) {
		for (LinphoneFriend friend : LinphoneManager.getLcIfManagerNotDestroyedOrNull().getFriendList()) {
			if (friend.getRefKey().equals(contact.getID())) {
				LinphoneManager.getLcIfManagerNotDestroyedOrNull().removeFriend(friend);
			}
		}
	}

	public Contact findContactWithDisplayName(String displayName) {
		String[] projection = {ContactsContract.Data.CONTACT_ID, ContactsContract.Data.DISPLAY_NAME};
		String selection = new StringBuilder()
				.append(ContactsContract.Data.DISPLAY_NAME)
				.append(" = ?").toString();

		Cursor c = contentResolver.query(ContactsContract.Data.CONTENT_URI, projection, selection,
				new String[]{displayName}, null);
		if (c != null) {
			if (c.moveToFirst()) {
				Contact contact = Compatibility.getContact(contentResolver, c, c.getPosition());
				c.close();

				if (contact != null) {
					return contact;
				} else {
					return null;
				}
			}
			c.close();
		}
		return null;
	}

	public Contact getContact(String id, ContentResolver contentResolver){
		String[] projection = {ContactsContract.Data.CONTACT_ID, ContactsContract.Data.DISPLAY_NAME};
		String selection = new StringBuilder()
				.append(ContactsContract.Data.CONTACT_ID)
				.append(" = ?").toString();

		Cursor c = contentResolver.query(ContactsContract.Data.CONTENT_URI, projection, selection, new String[]{id}, null);
		if(c!=null){
			if (c.moveToFirst()) {
				Contact contact = Compatibility.getContact(contentResolver, c, c.getPosition());
				c.close();

				if (contact != null) {
					return contact;
				} else {
					return null;
				}
			}
			c.close();
		}
		return null;
	}

	public List<String> getContactsId(){
		List<String> ids = new ArrayList<String>();
		if(LinphoneManager.getLcIfManagerNotDestroyedOrNull().getFriendList() == null) return null;
		for(LinphoneFriend friend : LinphoneManager.getLcIfManagerNotDestroyedOrNull().getFriendList()) {
			friend.edit();
			friend.enableSubscribes(false);
			friend.done();
			if(!ids.contains(friend.getRefKey())){
				ids.add(friend.getRefKey());
			}
		}

		return ids;
	}
//End linphone Friend

	public boolean removeContactTagIsNeeded(Contact contact){
		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			LinphoneProxyConfig lpc = lc.createProxyConfig();
			contact.refresh(contentResolver);
			for (String address : contact.getNumbersOrAddresses()) {
				if (!lpc.isPhoneNumber(address)) {
					return false;
				}
			}
			return true;
		}
		return false;
	}

	public void removeLinphoneContactTag(Contact contact){
		ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
		String select = ContactsContract.RawContacts._ID + " = ?";
		String[] args = new String[] { findRawLinphoneContactID(contact.getID()) };


		ops.add(ContentProviderOperation.newDelete(ContactsContract.RawContacts.CONTENT_URI)
						.withSelection(select, args)
						.build()
		);

		try {
			contentResolver.applyBatch(ContactsContract.AUTHORITY, ops);
		} catch (Exception e) {
			Log.w(e.getMessage() + ":" + e.getStackTrace());
		}
	}

	private Contact checkPhoneQueryResult(ContentResolver contentResolver, Cursor c, String columnPhone, String columnId, String username) {
		boolean contactFound = false;

		if (c != null) {
			while (!contactFound && c.moveToNext()) {
				String phone = c.getString(c.getColumnIndex(columnPhone));
				if (phone.equals(username)) {
					contactFound = true;
				} else {
					String normalizedUsername = null;
					LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
					if (lc != null) {
						LinphoneProxyConfig lpc = lc.getDefaultProxyConfig();
						if (lpc != null) {
							normalizedUsername = lpc.normalizePhoneNumber(phone);
						}
					}

					if (normalizedUsername != null && normalizedUsername.equals(username)) {
						contactFound = true;
					}
				}

				if(contactFound){
					Contact contact = getContact(c.getString(c.getColumnIndex(columnId)), contentResolver);
					c.close();
					return contact;
				}
			}
			c.close();
		}
		return null;
	}

	public Contact findContactWithAddress(LinphoneAddress address) {
		String sipUri = address.asStringUriOnly();
		if (sipUri.startsWith("sip:"))
			sipUri = sipUri.substring(4);

		for(Contact c: getAllContacts()){
			for(String a: c.getNumbersOrAddresses()){
				if(a.equals(sipUri))
					return c;
			}
		}
		return null;
	}

	public Contact findContactWithAddress(ContentResolver contentResolver, LinphoneAddress address){
		String sipUri = address.asStringUriOnly();
		if (sipUri.startsWith("sip:"))
			sipUri = sipUri.substring(4);

		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if(lc != null && lc.getFriendList() != null && LinphoneManager.getLcIfManagerNotDestroyedOrNull().getFriendList().length > 0) {
			for (LinphoneFriend friend : LinphoneManager.getLcIfManagerNotDestroyedOrNull().getFriendList()) {
				if (friend.getAddress().equals(address)) {
					return getContact(friend.getRefKey(), contentResolver);
				}
			}
		}

		//Find Sip address
		if(hasContactAccess) {
			Contact contact;
			String[] projection = new String[]{ContactsContract.Data.CONTACT_ID, ContactsContract.Data.DISPLAY_NAME};
			String selection = new StringBuilder()
					.append(ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS)
					.append(" = ?").toString();

			Cursor cur = contentResolver.query(ContactsContract.Data.CONTENT_URI, projection, selection,
					new String[]{sipUri}, null);
			if (cur != null) {
				if (cur.moveToFirst()) {
					contact = Compatibility.getContact(contentResolver, cur, cur.getPosition());
					cur.close();

					if (contact != null) {
						return contact;
					}
				}
				cur.close();
			}

			//Find number
			Uri lookupUri = Uri.withAppendedPath(android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address.getUserName()));
			projection = new String[]{ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.NUMBER, ContactsContract.PhoneLookup.DISPLAY_NAME};
			Cursor c = contentResolver.query(lookupUri, projection, null, null, null);
			contact = checkPhoneQueryResult(contentResolver, c, ContactsContract.PhoneLookup.NUMBER, ContactsContract.PhoneLookup._ID, address.getUserName());

			if (contact != null) {
				return contact;
			}
		}

		return null;
	}

	public void removeContactFromLists(ContentResolver contentResolver, Contact contact) {
		for (Contact c : contactList) {
			if (c != null && c.getID().equals(contact.getID())) {
				contactList.remove(c);
				contactCursor = Compatibility.getContactsCursor(contentResolver,getContactsId());
				break;
			}
		}

		for (Contact c : sipContactList) {
			if (c != null && c.getID().equals(contact.getID())) {
				sipContactList.remove(c);
				sipContactCursor = Compatibility.getSIPContactsCursor(contentResolver,getContactsId());
				break;
			}
		}
	}

	public boolean isContactHasAddress(Contact contact, String address){
		if(contact != null) {
			contact.refresh(contentResolver);
			return contact.getNumbersOrAddresses().contains(address) || contact.getNumbersOrAddresses().contains("sip:" + address);
		}
		return false;
	}

	public String findRawContactID(ContentResolver cr, String contactID) {
		Cursor c = cr.query(ContactsContract.RawContacts.CONTENT_URI,
				new String[]{ContactsContract.RawContacts._ID},
				ContactsContract.RawContacts.CONTACT_ID + "=?",
				new String[]{contactID}, null);
		if (c != null) {
			String result = null;
			if (c.moveToFirst()) {
				result = c.getString(c.getColumnIndex(ContactsContract.RawContacts._ID));
			}

			c.close();
			return result;
		}
		return null;
	}

	public String findRawLinphoneContactID(String contactID) {
		String result = null;
		String[] projection = { ContactsContract.RawContacts._ID };

		String selection = ContactsContract.RawContacts.CONTACT_ID + "=? AND "
				+ ContactsContract.RawContacts.ACCOUNT_TYPE + "=? ";

		Cursor c = contentResolver.query(ContactsContract.RawContacts.CONTENT_URI, projection,
				selection, new String[]{contactID, "org.linphone"}, null);
		if (c != null) {
			if (c.moveToFirst()) {
				result = c.getString(c.getColumnIndex(ContactsContract.RawContacts._ID));
			}
		}
		c.close();
		return result;
	}

	//Migrate old IM contacts into SIP addresses or linphoneFriends
	public void migrateContacts() {
		Cursor oldContacts =  Compatibility.getImContactsCursor(contentResolver);
		ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();

		if(oldContacts != null){
			for (int i = 0; i < oldContacts.getCount(); i++) {
				Contact contact = Compatibility.getContact(contentResolver, oldContacts, i);
				for (String address : Compatibility.extractContactImAddresses(contact.getID(), contentResolver)) {
					if (LinphoneUtils.isSipAddress(address)) {
						if (address.startsWith("sip:")) {
							address = address.substring(4);
						}

						//Add new sip address
						Compatibility.addSipAddressToContact(context, ops, address, findRawContactID(contentResolver, contact.getID()));
						try {
							contentResolver.applyBatch(ContactsContract.AUTHORITY, ops);
						} catch (Exception e) {
							e.printStackTrace();
						}

						ops.clear();

						contact.refresh(contentResolver);

						//If address sip is correctly add, remove the im address
						if(contact.getNumbersOrAddresses().contains(address)){
							Compatibility.deleteImAddressFromContact(ops, address, contact.getID());
							try {
								contentResolver.applyBatch(ContactsContract.AUTHORITY, ops);
							} catch (Exception e) {
								e.printStackTrace();
							}
							ops.clear();
						} else {
							//Add linphone friend instead
							if(createNewFriend(contact, address)) {
								contact.refresh(contentResolver);

								//Remove IM address
								Compatibility.deleteImAddressFromContact(ops, address, contact.getID());
								try {
									contentResolver.applyBatch(ContactsContract.AUTHORITY, ops);
								} catch (Exception e) {
									e.printStackTrace();

								}
							}
						}
					}
					ops.clear();
				}
			}
			oldContacts.close();
		}

	}

	public synchronized void prepareContactsInBackground() {
		if (contactCursor != null) {
			contactCursor.close();
		}
		if (sipContactCursor != null) {
			sipContactCursor.close();
		}

		if(LinphoneActivity.instance().getResources().getBoolean(R.bool.use_linphone_friend)){
			contactList = new ArrayList<Contact>();
			for(LinphoneFriend friend : LinphoneManager.getLc().getFriendList()){
				Contact contact = new Contact(friend.getRefKey(),friend.getAddress());
				contactList.add(contact);
			}

			contactCursor = getFriendListCursor(contactList,true);
			return;
		}

		if(mAccount == null) return;

		contactCursor = Compatibility.getContactsCursor(contentResolver, getContactsId());
		sipContactCursor = Compatibility.getSIPContactsCursor(contentResolver, getContactsId());

		Thread sipContactsHandler = new Thread(new Runnable() {
			@Override
			public void run() {
				if(sipContactCursor != null && sipContactCursor.getCount() > 0) {
					for (int i = 0; i < sipContactCursor.getCount(); i++) {
						Contact contact = Compatibility.getContact(contentResolver, sipContactCursor, i);
						if (contact == null)
							continue;

						contact.refresh(contentResolver);
						//Add tag to Linphone contact if it not existed
						if (LinphoneActivity.isInstanciated() && LinphoneActivity.instance().getResources().getBoolean(R.bool.use_linphone_tag)) {
							if (!isContactHasLinphoneTag(contact, contentResolver)) {
								Compatibility.createLinphoneContactTag(context, contentResolver, contact,
										findRawContactID(contentResolver, String.valueOf(contact.getID())));
							}
						}

						sipContactList.add(contact);
					}
				}
				if (contactCursor != null) {
					for (int i = 0; i < contactCursor.getCount(); i++) {
						Contact contact = Compatibility.getContact(contentResolver, contactCursor, i);
						if (contact == null)
							continue;

						//Remove linphone contact tag if the contact has no sip address
						if (LinphoneActivity.isInstanciated() && LinphoneActivity.instance().getResources().getBoolean(R.bool.use_linphone_tag)) {
							if (removeContactTagIsNeeded(contact) && findRawLinphoneContactID(contact.getID()) != null) {
								removeLinphoneContactTag(contact);
							}
						}
						for (Contact c : sipContactList) {
							if (c != null && c.getID().equals(contact.getID())) {
								contact = c;
								break;
							}
						}
						contactList.add(contact);
					}
				}
			}
		});

		contactList = new ArrayList<Contact>();
		sipContactList = new ArrayList<Contact>();

		sipContactsHandler.start();
	}

	public static String queryAddressOrNumber(ContentResolver resolver, Uri contactUri) {
		// Phone Numbers
		String[] projection = new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER};
		Cursor c = resolver.query(contactUri, projection, null, null, null);
		if (c != null) {
			while (c.moveToNext()) {
				int numberIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
				String number = c.getString(numberIndex);
				c.close();
				return number;
			}
		}

		// SIP addresses
		projection = new String[] {ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS};
		c = resolver.query(contactUri, projection, null, null, null);
		if (c != null) {
			while (c.moveToNext()) {
				int numberIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS);
				String address = c.getString(numberIndex);
				c.close();
				return address;
			}
			c.close();
		}
		return null;
	}

	boolean isContactHasLinphoneTag(Contact contact, ContentResolver cr) {
		String select = ContactsContract.Data.CONTACT_ID + " = ?";
		String[] args = new String[] { contact.getID() };

		String[] projection = new String[] {ContactsContract.Data.MIMETYPE };

		Cursor cursor = cr.query(ContactsContract.Data.CONTENT_URI, projection, select, args, null);

		if (cursor != null) {
			while (cursor.moveToNext()) {
				if(cursor.getString(cursor.getColumnIndex(ContactsContract.Data.MIMETYPE)).equals("vnd.android.cursor.item/org.linphone.profile")){
					cursor.close();
					return true;
				}
			}
		}
		cursor.close();
		return false;
	}

	public Cursor getFriendListCursor(List<Contact> contacts, boolean shouldGroupBy){
		String[] columns = new String[] { ContactsContract.Data.CONTACT_ID, ContactsContract.Data.DISPLAY_NAME };


		if (!shouldGroupBy) {
			return null;
		}

		MatrixCursor result = new MatrixCursor(columns);
		Set<String> groupBy = new HashSet<String>();
		for (Contact contact: contacts) {
			String name = contact.getName();
			if (!groupBy.contains(name)) {
				groupBy.add(name);
				Object[] newRow = new Object[2];

				newRow[0] = contact.getID();
				newRow[1] = contact.getName();

				result.addRow(newRow);
			}
		}
		return result;
	}

}
