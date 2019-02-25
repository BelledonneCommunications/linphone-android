package org.linphone.contacts;

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

import static android.os.AsyncTask.THREAD_POOL_EXECUTOR;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.ContactsContract;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.LinphoneService;
import org.linphone.R;
import org.linphone.compatibility.Compatibility;
import org.linphone.core.Address;
import org.linphone.core.Core;
import org.linphone.core.Friend;
import org.linphone.core.FriendList;
import org.linphone.core.FriendListListener;
import org.linphone.core.MagicSearch;
import org.linphone.core.ProxyConfig;
import org.linphone.core.tools.Log;
import org.linphone.settings.LinphonePreferences;

public class ContactsManager extends ContentObserver implements FriendListListener {
    private static ContactsManager sInstance;

    private List<LinphoneContact> mContacts, mSipContacts;
    private ArrayList<ContactsUpdatedListener> mContactsUpdatedListeners;
    private MagicSearch mMagicSearch;
    private boolean mContactsFetchedOnce = false;
    private Context mContext;
    private AsyncContactsLoader mLoadContactTask;
    private boolean mInitialized = false;

    public static ContactsManager getInstance() {
        if (sInstance == null) sInstance = new ContactsManager();
        return sInstance;
    }

    private ContactsManager() {
        super(LinphoneService.instance().handler);
        mContactsUpdatedListeners = new ArrayList<>();
        mContacts = new ArrayList<>();
        mSipContacts = new ArrayList<>();

        if (LinphoneManager.getLcIfManagerNotDestroyedOrNull() != null) {
            mMagicSearch = LinphoneManager.getLcIfManagerNotDestroyedOrNull().createMagicSearch();
            mMagicSearch.setLimitedSearch(false); // Do not limit the number of results
        }
    }

    public void addContactsListener(ContactsUpdatedListener listener) {
        mContactsUpdatedListeners.add(listener);
    }

    public void removeContactsListener(ContactsUpdatedListener listener) {
        mContactsUpdatedListeners.remove(listener);
    }

    public ArrayList<ContactsUpdatedListener> getContactsListeners() {
        return mContactsUpdatedListeners;
    }

    @Override
    public void onChange(boolean selfChange) {
        onChange(selfChange, null);
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        fetchContactsAsync();
    }

    public synchronized List<LinphoneContact> getContacts() {
        return mContacts;
    }

    synchronized void setContacts(List<LinphoneContact> c) {
        mContacts = c;
    }

    public synchronized List<LinphoneContact> getSIPContacts() {
        return mSipContacts;
    }

    synchronized void setSipContacts(List<LinphoneContact> c) {
        mSipContacts = c;
    }

