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
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

package org.linphone;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Data;

interface ContactsUpdatedListener {
	void onContactsUpdated();
}

public class ContactsManager extends ContentObserver {
	public static int CONTACTS_STEP = 15;
	private static ContactsManager instance;
	private List<LinphoneContact> contacts, sipContacts;
	private boolean preferLinphoneContacts = false, isContactPresenceDisabled = true, hasContactAccess = false;
	private ContentResolver contentResolver;
	private Context context;
	private ContactsFetchTask contactsFetchTask;
	private HashMap<String, LinphoneContact> contactsCache;
	private LinphoneContact contactNotFound;
	private Bitmap defaultAvatar;

	private static ArrayList<ContactsUpdatedListener> contactsUpdatedListeners;
	public static void addContactsListener(ContactsUpdatedListener listener) {
		contactsUpdatedListeners.add(listener);
	}
	public static void removeContactsListener(ContactsUpdatedListener listener) {
		contactsUpdatedListeners.remove(listener);
	}

	private static Handler handler = new Handler() {
		@Override
		public void handleMessage (Message msg) {

		}
	};

	private ContactsManager(Handler handler) {
		super(handler);
		defaultAvatar = BitmapFactory.decodeResource(LinphoneService.instance().getResources(), R.drawable.avatar);
		contactNotFound = new LinphoneContact();
		contactsCache = new HashMap<String, LinphoneContact>();
		contactsUpdatedListeners = new ArrayList<ContactsUpdatedListener>();
		contacts = new ArrayList<LinphoneContact>();
		sipContacts = new ArrayList<LinphoneContact>();
	}

	public void destroy() {
		if (contactsFetchTask != null && !contactsFetchTask.isCancelled()) {
			contactsFetchTask.cancel(true);
		}
		defaultAvatar.recycle();
		instance = null;
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
		fetchContactsAsync();
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
		hasContactAccess = true;
		LinphonePreferences.instance().disableFriendsStorage();
	}

