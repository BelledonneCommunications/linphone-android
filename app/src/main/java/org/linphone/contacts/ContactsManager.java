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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Data;

import org.linphone.LinphoneManager;
import org.linphone.settings.LinphonePreferences;
import org.linphone.LinphoneService;
import org.linphone.utils.LinphoneUtils;
import org.linphone.R;
import org.linphone.core.Address;
import org.linphone.core.Core;
import org.linphone.core.Friend;
import org.linphone.core.FriendList;
import org.linphone.core.FriendListListener;
import org.linphone.core.MagicSearch;
import org.linphone.core.PresenceBasicStatus;
import org.linphone.core.PresenceModel;
import org.linphone.core.ProxyConfig;
import org.linphone.mediastream.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import static android.os.AsyncTask.THREAD_POOL_EXECUTOR;

public class ContactsManager extends ContentObserver implements FriendListListener {
    private static ContactsManager instance;

    private List<LinphoneContact> mContacts, mSipContacts;
    private MagicSearch magicSearch;
    private Bitmap defaultAvatar;
    private boolean mContactsFetchedOnce = false;
    private Context mContext;
    private AsyncContactsLoader mLoadContactTask;

    private static ArrayList<ContactsUpdatedListener> contactsUpdatedListeners;

    public static void addContactsListener(ContactsUpdatedListener listener) {
        contactsUpdatedListeners.add(listener);
    }

    public static void removeContactsListener(ContactsUpdatedListener listener) {
        contactsUpdatedListeners.remove(listener);
    }

    private ContactsManager() {
        super(LinphoneService.instance().mHandler);
        defaultAvatar = BitmapFactory.decodeResource(LinphoneService.instance().getResources(), R.drawable.avatar);
        contactsUpdatedListeners = new ArrayList<>();
        mContacts = new ArrayList<>();
        mSipContacts = new ArrayList<>();
        if (LinphoneManager.getLcIfManagerNotDestroyedOrNull() != null) {
            magicSearch = LinphoneManager.getLcIfManagerNotDestroyedOrNull().createMagicSearch();
        }
    }