    public void destroy() {
        if (mLoadContactTask != null) {
            mLoadContactTask.cancel(true);
        }
        // LinphoneContact has a Friend field and Friend can have a LinphoneContact has userData
        // Friend also keeps a ref on the Core, so we have to clean them
        for (LinphoneContact c : mContacts) {
            c.setFriend(null);
        }
        mContacts.clear();
        for (LinphoneContact c : mSipContacts) {
            c.setFriend(null);
        }
        mSipContacts.clear();

        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            for (FriendList list : lc.getFriendsLists()) {
                list.removeListener(this);
            }
        }
        sInstance = null;
    }

    public void fetchContactsAsync() {
        if (mLoadContactTask != null) {
            mLoadContactTask.cancel(true);
        }
        if (!hasReadContactsAccess()) {
            Log.w("[Contacts Manager] Can't fetch contact without READ permission");
            return;
        }
        mLoadContactTask = new AsyncContactsLoader(mContext);
        mContactsFetchedOnce = true;
        mLoadContactTask.executeOnExecutor(THREAD_POOL_EXECUTOR);
    }

    public void editContact(Context context, LinphoneContact contact, String valueToAdd) {
        if (context.getResources().getBoolean(R.bool.use_native_contact_editor)) {
            Intent intent = new Intent(Intent.ACTION_EDIT);
            Uri contactUri = contact.getAndroidLookupUri();
            intent.setDataAndType(contactUri, ContactsContract.Contacts.CONTENT_ITEM_TYPE);
            intent.putExtra(
                    "finishActivityOnSaveCompleted", true); // So after save will go back here
            if (valueToAdd != null) {
                intent.putExtra(ContactsContract.Intents.Insert.IM_HANDLE, valueToAdd);
            }
            context.startActivity(intent);
        } else {
            LinphoneActivity.instance().editContact(contact, valueToAdd);
        }
    }

    public void createContact(Context context, String name, String valueToAdd) {
        if (context.getResources().getBoolean(R.bool.use_native_contact_editor)) {
            Intent intent = new Intent(ContactsContract.Intents.Insert.ACTION);
            intent.setType(ContactsContract.RawContacts.CONTENT_TYPE);
            intent.putExtra(
                    "finishActivityOnSaveCompleted", true); // So after save will go back here
            if (name != null) {
                intent.putExtra(ContactsContract.Intents.Insert.NAME, name);
            }
            if (valueToAdd != null) {
                intent.putExtra(ContactsContract.Intents.Insert.IM_HANDLE, valueToAdd);
            }
            context.startActivity(intent);
        } else {
            LinphoneActivity.instance().addContact(name, valueToAdd);
        }
    }

    public MagicSearch getMagicSearch() {
        return mMagicSearch;
    }

    public boolean contactsFetchedOnce() {
        return mContactsFetchedOnce;
    }

    public List<LinphoneContact> getContacts(String search) {
        search = search.toLowerCase(Locale.getDefault());
        List<LinphoneContact> searchContactsBegin = new ArrayList<>();
        List<LinphoneContact> searchContactsContain = new ArrayList<>();
        for (LinphoneContact contact : getContacts()) {
            if (contact.getFullName() != null) {
                if (contact.getFullName().toLowerCase(Locale.getDefault()).startsWith(search)) {
                    searchContactsBegin.add(contact);
                } else if (contact.getFullName()
                        .toLowerCase(Locale.getDefault())
                        .contains(search)) {
                    searchContactsContain.add(contact);
                }
            }
        }
        searchContactsBegin.addAll(searchContactsContain);
        return searchContactsBegin;
    }

    public List<LinphoneContact> getSIPContacts(String search) {
        search = search.toLowerCase(Locale.getDefault());
        List<LinphoneContact> searchContactsBegin = new ArrayList<>();
        List<LinphoneContact> searchContactsContain = new ArrayList<>();
        for (LinphoneContact contact : getSIPContacts()) {
            if (contact.getFullName() != null) {
                if (contact.getFullName().toLowerCase(Locale.getDefault()).startsWith(search)) {
                    searchContactsBegin.add(contact);
                } else if (contact.getFullName()
                        .toLowerCase(Locale.getDefault())
                        .contains(search)) {
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

    public boolean hasReadContactsAccess() {
        if (mContext == null) {
            return false;
        }
        boolean contactsR =
                (PackageManager.PERMISSION_GRANTED
                        == mContext.getPackageManager()
                                .checkPermission(
                                        android.Manifest.permission.READ_CONTACTS,
                                        mContext.getPackageName()));
        return contactsR
                && !mContext.getResources().getBoolean(R.bool.force_use_of_linphone_friends);
    }

    public boolean hasWriteContactsAccess() {
        if (mContext == null) {
            return false;
        }
        return (PackageManager.PERMISSION_GRANTED
                == mContext.getPackageManager()
                        .checkPermission(
                                Manifest.permission.WRITE_CONTACTS, mContext.getPackageName()));
    }

    public boolean hasWriteSyncPermission() {
        if (mContext == null) {
            return false;
        }
        return (PackageManager.PERMISSION_GRANTED
                == mContext.getPackageManager()
                        .checkPermission(
                                Manifest.permission.WRITE_SYNC_SETTINGS,
                                mContext.getPackageName()));
    }

    public boolean isLinphoneContactsPrefered() {
        ProxyConfig lpc = LinphoneManager.getLc().getDefaultProxyConfig();
        return lpc != null
                && lpc.getIdentityAddress()
                        .getDomain()
                        .equals(mContext.getString(R.string.default_domain));
    }

    public void initializeContactManager(Context context) {
        mContext = context;

        if (!mInitialized) {
            if (mContext.getResources().getBoolean(R.bool.use_linphone_tag)) {
                if (hasReadContactsAccess()
                        && hasWriteContactsAccess()
                        && hasWriteSyncPermission()) {
                    if (LinphoneService.isReady()) {
                        ContactsManager.getInstance().initializeSyncAccount();
                        mInitialized = true;
                    }
                }
            }
        }

        if (mContext != null && getContacts().size() == 0 && hasReadContactsAccess()) {
            fetchContactsAsync();
        }
    }

    private void makeContactAccountVisible() {
        ContentProviderClient client =
                mContext.getContentResolver()
                        .acquireContentProviderClient(ContactsContract.AUTHORITY_URI);
        ContentValues values = new ContentValues();
        values.put(
                ContactsContract.Settings.ACCOUNT_NAME,
                mContext.getString(R.string.sync_account_name));
        values.put(
                ContactsContract.Settings.ACCOUNT_TYPE,
                mContext.getString(R.string.sync_account_type));
        values.put(ContactsContract.Settings.UNGROUPED_VISIBLE, true);
        try {
            client.insert(
                    ContactsContract.Settings.CONTENT_URI
                            .buildUpon()
                            .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
                            .build(),
                    values);
            Log.i("[Contacts Manager] Contacts account made visible");
        } catch (RemoteException e) {
            Log.e("[Contacts Manager] Couldn't make contacts account visible: " + e);
        }
        Compatibility.closeContentProviderClient(client);
    }

    private void initializeSyncAccount() {
        AccountManager accountManager =
                (AccountManager) mContext.getSystemService(Context.ACCOUNT_SERVICE);

        Account[] accounts =
                accountManager.getAccountsByType(mContext.getString(R.string.sync_account_type));

        if (accounts != null && accounts.length == 0) {
            Account newAccount =
                    new Account(
                            mContext.getString(R.string.sync_account_name),
                            mContext.getString(R.string.sync_account_type));
            try {
                accountManager.addAccountExplicitly(newAccount, null, null);
                Log.i("[Contacts Manager] Contact account added");
                makeContactAccountVisible();
            } catch (Exception e) {
                Log.e("[Contacts Manager] Couldn't initialize sync account: " + e);
            }
        } else if (accounts != null) {
            for (int i = 0; i < accounts.length; i++) {
                Log.i(
                        "[Contacts Manager] Found account with name \""
                                + accounts[i].name
                                + "\" and type \""
                                + accounts[i].type
                                + "\"");
                makeContactAccountVisible();
            }
        }
    }

    public synchronized LinphoneContact findContactFromAndroidId(String androidId) {
        if (androidId == null) return null;

        for (LinphoneContact c : getContacts()) {
            if (c.getAndroidId() != null && c.getAndroidId().equals(androidId)) {
                return c;
            }
        }
        return null;
    }

    public synchronized LinphoneContact findContactFromAddress(Address address) {
        if (address == null) return null;
        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        Friend lf = lc.findFriend(address);
        if (lf != null) {
            return (LinphoneContact) lf.getUserData();
        }
        return findContactFromPhoneNumber(address.getUsername());
    }

    public synchronized LinphoneContact findContactFromPhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return null;
        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        ProxyConfig lpc = null;
        if (lc != null) {
            lpc = lc.getDefaultProxyConfig();
        }
        if (lpc == null) return null;
        String normalized = lpc.normalizePhoneNumber(phoneNumber);
        if (normalized == null) normalized = phoneNumber;

        Address addr = lpc.normalizeSipUri(normalized);
        if (addr == null) {
            return null;
        }
        addr.setUriParam("user", "phone");
        Friend lf =
                lc.findFriend(
                        addr); // Without this, the hashmap inside liblinphone won't find it...
        if (lf != null) {
            return (LinphoneContact) lf.getUserData();
        }
        return null;
    }

    public String getAddressOrNumberForAndroidContact(ContentResolver resolver, Uri contactUri) {
        // Phone Numbers
        String[] projection = new String[] {ContactsContract.CommonDataKinds.Phone.NUMBER};
        Cursor c = resolver.query(contactUri, projection, null, null, null);
        if (c != null) {
            if (c.moveToNext()) {
                int numberIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                String number = c.getString(numberIndex);
                c.close();
                return number;
            }
        }
        c.close();

        // SIP addresses
        projection = new String[] {ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS};
        c = resolver.query(contactUri, projection, null, null, null);
        if (c != null) {
            if (c.moveToNext()) {
                int numberIndex =
                        c.getColumnIndex(ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS);
                String address = c.getString(numberIndex);
                c.close();
                return address;
            }
        }
        c.close();
        return null;
    }

    private synchronized boolean refreshSipContact(Friend lf) {
        LinphoneContact contact = (LinphoneContact) lf.getUserData();
        if (contact != null) {
            if (!mSipContacts.contains(contact)) {
                mSipContacts.add(contact);
                return true;
            }
        }
        return false;
    }

    public void delete(String id) {
        ArrayList<String> ids = new ArrayList<>();
        ids.add(id);
        deleteMultipleContactsAtOnce(ids);
    }

    public void deleteMultipleContactsAtOnce(List<String> ids) {
        String select = ContactsContract.Data.CONTACT_ID + " = ?";
        ArrayList<ContentProviderOperation> ops = new ArrayList<>();

        for (String id : ids) {
            String[] args = new String[] {id};
            ops.add(
                    ContentProviderOperation.newDelete(ContactsContract.RawContacts.CONTENT_URI)
                            .withSelection(select, args)
                            .build());
        }

        try {
            mContext.getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
        } catch (Exception e) {
            Log.e("[Contacts Manager] " + e);
        }

        // To ensure removed contacts won't appear in the contacts list anymore
        fetchContactsAsync();
    }

    public String getString(int resourceID) {
        if (mContext == null) return null;
        return mContext.getString(resourceID);
    }

    @Override
    public void onContactCreated(FriendList list, Friend lf) {}

    @Override
    public void onContactDeleted(FriendList list, Friend lf) {}

    @Override
    public void onContactUpdated(FriendList list, Friend newFriend, Friend oldFriend) {}

    @Override
    public void onSyncStatusChanged(FriendList list, FriendList.SyncStatus status, String msg) {}

    @Override
    public void onPresenceReceived(FriendList list, Friend[] friends) {
        boolean updated = false;
        for (Friend lf : friends) {
            boolean newContact = ContactsManager.getInstance().refreshSipContact(lf);
            if (newContact) {
                updated = true;
            }
        }

        if (updated) {
            Collections.sort(mSipContacts);
        }

        for (ContactsUpdatedListener listener : mContactsUpdatedListeners) {
            listener.onContactsUpdated();
        }
    }
}