	public boolean hasContactsAccess() {
		if (context == null)
			return false;
		int contacts = context.getPackageManager().checkPermission(android.Manifest.permission.READ_CONTACTS, context.getPackageName());
		context.getPackageManager();
		return contacts == PackageManager.PERMISSION_GRANTED && !context.getResources().getBoolean(R.bool.force_use_of_linphone_friends);
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
		String username = address.getUserName();

		LinphoneContact cache = contactsCache.get(sipUri);
		if (cache != null) {
			if (cache == contactNotFound) return null;
			return cache;
		}

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
				String alias = c.getPresenceModelForUri(noa.getValue());

				if ((noa.isSIPAddress() && noa.getValue().equals(sipUri)) || (alias != null && alias.equals(sipUri)) || (normalized != null && !noa.isSIPAddress() && normalized.equals(username)) || (!noa.isSIPAddress() && noa.getValue().equals(username))) {
					contactsCache.put(sipUri, c);
					return c;
				}
			}
		}
		contactsCache.put(sipUri, contactNotFound);
		return null;
	}

	public synchronized LinphoneContact findContactFromPhoneNumber(String phoneNumber) {
		LinphoneContact cache = contactsCache.get(phoneNumber);
		if (cache != null) {
			if (cache == contactNotFound) return null;
			return cache;
		}

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
				if (noa.getValue().equals(phoneNumber) || (normalized != null && normalized.equals(phoneNumber))) {
					contactsCache.put(phoneNumber, c);
					return c;
				}
			}
		}
		contactsCache.put(phoneNumber, contactNotFound);
		return null;
	}

	public synchronized void setContacts(List<LinphoneContact> c) {
		contacts = c;
		sipContacts = new ArrayList<LinphoneContact>();
		for (LinphoneContact contact : contacts) {
			if (contact.hasAddress() || contact.isInLinphoneFriendList()) {
				sipContacts.add(contact);
			}
		}
	}

	public synchronized void fetchContactsAsync() {
		if (contactsFetchTask != null && !contactsFetchTask.isCancelled()) {
			contactsFetchTask.cancel(true);
		}
		contactsFetchTask = new ContactsFetchTask();
		contactsFetchTask.execute();
	}

	private class ContactsFetchTask extends AsyncTask<Void, List<LinphoneContact>, List<LinphoneContact>> {
		@SuppressWarnings("unchecked")
		protected List<LinphoneContact> doInBackground(Void... params) {
			List<LinphoneContact> contacts = new ArrayList<LinphoneContact>();
			Date contactsTime = new Date();

			//We need to check sometimes to know if Linphone was destroyed
			if (this.isCancelled()) {
				return null;
			}

			if (hasContactsAccess()) {
				Cursor c = getContactsCursor(contentResolver);
				if (c != null) {
					while (c.moveToNext()) {
						String id = c.getString(c.getColumnIndex(Data.CONTACT_ID));
				    	String displayName = c.getString(c.getColumnIndex(Data.DISPLAY_NAME));
						LinphoneContact contact = new LinphoneContact();
						contact.setFullName(displayName);
						contact.setAndroidId(id);
						contacts.add(contact);
					}
					c.close();
				}
			} else {
				Log.w("[Permission] Read contacts permission wasn't granted, only fetch LinphoneFriends");
			}
			long timeElapsed = (new Date()).getTime() - contactsTime.getTime();
			String time = String.format("%02d:%02d",
				    TimeUnit.MILLISECONDS.toMinutes(timeElapsed),
				    TimeUnit.MILLISECONDS.toSeconds(timeElapsed) -
				    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(timeElapsed)));
			Log.i("[ContactsManager] got " + contacts.size() + " contacts names and pictures in " + time);
			
			//We need to check sometimes to know if Linphone was destroyed
			if (this.isCancelled()) {
				return null;
			}
			LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
			if (lc != null) {
				for (LinphoneFriend friend : lc.getFriendList()) {
					String refkey = friend.getRefKey();
					if (refkey != null) {
						boolean found = false;
						for (LinphoneContact contact : contacts) {
							if (refkey.equals(contact.getAndroidId())) {
								// Native matching contact found, link the friend to it
								contact.setFriend(friend);
								found = true;
								break;
							}
						}
						if (!found) {
							if (hasContactAccess) {
								// If refkey != null and hasContactAccess but there isn't a native contact with this value, then this contact has been deleted. Let's do the same with the LinphoneFriend
								lc.removeFriend(friend);
							} else {
								// Refkey not null but no contact access => can't link it to native contact so display it on is own
								LinphoneContact contact = new LinphoneContact();
								contact.setFriend(friend);
								contacts.add(contact);
							}
						} else {
							// Now that we no longer store friends in database that match one in the system, let's remove it
							lc.removeFriend(friend);
						}
					} else {
						// No refkey so it's a standalone contact
						LinphoneContact contact = new LinphoneContact();
						contact.setFriend(friend);
						contacts.add(contact);
					}
				}
			}

			int i = 0;
			
			for (LinphoneContact contact : contacts) {
				//We need to check sometimes to know if Linphone was destroyed
				if (this.isCancelled()) {
					return null;
				}
				// This will only get name & picture informations to be able to quickly display contacts list
				contact.minimalRefresh();
				i++;
				
				if (i == CONTACTS_STEP) {
					i = 0;
					publishProgress(contacts);
				}
			}
			Collections.sort(contacts);

			timeElapsed = (new Date()).getTime() - contactsTime.getTime();
			time = String.format("%02d:%02d",
				    TimeUnit.MILLISECONDS.toMinutes(timeElapsed),
				    TimeUnit.MILLISECONDS.toSeconds(timeElapsed) -
				    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(timeElapsed)));
			Log.i("[ContactsManager] organization, SIP and phone numbers for " + contacts.size() + " contacts fetched in " + time);
			// Public the current list of contacts without all the informations populated
			publishProgress(contacts);

			for (LinphoneContact contact : contacts) {
				//We need to check sometimes to know if Linphone was destroyed
				if (this.isCancelled()) {
					return null;
				}
				// This time fetch all informations including phone numbers and SIP addresses
				contact.refresh();
			}
			timeElapsed = (new Date()).getTime() - contactsTime.getTime();
			time = String.format("%02d:%02d",
				    TimeUnit.MILLISECONDS.toMinutes(timeElapsed),
				    TimeUnit.MILLISECONDS.toSeconds(timeElapsed) -
				    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(timeElapsed)));
			Log.i("[ContactsManager] linphone friends for " + contacts.size() + " contacts created in " + time);

			return contacts;
		}

		protected void onProgressUpdate(List<LinphoneContact>... result) {
			synchronized (ContactsManager.this) {
				setContacts(result[0]);
				contactsCache.clear();
				for (ContactsUpdatedListener listener : contactsUpdatedListeners) {
					listener.onContactsUpdated();
				}
			}
		}

		protected void onPostExecute(List<LinphoneContact> result) {
			for (ContactsUpdatedListener listener : contactsUpdatedListeners) {
				listener.onContactsUpdated();
			}
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
		return context.getString(resourceID);
	}

	private Cursor getContactsCursor(ContentResolver cr) {
		String req = "(" + Data.MIMETYPE + " = '" + CommonDataKinds.Phone.CONTENT_ITEM_TYPE
				+ "' AND " + CommonDataKinds.Phone.NUMBER + " IS NOT NULL "
				+ " OR (" + Data.MIMETYPE + " = '" + CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE
				+ "' AND " + CommonDataKinds.SipAddress.SIP_ADDRESS + " IS NOT NULL))";
		String[] projection = new String[] { Data.CONTACT_ID, Data.DISPLAY_NAME };
		String query = Data.DISPLAY_NAME + " IS NOT NULL AND (" + req + ")";

		Cursor cursor = cr.query(Data.CONTENT_URI, projection, query, null, " lower(" + Data.DISPLAY_NAME + ") COLLATE UNICODE ASC");
		if (cursor == null) {
			return cursor;
		}

		MatrixCursor result = new MatrixCursor(cursor.getColumnNames());
		Set<String> groupBy = new HashSet<String>();
		while (cursor.moveToNext()) {
		    String name = cursor.getString(cursor.getColumnIndex(Data.DISPLAY_NAME));
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
}
