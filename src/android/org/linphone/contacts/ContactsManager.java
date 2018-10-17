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
import android.app.LoaderManager;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Data;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.linphone.LinphoneManager;
import org.linphone.LinphonePreferences;
import org.linphone.LinphoneService;
import org.linphone.LinphoneUtils;
import org.linphone.R;
import org.linphone.activities.LinphoneActivity;
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
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class ContactsManager extends ContentObserver implements FriendListListener, LoaderManager.LoaderCallbacks<Cursor> {
    private static ContactsManager instance;

    private List<LinphoneContact> mContacts, mSipContacts;
    private MagicSearch magicSearch;
    private boolean preferLinphoneContacts = false;
    private Activity mActivity;
    private HashMap<String, LinphoneContact> mAndroidContactsCache;
    private Bitmap defaultAvatar;
    private boolean mContactsFetchedOnce = false;

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
        mAndroidContactsCache = new HashMap<>();
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
        fetchContactsSync();
    }

    public static final ContactsManager getInstance() {
        if (instance == null) instance = new ContactsManager();
        return instance;
    }

    public synchronized boolean hasContacts() {
        return mContacts.size() > 0;
    }

    public synchronized List<LinphoneContact> getContacts() {
        return mContacts;
    }

    public synchronized List<LinphoneContact> getSIPContacts() {
        return mSipContacts;
    }

    public synchronized List<LinphoneContact> getContacts(String search) {
        search = search.toLowerCase(Locale.getDefault());
        List<LinphoneContact> searchContactsBegin = new ArrayList<LinphoneContact>();
        List<LinphoneContact> searchContactsContain = new ArrayList<LinphoneContact>();
        for (LinphoneContact contact : mContacts) {
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
        for (LinphoneContact contact : mSipContacts) {
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
        if (mActivity == null) {
            return false;
        }
        boolean contactsR = (PackageManager.PERMISSION_GRANTED ==
                mActivity.getPackageManager().checkPermission(android.Manifest.permission.READ_CONTACTS, mActivity.getPackageName()));
        return contactsR && !mActivity.getResources().getBoolean(R.bool.force_use_of_linphone_friends);
    }

    public void setLinphoneContactsPrefered(boolean isPrefered) {
        preferLinphoneContacts = isPrefered;
    }

    public boolean isLinphoneContactsPrefered() {
        return preferLinphoneContacts;
    }

    public void initializeContactManager(Activity activity) {
        if (mActivity == null) {
            mActivity = activity;
        } else if (mActivity != activity) {
            mActivity = activity;
        }
        if (mActivity == null && LinphoneActivity.isInstanciated()) {
            mActivity = LinphoneActivity.instance();
        }
        if (mActivity != null && mContacts.size() == 0 && hasContactsAccess()) {
            mActivity.getLoaderManager().initLoader(CONTACTS_LOADER, null, this);
        }
    }

    public void initializeSyncAccount(Activity activity) {
        initializeContactManager(activity);
        AccountManager accountManager = (AccountManager) activity.getSystemService(Context.ACCOUNT_SERVICE);

        Account[] accounts = accountManager.getAccountsByType(activity.getPackageName());

        if (accounts != null && accounts.length == 0) {
            Account newAccount = new Account(mActivity.getString(R.string.sync_account_name), activity.getPackageName());
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
        if (contact != null && !mSipContacts.contains(contact)) {
            mSipContacts.add(contact);
            Collections.sort(mSipContacts);
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
            mActivity.getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
        } catch (Exception e) {
            Log.e(e);
        }
    }

    public String getString(int resourceID) {
        if (mActivity == null) return null;
        return mActivity.getString(resourceID);
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

    public void fetchContactsSync() {
        if (mActivity == null && LinphoneActivity.isInstanciated()) {
            mActivity = LinphoneActivity.instance();
        }
        if (mActivity == null) {
            Log.w("Can't fetch contacts right now, activity is null...");
            return;
        }
        mActivity.getLoaderManager().initLoader(CONTACTS_LOADER, null, this);
    }

    public void fetchContactsAsync() {
        fetchContactsSync();
    }

    private static final int CONTACTS_LOADER = 1;

    @SuppressLint("InlinedApi")
    private static final String[] PROJECTION =
            {
                    ContactsContract.Data.CONTACT_ID,
                    ContactsContract.Contacts.LOOKUP_KEY,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                    ContactsContract.Data.MIMETYPE,
                    "data1", //Company, Phone or SIP Address
                    "data2", //ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME
                    "data3", //ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME
                    "data4", //Normalized phone number
            };

    private static final String SELECTION = ContactsContract.Data.DISPLAY_NAME_PRIMARY + " IS NOT NULL AND ("
            + "(" + ContactsContract.Data.MIMETYPE + " = '" + ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE + "' AND data1 IS NOT NULL) OR "
            + "(" + ContactsContract.Data.MIMETYPE + " = '" + ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE + "' AND data1 IS NOT NULL) OR "
            + "(" + ContactsContract.Data.MIMETYPE + " = '" + getInstance().getString(R.string.sync_mimetype) + "' AND data1 IS NOT NULL))";

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, @Nullable Bundle args) {
        if (id == CONTACTS_LOADER) {
            if (!hasContactsAccess()) {
                Log.w("[ContactsManager] Read contacts permission was denied");
                return null;
            }
            return new CursorLoader(
                    mActivity,
                    ContactsContract.Data.CONTENT_URI,
                    PROJECTION,
                    null,
                    null,
                    null
            );
        }
        return null;
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor c) {
        mContactsFetchedOnce = true;

        Date contactsTime = new Date();
        List<LinphoneContact> contacts = new ArrayList<>();
        List<LinphoneContact> sipContacts = new ArrayList<>();
        mAndroidContactsCache.clear();

        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            for (FriendList list : lc.getFriendsLists()) {
                for (Friend friend : list.getFriends()) {
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
                            contacts.add(contact);
                            if (contact.hasAddress()) {
                                sipContacts.add(contact);
                            }
                        }
                    }
                }
            }
        }

        if (c != null) {
            List<String> nativeIds = new ArrayList<>();
            while (c.moveToNext()) {
                String id = c.getString(c.getColumnIndex(ContactsContract.Data.CONTACT_ID));
                String displayName = c.getString(c.getColumnIndex(ContactsContract.Data.DISPLAY_NAME_PRIMARY));
                String mime = c.getString(c.getColumnIndex(ContactsContract.Data.MIMETYPE));
                String data1 = c.getString(c.getColumnIndex("data1"));
                String data2 = c.getString(c.getColumnIndex("data2"));
                String data3 = c.getString(c.getColumnIndex("data3"));
                String data4 = c.getString(c.getColumnIndex("data4"));

                nativeIds.add(id);
                LinphoneContact contact = mAndroidContactsCache.get(id);
                if (contact == null) {
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

            for (LinphoneContact contact : mAndroidContactsCache.values()) {
                // Only add contact to contacts list once we are finished with it, helps prevent duplicates
                int indexOf = contacts.indexOf(contact);
                if (indexOf < 0) {
                    contacts.add(contact);
                    if (contact.hasAddress()) {
                        if (mActivity.getResources().getBoolean(R.bool.hide_sip_contacts_without_presence)) {
                            if (contact.getFriend() != null) {
                                for (LinphoneNumberOrAddress noa : contact.getNumbersOrAddresses()) {
                                    PresenceModel pm = contact.getFriend().getPresenceModelForUriOrTel(noa.getValue());
                                    if (pm != null && pm.getBasicStatus().equals(PresenceBasicStatus.Open)) {
                                        sipContacts.add(contact);
                                        break;
                                    }
                                }
                            }
                        } else {
                            sipContacts.add(contact);
                        }
                    }
                } else {
                    Log.w("Contact " + contact.getFullName() + " (" + contact.getAndroidId() +
                            ") is an exact duplicate of " + contacts.get(indexOf).getAndroidId());
                }
            }
        }

        for (LinphoneContact contact : contacts) {
            // Create the Friends matching the native contacts
            if (contact.getFullName() == null) {
                if (contact.hasAddress()) {
                    for (LinphoneNumberOrAddress noa : contact.getNumbersOrAddresses()) {
                        if (noa.isSIPAddress()) {
                            contact.setFullName(LinphoneUtils.getAddressDisplayName(noa.getValue()));
                            Log.w("Couldn't find a display name for contact " + contact.getFullName() + ", used SIP address display name / username instead...");
                            break;
                        }
                    }
                }
                if (contact.getFullName() == null) {
                    Log.e("Couldn't find a display name for contact " + contact);
                    continue;
                }
            }
            contact.createOrUpdateFriendFromNativeContact();
        }
        mAndroidContactsCache.clear();

        setContacts(contacts);
        setSipContacts(sipContacts);

        if (LinphonePreferences.instance() != null && LinphonePreferences.instance().isFriendlistsubscriptionEnabled()) {
            String rls = mActivity.getString(R.string.rls_uri);
            for (FriendList list : LinphoneManager.getLc().getFriendsLists()) {
                if (rls != null && (list.getRlsAddress() == null || !list.getRlsAddress().asStringUriOnly().equals(rls))) {
                    list.setRlsUri(rls);
                }
                list.setListener(this);
                list.updateSubscriptions();
            }
        }

        long timeElapsed = (new Date()).getTime() - contactsTime.getTime();
        String time = String.format(Locale.getDefault(), "%02d:%02d:%03d",
                TimeUnit.MILLISECONDS.toMinutes(timeElapsed),
                TimeUnit.MILLISECONDS.toSeconds(timeElapsed) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(timeElapsed)),
                TimeUnit.MILLISECONDS.toMillis(timeElapsed) -
                        TimeUnit.SECONDS.toMillis(TimeUnit.MILLISECONDS.toSeconds(timeElapsed)));
        Log.i("[ContactsManager] For " + contacts.size() + " contacts: " + time + " elapsed since starting");

        for (ContactsUpdatedListener listener : contactsUpdatedListeners) {
            listener.onContactsUpdated();
        }
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader) {

    }

    public synchronized void setContacts(List<LinphoneContact> c) {
        if (mContacts.isEmpty() || mContacts.size() > c.size()) {
            mContacts = c;
        } else {
            for (LinphoneContact contact : c) {
                if (!mContacts.contains(contact)) {
                    mContacts.add(contact);
                }
            }
        }
        Collections.sort(mContacts);
    }

    public synchronized void setSipContacts(List<LinphoneContact> c) {
        if (mSipContacts.isEmpty() || mSipContacts.size() > c.size()) {
            mSipContacts = c;
        } else {
            for (LinphoneContact contact : c) {
                if (!mSipContacts.contains(contact)) {
                    mSipContacts.add(contact);
                }
            }
        }
       Collections.sort(mSipContacts);
    }
}
