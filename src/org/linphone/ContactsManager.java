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
import org.linphone.core.LinphoneFriend;
import org.linphone.mediastream.Log;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Data;

interface ContactsUpdatedListener {
	void onContactsUpdated();
}

public class ContactsManager extends ContentObserver {
	private static final int CONTACTS_UPDATED = 543;
	
	private static ContactsManager instance;
	private List<LinphoneContact> contacts;
	//private Cursor contactCursor, sipContactCursor;
	private Account mAccount;
	private boolean preferLinphoneContacts = false, isContactPresenceDisabled = true, hasContactAccess = false;
	private ContentResolver contentResolver;
	private static Handler handler = new Handler() {
		@SuppressWarnings("unchecked")
		@Override
		public void handleMessage (Message msg) {
			if (msg.what == CONTACTS_UPDATED && msg.obj instanceof List<?>) {
				List<LinphoneContact> c = (List<LinphoneContact>) msg.obj;
				ContactsManager.getInstance().setContacts(c);
			}
		}
	};

	private ContactsManager(Handler handler) {
		super(handler);
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
	
	public boolean hasContacts() {
		return contacts.size() > 0;
	}
	
	public List<LinphoneContact> getContacts() {
		return contacts;
	}
	
	public List<LinphoneContact> getSIPContacts() {
		return contacts;
	}
	
	public void enableContactsAccess() {
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
	
	public LinphoneContact findContactFromAddress(LinphoneAddress address) {
		String sipUri = address.asStringUriOnly();
		String username = address.getUserName();
		
		for (LinphoneContact c: getContacts()) {
			for (LinphoneNumberOrAddress noa: c.getNumbersOrAddresses()) {
				if ((noa.isSIPAddress() && noa.getValue().equals(sipUri)) || (!noa.isSIPAddress() && noa.getValue().equals(username))) {
					return c;
				}
			}
		}
		return null;
	}
	
	public synchronized void setContacts(List<LinphoneContact> c) {
		contacts = c;
	}

	public synchronized void fetchContacts() {
		contacts = new ArrayList<LinphoneContact>();
		
		for (LinphoneFriend friend : LinphoneManager.getLc().getFriendList()) {
			LinphoneContact contact = new LinphoneContact();
			LinphoneAddress addr = friend.getAddress();
			contact.setFullName(addr.getDisplayName());
			contact.addNumberOrAddress(new LinphoneNumberOrAddress(addr.asStringUriOnly(), true));
			contacts.add(contact);
		}

		if (mAccount == null || !hasContactAccess) return;
		
		Cursor c = Compatibility.getContactsCursor(contentResolver, null);
		if (c != null) {
			while (c.moveToNext()) {
				String id = c.getString(c.getColumnIndex(Data.CONTACT_ID));
				LinphoneContact contact = new LinphoneContact();
				contact.setAndroidId(id);
				contact.refresh();
				contacts.add(contact);
			}
			c.close();
		}
	}
	
	public List<LinphoneContact> fetchContactsAsync() {
		List<LinphoneContact> contacts = new ArrayList<LinphoneContact>();
		
		for (LinphoneFriend friend : LinphoneManager.getLc().getFriendList()) {
			LinphoneContact contact = new LinphoneContact();
			LinphoneAddress addr = friend.getAddress();
			contact.setFullName(addr.getDisplayName());
			contact.addNumberOrAddress(new LinphoneNumberOrAddress(addr.asStringUriOnly(), true));
			contacts.add(contact);
		}

		if (mAccount == null || !hasContactAccess) return contacts;
		
		Cursor c = Compatibility.getContactsCursor(contentResolver, null);
		if (c != null) {
			while (c.moveToNext()) {
				String id = c.getString(c.getColumnIndex(Data.CONTACT_ID));
				LinphoneContact contact = new LinphoneContact();
				contact.setAndroidId(id);
				contact.refresh();
				contacts.add(contact);
			}
			c.close();
		}
		
		return contacts;
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
}