    public void destroy() {
        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            for (FriendList list : lc.getFriendsLists()) {
                list.setListener(null);
            }
        }
        defaultAvatar.recycle();
        instance = null;
    }

    public MagicSearch getMagicSearch() {
        return magicSearch;
    }

    public boolean contactsFetchedOnce() {
        return mContactsFetchedOnce;
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

    public static final ContactsManager getInstance() {
        if (instance == null) instance = new ContactsManager();
        return instance;
    }

    public synchronized boolean hasContacts() {
        return mContacts.size() > 0;
    }

    public List<LinphoneContact> getContacts() {
        synchronized (mContacts) {
            return mContacts;
        }
    }

    public List<LinphoneContact> getSIPContacts() {
        synchronized (mSipContacts) {
            return mSipContacts;
        }
    }

    public List<LinphoneContact> getContacts(String search) {
        search = search.toLowerCase(Locale.getDefault());
        List<LinphoneContact> searchContactsBegin = new ArrayList<>();
        List<LinphoneContact> searchContactsContain = new ArrayList<>();
        for (LinphoneContact contact : getContacts()) {
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

    public List<LinphoneContact> getSIPContacts(String search) {
        search = search.toLowerCase(Locale.getDefault());
        List<LinphoneContact> searchContactsBegin = new ArrayList<>();
        List<LinphoneContact> searchContactsContain = new ArrayList<>();
        for (LinphoneContact contact : getSIPContacts()) {
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

    private void addSipContact(LinphoneContact contact) {
        synchronized (mSipContacts) {
            mSipContacts.add(contact);
            Collections.sort(mSipContacts);
        }
    }

    public void enableContactsAccess() {
        LinphonePreferences.instance().disableFriendsStorage();
    }

    public boolean hasContactsAccess() {
        if (mContext == null) {
            return false;
        }
        boolean contactsR = (PackageManager.PERMISSION_GRANTED ==
                mContext.getPackageManager().checkPermission(android.Manifest.permission.READ_CONTACTS, mContext.getPackageName()));
        return contactsR && !mContext.getResources().getBoolean(R.bool.force_use_of_linphone_friends);
    }

    public boolean isLinphoneContactsPrefered() {
        ProxyConfig lpc = LinphoneManager.getLc().getDefaultProxyConfig();
        if (lpc != null && lpc.getIdentityAddress().getDomain().equals(getString(R.string.default_domain))) return true;
        return false;
    }

    public void initializeContactManager(Context context) {
        mContext = context;

        if (mContext != null && getContacts().size() == 0 && hasContactsAccess()) {
            fetchContactsAsync();
        }
    }

    public void initializeSyncAccount(Activity activity) {
        initializeContactManager(activity);
        AccountManager accountManager = (AccountManager) activity.getSystemService(Context.ACCOUNT_SERVICE);

        Account[] accounts = accountManager.getAccountsByType(activity.getPackageName());

        if (accounts != null && accounts.length == 0) {
            Account newAccount = new Account(getString(R.string.sync_account_name), activity.getPackageName());
            try {
                accountManager.addAccountExplicitly(newAccount, null, null);
            } catch (Exception e) {
                Log.e(e);
            }
        }
    }

    public synchronized LinphoneContact findContactFromAddress(Address address) {
        if (address == null) return null;
        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        Friend lf = lc.findFriend(address);
        if (lf != null) {
            LinphoneContact contact = (LinphoneContact) lf.getUserData();
            return contact;
        }
        return findContactFromPhoneNumber(address.getUsername());
    }

    public synchronized LinphoneContact findContactFromPhoneNumber(String phoneNumber) {
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
        Friend lf = lc.findFriend(addr); // Without this, the hashmap inside liblinphone won't find it...
        if (lf != null) {
            LinphoneContact contact = (LinphoneContact) lf.getUserData();
            return contact;
        }
        return null;
    }

    public synchronized boolean refreshSipContact(Friend lf) {
        LinphoneContact contact = (LinphoneContact) lf.getUserData();
        if (contact != null && !getSIPContacts().contains(contact)) {
            addSipContact(contact);
            return true;
        }
        return false;
    }

    public static String getAddressOrNumberForAndroidContact(ContentResolver resolver, Uri contactUri) {
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
        projection = new String[]{ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS};
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
        ArrayList<String> ids = new ArrayList<>();
        ids.add(id);
        deleteMultipleContactsAtOnce(ids);
    }

    public void deleteMultipleContactsAtOnce(List<String> ids) {
        String select = ContactsContract.Data.CONTACT_ID + " = ?";
        ArrayList<ContentProviderOperation> ops = new ArrayList<>();

        for (String id : ids) {
            String[] args = new String[]{id};
            ops.add(ContentProviderOperation.newDelete(ContactsContract.RawContacts.CONTENT_URI).withSelection(select, args).build());
        }

        try {
            mContext.getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
        } catch (Exception e) {
            Log.e(e);
        }
    }

    public String getString(int resourceID) {
        if (mContext == null) return null;
        return mContext.getString(resourceID);
    }

    @Override
    public void onContactCreated(FriendList list, Friend lf) {

    }

    @Override
    public void onContactDeleted(FriendList list, Friend lf) {

    }

    @Override
    public void onContactUpdated(FriendList list, Friend newFriend, Friend oldFriend) {

    }

    @Override
    public void onSyncStatusChanged(FriendList list, FriendList.SyncStatus status, String msg) {

    }

    @Override
    public void onPresenceReceived(FriendList list, Friend[] friends) {
        for (Friend lf : friends) {
            boolean newContact = ContactsManager.getInstance().refreshSipContact(lf);
            if (newContact) {
                for (ContactsUpdatedListener listener : contactsUpdatedListeners) {
                    listener.onContactsUpdated();
                }
            }
        }
    }

    public void fetchContactsAsync() {
        if (mLoadContactTask != null) {
            mLoadContactTask.cancel(true);
        }
        mLoadContactTask = new AsyncContactsLoader();
        mLoadContactTask.executeOnExecutor(THREAD_POOL_EXECUTOR);
    }

    @SuppressLint("InlinedApi")
    private static final String[] PROJECTION =
    {
        Data.CONTACT_ID,
        ContactsContract.Contacts.LOOKUP_KEY,
        ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
        Data.MIMETYPE,
        "data1", //Company, Phone or SIP Address
        "data2", //ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME
        "data3", //ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME
        "data4", //Normalized phone number
    };

    class AsyncContactsData {
        List<LinphoneContact> contacts;
        List<LinphoneContact> sipContacts;

        public AsyncContactsData() {
            contacts = new ArrayList<>();
            sipContacts = new ArrayList<>();
        }
    }

    class AsyncContactsLoader extends AsyncTask<Void, Void, AsyncContactsData> {
        private HashMap<String, LinphoneContact> mAndroidContactsCache;

        @Override
        protected void onPreExecute() {
            mAndroidContactsCache = new HashMap<>();
            mContactsFetchedOnce = true;

            if (LinphonePreferences.instance() != null && LinphonePreferences.instance().isFriendlistsubscriptionEnabled()) {
                String rls = getString(R.string.rls_uri);
                for (FriendList list : LinphoneManager.getLc().getFriendsLists()) {
                    if (rls != null && (list.getRlsAddress() == null || !list.getRlsAddress().asStringUriOnly().equals(rls))) {
                        list.setRlsUri(rls);
                    }
                    list.setListener(ContactsManager.this);
                }
            }
        }

        @Override
        protected AsyncContactsData doInBackground(Void... params) {
            Cursor c = mContext.getContentResolver().query(ContactsContract.Data.CONTENT_URI, PROJECTION, Data.IN_VISIBLE_GROUP + " == 1", null, null);
            AsyncContactsData data = new AsyncContactsData();

            Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
            if (lc != null) {
                for (FriendList list : lc.getFriendsLists()) {
                    for (Friend friend : list.getFriends()) {
                        if (isCancelled()) return data;

                        LinphoneContact contact = (LinphoneContact) friend.getUserData();
                        if (contact != null) {
                            contact.clearAddresses();
                            if (contact.getAndroidId() != null) {
                                mAndroidContactsCache.put(contact.getAndroidId(), contact);
                            }
                        } else {
                            if (friend.getRefKey() != null) {
                                // Friend has a refkey and but no LinphoneContact => represents a native contact stored in db from a previous version of Linphone, remove it
                                list.removeFriend(friend);
                            } else {
                                // No refkey so it's a standalone contact
                                contact = new LinphoneContact();
                                contact.setFriend(friend);
                                contact.refresh();
                                data.contacts.add(contact);
                            }
                        }
                    }
                }
            }

            if (c != null) {
                List<String> nativeIds = new ArrayList<>();
                while (c.moveToNext()) {
                    if (isCancelled()) return data;

                    String id = c.getString(c.getColumnIndex(Data.CONTACT_ID));
                    String displayName = c.getString(c.getColumnIndex(Data.DISPLAY_NAME_PRIMARY));
                    String mime = c.getString(c.getColumnIndex(Data.MIMETYPE));
                    String data1 = c.getString(c.getColumnIndex("data1"));
                    String data2 = c.getString(c.getColumnIndex("data2"));
                    String data3 = c.getString(c.getColumnIndex("data3"));
                    String data4 = c.getString(c.getColumnIndex("data4"));

                    LinphoneContact contact = mAndroidContactsCache.get(id);
                    if (contact == null) {
                        nativeIds.add(id);
                        contact = new LinphoneContact();
                        contact.setAndroidId(id);
                        contact.setFullName(displayName);
                        mAndroidContactsCache.put(id, contact);
                    }
                    if (contact.getFullName() == null && displayName != null) {
                        contact.setFullName(displayName);
                    }

                    if (ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE.equals(mime)) {
                        contact.addNumberOrAddress(new LinphoneNumberOrAddress(data1, data4));
                    } else if (ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE.equals(mime) || getInstance().getString(R.string.sync_mimetype).equals(mime)) {
                        contact.addNumberOrAddress(new LinphoneNumberOrAddress(data1, true));
                    } else if (ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE.equals(mime)) {
                        contact.setOrganization(data1, false);
                    } else if (ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE.equals(mime)) {
                        contact.setFirstNameAndLastName(data2, data3, false);
                    }
                }

                for (FriendList list : lc.getFriendsLists()) {
                    for (Friend friend : list.getFriends()) {
                        if (isCancelled()) return data;

                        LinphoneContact contact = (LinphoneContact) friend.getUserData();
                        if (contact != null && contact.isAndroidContact()) {
                            String id = contact.getAndroidId();
                            if (id != null && !nativeIds.contains(id)) {
                                // Has been removed since last fetch
                                mAndroidContactsCache.remove(id);
                            }
                        }
                    }
                }
                nativeIds.clear();
            }

            for (LinphoneContact contact : mAndroidContactsCache.values()) {
                if (isCancelled()) return data;

                boolean hideContactsWithoutPresence = mContext.getResources().getBoolean(R.bool.hide_sip_contacts_without_presence);
                if (contact.hasAddress()) {
                    if (contact.getFullName() == null) {
                        for (LinphoneNumberOrAddress noa : contact.getNumbersOrAddresses()) {
                            if (noa.isSIPAddress()) {
                                contact.setFullName(LinphoneUtils.getAddressDisplayName(noa.getValue()));
                                Log.w("Couldn't find a display name for contact " + contact.getFullName() + ", used SIP address display name / username instead...");
                                break;
                            }
                        }
                    }
                    if (hideContactsWithoutPresence) {
                        if (contact.getFriend() != null) {
                            for (LinphoneNumberOrAddress noa : contact.getNumbersOrAddresses()) {
                                PresenceModel pm = contact.getFriend().getPresenceModelForUriOrTel(noa.getValue());
                                if (pm != null && pm.getBasicStatus().equals(PresenceBasicStatus.Open)) {
                                    data.sipContacts.add(contact);
                                    break;
                                }
                            }
                        }
                    } else {
                        data.sipContacts.add(contact);
                    }
                }
                contact.createOrUpdateFriendFromNativeContact();
                data.contacts.add(contact);
            }
            mAndroidContactsCache.clear();

            Collections.sort(data.contacts);
            Collections.sort(data.sipContacts);

            return data;
        }

        @Override
        protected void onPostExecute(AsyncContactsData data) {
            for (ContactsUpdatedListener listener : contactsUpdatedListeners) {
                listener.onContactsUpdated();
            }

            setContacts(data.contacts);
            setSipContacts(data.sipContacts);
        }
    }

    public void setContacts(List<LinphoneContact> c) {
        synchronized (mContacts) {
            mContacts = c;
        }
    }

    public synchronized void setSipContacts(List<LinphoneContact> c) {
        synchronized (mSipContacts) {
            mSipContacts = c;
        }
    }
}
