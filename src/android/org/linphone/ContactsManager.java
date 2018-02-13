package org.linphone;

/*
ContactsManager.java
Copyright (C) 2017  Belledonne Communications, Grenoble, France

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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Data;

import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneFriend;
import org.linphone.core.LinphoneFriendImpl;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.mediastream.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

interface ContactsUpdatedListener {
	void onContactsUpdated();
}

public class ContactsManager extends ContentObserver {
	private static ContactsManager instance;

	private List<LinphoneContact> contacts, sipContacts;
	private boolean preferLinphoneContacts = false, isContactPresenceDisabled = true;
	private ContentResolver contentResolver;
	private Context context;
	private HashMap<String, LinphoneContact> androidContactsCache;
	private Bitmap defaultAvatar;
	private Handler handler;

	private static ArrayList<ContactsUpdatedListener> contactsUpdatedListeners;
	public static void addContactsListener(ContactsUpdatedListener listener) {
		contactsUpdatedListeners.add(listener);
	}
	public static void removeContactsListener(ContactsUpdatedListener listener) {
		contactsUpdatedListeners.remove(listener);
	}

	private ContactsManager(Handler handler) {
		super(handler);
		this.handler = handler;
		defaultAvatar = BitmapFactory.decodeResource(LinphoneService.instance().getResources(), R.drawable.avatar);
		androidContactsCache = new HashMap<String, LinphoneContact>();
		contactsUpdatedListeners = new ArrayList<ContactsUpdatedListener>();
		contacts = new ArrayList<LinphoneContact>();
		sipContacts = new ArrayList<LinphoneContact>();
	}

	public void destroy() {
		defaultAvatar.recycle();
		instance = null;
	}

	public boolean contactsFetchedOnce() {
		return contacts.size() > 0;
	}

	public Bitmap getDefaultAvatarBitmap() {
		return defaultAvatar;
	}

	@Override
	public void onChange(boolean selfChange) {
		 onChange(selfChange, null);
	}

	@Override
	public void onChange(boolean selfChange, Uri uri) {
		fetchContactsSync();
	}

	public ContentResolver getContentResolver() {
		return contentResolver;
	}

	public static final ContactsManager getInstance() {
		if (instance == null) instance = new ContactsManager(LinphoneService.instance().mHandler);
		return instance;
	}

	public synchronized boolean hasContacts() {
		return contacts.size() > 0;
	}

	public synchronized List<LinphoneContact> getContacts() {
		return contacts;
	}

	public synchronized List<LinphoneContact> getSIPContacts() {
		return sipContacts;
	}

	public synchronized List<LinphoneContact> getContacts(String search) {
		search = search.toLowerCase(Locale.getDefault());
		List<LinphoneContact> searchContactsBegin = new ArrayList<LinphoneContact>();
		List<LinphoneContact> searchContactsContain = new ArrayList<LinphoneContact>();
		for (LinphoneContact contact : contacts) {
			if (contact.getFullName() != null) {
				if (contact.getFullName().toLowerCase(Locale.getDefault()).startsWith(search)) {
					searchContactsBegin.add(contact);
				} else if (contact.getFullName().toLowerCase(Locale.getDefault()).contains(search)) {
					searchContactsContain.add(contact);
				}
			}
		}
		searchContactsBegin.addAll(searchContactsContain);
		return searchContactsBegin;
	}

	public synchronized List<LinphoneContact> getSIPContacts(String search) {
		search = search.toLowerCase(Locale.getDefault());
		List<LinphoneContact> searchContactsBegin = new ArrayList<LinphoneContact>();
		List<LinphoneContact> searchContactsContain = new ArrayList<LinphoneContact>();
		for (LinphoneContact contact : sipContacts) {
			if (contact.getFullName() != null) {
				if (contact.getFullName().toLowerCase(Locale.getDefault()).startsWith(search)) {
					searchContactsBegin.add(contact);
				} else if (contact.getFullName().toLowerCase(Locale.getDefault()).contains(search)) {
					searchContactsContain.add(contact);
				}
			}
		}
		searchContactsBegin.addAll(searchContactsContain);
		return searchContactsBegin;
	}

	public void enableContactsAccess() {
		LinphonePreferences.instance().disableFriendsStorage();
	}

	public boolean hasContactsAccess() {
		if (context == null)
			return false;
		boolean contactsR = (PackageManager.PERMISSION_GRANTED ==
				context.getPackageManager().checkPermission(android.Manifest.permission.READ_CONTACTS, context.getPackageName()));
		context.getPackageManager();
		return contactsR && !context.getResources().getBoolean(R.bool.force_use_of_linphone_friends);
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
		initializeContactManager(context, contentResolver);
		AccountManager accountManager = (AccountManager) context.getSystemService(Context.ACCOUNT_SERVICE);

		Account[] accounts = accountManager.getAccountsByType(context.getPackageName());

		if (accounts != null && accounts.length == 0) {
			Account newAccount = new Account(context.getString(R.string.sync_account_name), context.getPackageName());
			try {
				accountManager.addAccountExplicitly(newAccount, null, null);
			} catch (Exception e) {
				Log.e(e);
			}
		}
		initializeContactManager(context, contentResolver);
	}

	public synchronized LinphoneContact findContactFromAddress(LinphoneAddress address) {
		String sipUri = address.asStringUriOnly();
		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		LinphoneFriend lf = lc.findFriendByAddress(sipUri);
		if (lf != null) {
			LinphoneContact contact = (LinphoneContact)((LinphoneFriendImpl)lf).getUserData();
			return contact;
		}
		return null;
	}

	public synchronized LinphoneContact findContactFromPhoneNumber(String phoneNumber) {
		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		LinphoneProxyConfig lpc = null;
		if (lc != null) {
			lpc = lc.getDefaultProxyConfig();
		}
		if (lpc == null) return null;
		String normalized = lpc.normalizePhoneNumber(phoneNumber);

		LinphoneAddress addr = lpc.normalizeSipUri(normalized);
		LinphoneFriend lf = lc.findFriendByAddress(addr.asStringUriOnly() + ";user=phone"); // Without this, the hashmap inside liblinphone won't find it...
		if (lf != null) {
			LinphoneContact contact = (LinphoneContact)((LinphoneFriendImpl)lf).getUserData();
			return contact;
		}
		return null;
	}

	public synchronized void setContacts(List<LinphoneContact> c) {
		contacts = c;
	}

	public synchronized void setSipContacts(List<LinphoneContact> c) {
		if (sipContacts.isEmpty()) {
			sipContacts = c;
		} else {
			for (LinphoneContact contact : c) {
				if (!sipContacts.contains(contact)) {
					sipContacts.add(contact);
				}
			}
		}
	}

	public synchronized void refreshSipContact(LinphoneFriend lf) {
		LinphoneContact contact = (LinphoneContact)((LinphoneFriendImpl)lf).getUserData();
		if (contact != null && !sipContacts.contains(contact)) {
			sipContacts.add(contact);
			Collections.sort(sipContacts);
			for (ContactsUpdatedListener listener : contactsUpdatedListeners) {
				listener.onContactsUpdated();
			}
		}
	}

	public void fetchContactsAsync() {
		handler.post(new Runnable() {
			@Override
			public void run() {
				fetchContactsSync();
			}
		});
	}

	private synchronized void fetchContactsSync() {
		List<LinphoneContact> contacts = new ArrayList<LinphoneContact>();
		List<LinphoneContact> sipContacts = new ArrayList<LinphoneContact>();
		Date contactsTime = new Date();
		androidContactsCache.clear();

		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			for (LinphoneFriend friend : lc.getFriendList()) {
				LinphoneContact contact = (LinphoneContact)((LinphoneFriendImpl)friend).getUserData();
				if (contact != null) {
					contact.clearAddresses();
					contacts.add(contact);
					if (contact.getAndroidId() != null) {
						androidContactsCache.put(contact.getAndroidId(), contact);
					}
				} else {
					if (friend.getRefKey() != null) {
						// Friend has a refkey and but no LinphoneContact => represents a native contact stored in db from a previous version of Linphone, remove it
						lc.removeFriend(friend);
					} else {
						// No refkey so it's a standalone contact
						contact = new LinphoneContact();
						contact.setFriend(friend);
						contact.refresh();
						if (contact.hasAddress()) {
							sipContacts.add(contact);
						}
						contacts.add(contact);
					}
				}
			}
		}

		long timeElapsed = (new Date()).getTime() - contactsTime.getTime();
		String time = String.format("%02d:%02d",
			    TimeUnit.MILLISECONDS.toMinutes(timeElapsed),
			    TimeUnit.MILLISECONDS.toSeconds(timeElapsed) -
			    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(timeElapsed)));
		Log.i("[ContactsManager] Step 0 for " + contacts.size() + " contacts: " + time + " elapsed since starting");

		if (hasContactsAccess()) {
			List<String> nativeIds = new ArrayList<String>();
			Cursor c = getContactsCursor(contentResolver);
			if (c != null) {
				while (c.moveToNext()) {
					String id = c.getString(c.getColumnIndex(Data.CONTACT_ID));
			    	String displayName = c.getString(c.getColumnIndex(Data.DISPLAY_NAME_PRIMARY));

			    	nativeIds.add(id);
			    	boolean created = false;
			    	LinphoneContact contact = androidContactsCache.get(id);
			    	if (contact == null) {
			    		created = true;
			    		contact = new LinphoneContact();
						contact.setAndroidId(id);
			    	}

					contact.setFullName(displayName);
					/*contact.getAndroidIds();*/
					if (created) {
						contacts.add(contact);
						androidContactsCache.put(id, contact);
					}
				}
				c.close();
			}

			boolean isOrgVisible = LinphoneManager.getInstance().getContext().getResources().getBoolean(R.bool.display_contact_organization);
			if (isOrgVisible) {
				c = getOrganizationCursor(contentResolver);
				if (c != null) {
					while (c.moveToNext()) {
				    	String id = c.getString(c.getColumnIndex(ContactsContract.Data.CONTACT_ID));
				    	String org = c.getString(c.getColumnIndex(ContactsContract.CommonDataKinds.Organization.COMPANY));
				    	LinphoneContact contact = androidContactsCache.get(id);
				    	if (contact != null) {
				    		contact.setOrganization(org);
				    	}
					}
					c.close();
				}
			}

			for (LinphoneContact contact : androidContactsCache.values()) {
				String id = contact.getAndroidId();
				if (id != null && !nativeIds.contains(id)) {
					// Has been removed since last fetch
					lc.removeFriend(contact.getLinphoneFriend());
					contacts.remove(contact);
				}
			}
			nativeIds.clear();

			timeElapsed = (new Date()).getTime() - contactsTime.getTime();
			time = String.format("%02d:%02d",
				    TimeUnit.MILLISECONDS.toMinutes(timeElapsed),
				    TimeUnit.MILLISECONDS.toSeconds(timeElapsed) -
				    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(timeElapsed)));
			Log.i("[ContactsManager] Step 1 for " + contacts.size() + " contacts: " + time + " elapsed since starting");

			c = getPhonesCursor(contentResolver);
			if (c != null) {
				while (c.moveToNext()) {
					String id = c.getString(c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID));
			    	String number = c.getString(c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
			    	LinphoneContact contact = androidContactsCache.get(id);
			    	if (contact != null) {
			    		contact.addNumberOrAddress(new LinphoneNumberOrAddress(number, false));
			    	}
				}
				c.close();
			}
			c = getSipCursor(contentResolver);
			if (c != null) {
				while (c.moveToNext()) {
					String id = c.getString(c.getColumnIndex(ContactsContract.Data.CONTACT_ID));
			    	String sip = c.getString(c.getColumnIndex(ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS));
			    	LinphoneContact contact = androidContactsCache.get(id);
			    	if (contact != null) {
			    		contact.addNumberOrAddress(new LinphoneNumberOrAddress(sip, true));
			    		if (!sipContacts.contains(contact)) {
			    			sipContacts.add(contact);
			    		}
			    	}
				}
				c.close();
			}

			timeElapsed = (new Date()).getTime() - contactsTime.getTime();
			time = String.format("%02d:%02d",
				    TimeUnit.MILLISECONDS.toMinutes(timeElapsed),
				    TimeUnit.MILLISECONDS.toSeconds(timeElapsed) -
				    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(timeElapsed)));
			Log.i("[ContactsManager] Step 2 for " + contacts.size() + " contacts: " + time + " elapsed since starting");

			for (LinphoneContact contact : contacts) {
				// Create the LinphoneFriends matching the native contacts
				contact.createOrUpdateLinphoneFriendFromNativeContact();
			}
			timeElapsed = (new Date()).getTime() - contactsTime.getTime();
			time = String.format("%02d:%02d",
				    TimeUnit.MILLISECONDS.toMinutes(timeElapsed),
				    TimeUnit.MILLISECONDS.toSeconds(timeElapsed) -
				    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(timeElapsed)));
			Log.i("[ContactsManager] Step 3 for " + contacts.size() + " contacts: " + time + " elapsed since starting");

			androidContactsCache.clear();
		} else {
			Log.w("[Permission] Read contacts permission wasn't granted, only fetch LinphoneFriends");
		}

		Collections.sort(contacts);
		Collections.sort(sipContacts);
		setContacts(contacts);
		setSipContacts(sipContacts);

		if (LinphonePreferences.instance() != null && LinphonePreferences.instance().isFriendlistsubscriptionEnabled()) {
			if (getString(R.string.rls_uri) != null) {
				LinphoneManager.getLc().getFriendLists()[0].setRLSUri(getString(R.string.rls_uri));
			}
			LinphoneManager.getLc().getFriendLists()[0].updateSubscriptions();
		}
		for (ContactsUpdatedListener listener : contactsUpdatedListeners) {
			listener.onContactsUpdated();
		}
	}

	public static String getAddressOrNumberForAndroidContact(ContentResolver resolver, Uri contactUri) {
		// Phone Numbers
		String[] projection = new String[] { ContactsContract.CommonDataKinds.Phone.NUMBER };
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

	public String getString(int resourceID) {
		if (context == null) return null;
		return context.getString(resourceID);
	}

	private Cursor getContactsCursor(ContentResolver cr) {
		String req = "(" + Data.MIMETYPE + " = '" + CommonDataKinds.Phone.CONTENT_ITEM_TYPE
				+ "' AND " + CommonDataKinds.Phone.NUMBER + " IS NOT NULL "
				+ " OR (" + Data.MIMETYPE + " = '" + CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE
				+ "' AND " + CommonDataKinds.SipAddress.SIP_ADDRESS + " IS NOT NULL))";
		String[] projection = new String[] { Data.CONTACT_ID, Data.DISPLAY_NAME_PRIMARY };
		String query = Data.DISPLAY_NAME_PRIMARY + " IS NOT NULL AND (" + req + ")";

		Cursor cursor = cr.query(Data.CONTENT_URI, projection, query, null, " lower(" + Data.DISPLAY_NAME_PRIMARY + ") COLLATE UNICODE ASC");
		if (cursor == null) {
			return cursor;
		}

		MatrixCursor result = new MatrixCursor(cursor.getColumnNames());
		Set<String> groupBy = new HashSet<String>();
		while (cursor.moveToNext()) {
		    String name = cursor.getString(cursor.getColumnIndex(Data.DISPLAY_NAME_PRIMARY));
		    if (!groupBy.contains(name)) {
		    	groupBy.add(name);
		    	Object[] newRow = new Object[cursor.getColumnCount()];

		    	int contactID = cursor.getColumnIndex(Data.CONTACT_ID);
		    	int displayName = cursor.getColumnIndex(Data.DISPLAY_NAME_PRIMARY);

		    	newRow[contactID] = cursor.getString(contactID);
		    	newRow[displayName] = cursor.getString(displayName);
		        result.addRow(newRow);
	    	}
	    }
		cursor.close();
		return result;
	}

	private Cursor getPhonesCursor(ContentResolver cr) {
		Cursor cursor = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
			     new String[] { ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.CONTACT_ID },
			     null, null, ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " ASC");
		return cursor;
	}

	private Cursor getSipCursor(ContentResolver cr) {
		String select = ContactsContract.Data.MIMETYPE + "=?";
		String[] projection = new String[] { ContactsContract.Data.CONTACT_ID, ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS };
		Cursor c = cr.query(ContactsContract.Data.CONTENT_URI, projection, select, new String[]{ ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE }, null);
		return c;
	}

	private Cursor getOrganizationCursor(ContentResolver cr) {
		String select = ContactsContract.Data.MIMETYPE + "=?";
		String[] projection = new String[] { ContactsContract.Data.CONTACT_ID, ContactsContract.CommonDataKinds.Organization.COMPANY };
		Cursor c = cr.query(ContactsContract.Data.CONTENT_URI, projection, select, new String[]{ ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE }, null);
		return c;
	}
}
