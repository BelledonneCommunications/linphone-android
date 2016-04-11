/*
ContactsManager.java
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

package org.linphone;

import java.util.ArrayList;
import java.util.List;

import org.linphone.compatibility.Compatibility;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneFriend;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.mediastream.Log;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Data;

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
	
	private static ArrayList<ContactsUpdatedListener> contactsUpdatedListeners;
	public static void addContactsListener(ContactsUpdatedListener listener) {
		contactsUpdatedListeners.add(listener);
	}
	public static void removeContactsListener(ContactsUpdatedListener listener) {
		contactsUpdatedListeners.remove(listener);
	}
	
	private static Handler handler = new Handler() {
		@SuppressWarnings("unchecked")
		@Override
		public void handleMessage (Message msg) {
			if (msg.what == CONTACTS_UPDATED && msg.obj instanceof List<?>) {
				List<LinphoneContact> c = (List<LinphoneContact>) msg.obj;
				ContactsManager.getInstance().setContacts(c);
				for (ContactsUpdatedListener listener : contactsUpdatedListeners) {
					listener.onContactsUpdated();
				}
			}
		}
	};

	private ContactsManager(Handler handler) {
		super(handler);
		contactsUpdatedListeners = new ArrayList<ContactsUpdatedListener>();
	}
	
	@Override
	public void onChange(boolean selfChange) {
		 onChange(selfChange, null);
	}
	
	@Override
	public void onChange(boolean selfChange, Uri uri) {
		List<LinphoneContact> contacts = fetchContactsAsync();
		Message msg = handler.obtainMessage();
		msg.what = CONTACTS_UPDATED;
		msg.obj = contacts;
		handler.sendMessage(msg);
	}
	
	public ContentResolver getContentResolver() {
		return contentResolver;
	}

	public static final synchronized ContactsManager getInstance() {
		if (instance == null) instance = new ContactsManager(handler);
		return instance;
	}
	
	public synchronized boolean hasContacts() {
		return contacts.size() > 0;
	}
	
	public synchronized List<LinphoneContact> getContacts() {
		return contacts;
	}

	}
	public Cursor getAllContactsCursor() {
		return contactCursor;
	}
	
	public boolean hasContactsAccess() {
		return hasContactAccess && !context.getResources().getBoolean(R.bool.force_use_of_linphone_friends);
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

	public void initializeContactManager(Context context, ContentResolver contentResolver) {
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
		initializeContactManager(context, contentResolver);
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
		LinphoneProxyConfig lpc = null;
		if (lc != null) {
			lpc = lc.getDefaultProxyConfig();
		}
		
		for (LinphoneContact c: getContacts()) {
			for (LinphoneNumberOrAddress noa: c.getNumbersOrAddresses()) {
				String normalized = null;
				if (lpc != null) {
					normalized = lpc.normalizePhoneNumber(noa.getValue());
				}
				
				if ((noa.isSIPAddress() && noa.getValue().equals(sipUri)) || (normalized != null && !noa.isSIPAddress() && normalized.equals(username)) || (!noa.isSIPAddress() && noa.getValue().equals(username))) {
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
		Contact contact;
		String [] projection = new String[]  {ContactsContract.Data.CONTACT_ID, ContactsContract.Data.DISPLAY_NAME};
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
		}

		//Find number
		Uri lookupUri = Uri.withAppendedPath(android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address.getUserName()));
		projection = new String[] {ContactsContract.PhoneLookup._ID,ContactsContract.PhoneLookup.NUMBER,ContactsContract.PhoneLookup.DISPLAY_NAME };
		Cursor c = contentResolver.query(lookupUri, projection, null, null, null);
		contact = checkPhoneQueryResult(contentResolver, c, ContactsContract.PhoneLookup.NUMBER, ContactsContract.PhoneLookup._ID, address.getUserName());

		if (contact != null) {
			return contact;
		}

		return null;

	}
	
	public synchronized void setContacts(List<LinphoneContact> c) {
		contacts = c;
		sipContacts = new ArrayList<LinphoneContact>();
		for (LinphoneContact contact : contacts) {
			if (contact.hasAddress()) {
				sipContacts.add(contact);
			}
		}
	}

	public synchronized void fetchContacts() {
		setContacts(fetchContactsAsync());
	}
	
	public List<LinphoneContact> fetchContactsAsync() {
		List<LinphoneContact> contacts = new ArrayList<LinphoneContact>();
		
		if (mAccount != null && hasContactsAccess()) {
			Cursor c = Compatibility.getContactsCursor(contentResolver, null);
			if (c != null) {
				while (c.moveToNext()) {
					String id = c.getString(c.getColumnIndex(Data.CONTACT_ID));
					LinphoneContact contact = new LinphoneContact();
					contact.setAndroidId(id);
					contacts.add(contact);
				}
				c.close();
			}
		}
		
		for (LinphoneFriend friend : LinphoneManager.getLc().getFriendList()) {
			String refkey = friend.getRefKey();
			if (refkey != null) {
				boolean found = false;
				for (LinphoneContact contact : contacts) {
					if (refkey.equals(contact.getAndroidId())) {
						contact.setFriend(friend);
						found = true;
						break;
					}
				}
				if (!found) {
					LinphoneContact contact = new LinphoneContact();
					contact.setFriend(friend);
					contacts.add(contact);
				}
			} else {
				LinphoneContact contact = new LinphoneContact();
				contact.setFriend(friend);
				contacts.add(contact);
			}
		}
		
		for (LinphoneContact contact : contacts) {
			contact.refresh();
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
	
	public static String getAddressOrNumberForAndroidContact(ContentResolver resolver, Uri contactUri) {
		// Phone Numbers
		String[] projection = new String[]{ ContactsContract.CommonDataKinds.Phone.NUMBER };
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
		projection = new String[] { ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS };
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
	
	public void delete(String id) {
		ArrayList<String> ids = new ArrayList<String>();
		ids.add(id);
		deleteMultipleContactsAtOnce(ids);
	}
	
	public void deleteMultipleContactsAtOnce(List<String> ids) {
		String select = ContactsContract.Data.CONTACT_ID + " = ?";
		ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
		
		for (String id : ids) {
			String[] args = new String[] { id };
			ops.add(ContentProviderOperation.newDelete(ContactsContract.RawContacts.CONTENT_URI).withSelection(select, args).build());
		}

		ContentResolver cr = ContactsManager.getInstance().getContentResolver();
		try {
			cr.applyBatch(ContactsContract.AUTHORITY, ops);
		} catch (Exception e) {
			Log.e(e);
		}
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
