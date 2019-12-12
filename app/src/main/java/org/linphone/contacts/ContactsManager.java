/*
 * Copyright (c) 2010-2019 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.contacts;

import static android.os.AsyncTask.THREAD_POOL_EXECUTOR;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.provider.ContactsContract;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.linphone.LinphoneContext;
import org.linphone.LinphoneManager;
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

public class ContactsManager extends ContentObserver
        implements FriendListListener, LinphoneContext.CoreStartedListener {
    private List<LinphoneContact> mContacts, mSipContacts;
    private final ArrayList<ContactsUpdatedListener> mContactsUpdatedListeners;
    private MagicSearch mMagicSearch;
    private boolean mContactsFetchedOnce = false;
    private Context mContext;
    private AsyncContactsLoader mLoadContactTask;
    private boolean mInitialized = false;

    public static ContactsManager getInstance() {
        return LinphoneContext.instance().getContactsManager();
    }

    public ContactsManager(Context context) {
        super(new Handler(Looper.getMainLooper()));
        mContext = context;
        mContactsUpdatedListeners = new ArrayList<>();
        mContacts = new ArrayList<>();
        mSipContacts = new ArrayList<>();

        if (LinphoneManager.getCore() != null) {
            mMagicSearch = LinphoneManager.getCore().createMagicSearch();
            mMagicSearch.setLimitedSearch(false); // Do not limit the number of results
        }

        LinphoneContext.instance().addCoreStartedListener(this);
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
        Log.i("[Contacts Manager] Content observer detected a changing in at least one contact");
        fetchContactsAsync();
    }

    @Override
    public void onCoreStarted() {
        // Core has been started, fetch contacts again in case there are some
        // in the configuration file or remote provisioning
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
        mContext.getContentResolver().unregisterContentObserver(this);
        LinphoneContext.instance().removeCoreStartedListener(this);

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

        Core core = LinphoneManager.getCore();
        if (core != null) {
            for (FriendList list : core.getFriendsLists()) {
                list.removeListener(this);
            }
        }
    }

    public void fetchContactsAsync() {
        if (mLoadContactTask != null) {
            mLoadContactTask.cancel(true);
        }

        if (!hasReadContactsAccess()) {
            Log.w(
                    "[Contacts Manager] Can't fetch native contacts without READ_CONTACTS permission");
        }

        mLoadContactTask = new AsyncContactsLoader(mContext);
        mContactsFetchedOnce = true;
        mLoadContactTask.executeOnExecutor(THREAD_POOL_EXECUTOR);
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
        if (mContext == null || mContext.getPackageManager() == null) {
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

    private boolean hasWriteContactsAccess() {
        if (mContext == null) {
            return false;
        }

        return (PackageManager.PERMISSION_GRANTED
                == mContext.getPackageManager()
                        .checkPermission(
                                Manifest.permission.WRITE_CONTACTS, mContext.getPackageName()));
    }

    private boolean hasWriteSyncPermission() {
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
        ProxyConfig lpc = LinphoneManager.getCore().getDefaultProxyConfig();
        return lpc != null
                && lpc.getIdentityAddress()
                        .getDomain()
                        .equals(mContext.getString(R.string.default_domain));
    }

    public void initializeContactManager() {
        if (!mInitialized) {
            if (mContext.getResources().getBoolean(R.bool.use_linphone_tag)) {
                if (hasReadContactsAccess()
                        && hasWriteContactsAccess()
                        && hasWriteSyncPermission()) {
                    if (LinphoneContext.isReady()) {
                        initializeSyncAccount();
                        mInitialized = true;
                    }
                }
            }
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
            for (Account account : accounts) {
                Log.i(
                        "[Contacts Manager] Found account with name \""
                                + account.name
                                + "\" and type \""
                                + account.type
                                + "\"");
                makeContactAccountVisible();
            }
        }
    }

    public String getAndroidContactIdFromUri(Uri uri) {
        String[] projection = {ContactsContract.CommonDataKinds.SipAddress.CONTACT_ID};
        Cursor cursor =
                mContext.getApplicationContext()
                        .getContentResolver()
                        .query(uri, projection, null, null, null);
        cursor.moveToFirst();

        int nameColumnIndex =
                cursor.getColumnIndex(ContactsContract.CommonDataKinds.SipAddress.CONTACT_ID);
        String id = cursor.getString(nameColumnIndex);
        cursor.close();
        return id;
    }

    public synchronized LinphoneContact findContactFromAndroidId(String androidId) {
        if (androidId == null) {
            return null;
        }

        for (LinphoneContact c : getContacts()) {
            if (c.getAndroidId() != null && c.getAndroidId().equals(androidId)) {
                return c;
            }
        }
        return null;
    }

    public synchronized LinphoneContact findContactFromAddress(Address address) {
        if (address == null) return null;
        Core core = LinphoneManager.getCore();

        Friend lf = core.findFriend(address);
        if (lf != null) {
            return (LinphoneContact) lf.getUserData();
        }

        String username = address.getUsername();
        if (username == null) {
            Log.w("[Contacts Manager] Address ", address.asString(), " doesn't have a username!");
            return null;
        }

        if (android.util.Patterns.PHONE.matcher(username).matches()) {
            return findContactFromPhoneNumber(username);
        }

        return null;
    }

    public synchronized LinphoneContact findContactFromPhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return null;

        if (!android.util.Patterns.PHONE.matcher(phoneNumber).matches()) {
            Log.w(
                    "[Contacts Manager] Expected phone number but doesn't look like it: "
                            + phoneNumber);
            return null;
        }

        Core core = LinphoneManager.getCore();
        ProxyConfig lpc = null;
        if (core != null) {
            lpc = core.getDefaultProxyConfig();
        }
        if (lpc == null) {
            Log.i("[Contacts Manager] Couldn't find default proxy config...");
            return null;
        }

        String normalized = lpc.normalizePhoneNumber(phoneNumber);
        if (normalized == null) {
            Log.w(
                    "[Contacts Manager] Couldn't normalize phone number "
                            + phoneNumber
                            + ", default proxy config prefix is "
                            + lpc.getDialPrefix());
            normalized = phoneNumber;
        }

        Address addr = lpc.normalizeSipUri(normalized);
        if (addr == null) {
            Log.w("[Contacts Manager] Couldn't normalize SIP URI " + normalized);
            return null;
        }

        // Without this, the hashmap inside liblinphone won't find it...
        addr.setUriParam("user", "phone");
        Friend lf = core.findFriend(addr);
        if (lf != null) {
            return (LinphoneContact) lf.getUserData();
        }

        Log.w("[Contacts Manager] Couldn't find friend...");
        return null;
    }

    public String getAddressOrNumberForAndroidContact(ContentResolver resolver, Uri contactUri) {
        if (resolver == null || contactUri == null) return null;

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
            c.close();
        }

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
            c.close();
        }

        return null;
    }

    private synchronized boolean refreshSipContact(Friend lf) {
        if (lf == null) return false;
        LinphoneContact contact = (LinphoneContact) lf.getUserData();

        if (contact != null) {
            if (LinphoneContext.instance()
                    .getApplicationContext()
                    .getResources()
                    .getBoolean(R.bool.use_linphone_tag)) {
                // Inserting Linphone information in Android contact if the parameter is enabled
                if (LinphonePreferences.instance()
                        .isPresenceStorageInNativeAndroidContactEnabled()) {
                    // add presence to native contact
                    AsyncContactPresence asyncContactPresence = new AsyncContactPresence(contact);
                    asyncContactPresence.execute();
                }
            }

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
            Log.i("[Contacts Manager] Adding Android contact id ", id, " to batch removal");
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
            boolean newContact = refreshSipContact(lf);

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

        Compatibility.createChatShortcuts(mContext);
    }
}
