package org.linphone.contacts;

/*
AsyncContactsLoader.java
Copyright (C) 2018  Belledonne Communications, Grenoble, France

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

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.linphone.LinphoneManager;
import org.linphone.LinphoneService;
import org.linphone.R;
import org.linphone.core.Core;
import org.linphone.core.Friend;
import org.linphone.core.FriendList;
import org.linphone.core.PresenceBasicStatus;
import org.linphone.core.PresenceModel;
import org.linphone.core.tools.Log;
import org.linphone.settings.LinphonePreferences;
import org.linphone.utils.LinphoneUtils;

class AsyncContactsLoader extends AsyncTask<Void, Void, AsyncContactsLoader.AsyncContactsData> {
    @SuppressLint("InlinedApi")
    public static final String[] PROJECTION = {
        ContactsContract.Data.CONTACT_ID,
        ContactsContract.Contacts.LOOKUP_KEY,
        ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
        ContactsContract.Data.MIMETYPE,
        "data1", // Company, Phone or SIP Address
        "data2", // ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME
        "data3", // ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME
        "data4", // Normalized phone number
    };

    private Context mContext;

    public AsyncContactsLoader(Context context) {
        mContext = context;
    }

    @Override
    protected void onPreExecute() {
        Log.i("[Contacts Manager] Synchronization started");
        if (mContext == null) {
            mContext = LinphoneService.instance().getApplicationContext();
        }

        if (LinphonePreferences.instance().isFriendlistsubscriptionEnabled()) {
            String rls = mContext.getString(R.string.rls_uri);
            for (FriendList list : LinphoneManager.getLc().getFriendsLists()) {
                if (rls != null
                        && (list.getRlsAddress() == null
                                || !list.getRlsAddress().asStringUriOnly().equals(rls))) {
                    list.setRlsUri(rls);
                }
                list.addListener(ContactsManager.getInstance());
            }
        }
    }

    @Override
    protected AsyncContactsData doInBackground(Void... params) {
        Log.i("[Contacts Manager] Background synchronization started");
        Cursor c =
                mContext.getContentResolver()
                        .query(
                                ContactsContract.Data.CONTENT_URI,
                                PROJECTION,
                                ContactsContract.Data.IN_VISIBLE_GROUP + " == 1",
                                null,
                                null);

        HashMap<String, LinphoneContact> androidContactsCache = new HashMap<>();
        AsyncContactsData data = new AsyncContactsData();
        List<String> nativeIds = new ArrayList<>();

        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            for (FriendList list : lc.getFriendsLists()) {
                for (Friend friend : list.getFriends()) {
                    if (isCancelled()) return data;

                    LinphoneContact contact = (LinphoneContact) friend.getUserData();
                    if (contact != null) {
                        contact.clearAddresses();
                        if (contact.getAndroidId() != null) {
                            androidContactsCache.put(contact.getAndroidId(), contact);
                            nativeIds.add(contact.getAndroidId());
                        }
                    } else {
                        if (friend.getRefKey() != null) {
                            // Friend has a refkey but no LinphoneContact => represents a
                            // native contact stored in db from a previous version of Linphone,
                            // remove it
                            list.removeFriend(friend);
                        } else {
                            // No refkey so it's a standalone contact
                            contact = new LinphoneContact();
                            contact.setFriend(friend);
                            contact.syncValuesFromFriend();
                            data.contacts.add(contact);
                        }
                    }
                }
            }
        }

        if (c != null) {
            while (c.moveToNext()) {
                if (isCancelled()) return data;

                String id = c.getString(c.getColumnIndex(ContactsContract.Data.CONTACT_ID));
                String lookupKey =
                        c.getString(c.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY));

                LinphoneContact contact = androidContactsCache.get(id);
                if (contact == null) {
                    nativeIds.add(id);
                    contact = new LinphoneContact();
                    contact.setAndroidId(id);
                    contact.setAndroidLookupKey(lookupKey);
                    androidContactsCache.put(id, contact);
                }

                contact.syncValuesFromAndroidCusor(c);
            }
            c.close();

            for (FriendList list : lc.getFriendsLists()) {
                for (Friend friend : list.getFriends()) {
                    if (isCancelled()) return data;

                    LinphoneContact contact = (LinphoneContact) friend.getUserData();
                    if (contact != null && contact.isAndroidContact()) {
                        String id = contact.getAndroidId();
                        if (id != null && !nativeIds.contains(id)) {
                            // Has been removed since last fetch
                            androidContactsCache.remove(id);
                        }
                    }
                }
            }
            nativeIds.clear();
        }

        for (LinphoneContact contact : androidContactsCache.values()) {
            if (isCancelled()) return data;

            if (contact.getFullName() == null) {
                for (LinphoneNumberOrAddress noa : contact.getNumbersOrAddresses()) {
                    if (noa.isSIPAddress()) {
                        contact.setFullName(LinphoneUtils.getAddressDisplayName(noa.getValue()));
                        Log.w(
                                "[Contacts Manager] Couldn't find a display name for contact "
                                        + contact.getFullName()
                                        + ", used SIP address display name / username instead...");
                        break;
                    }
                }
            }

            if (contact.getFriend() != null) {
                for (LinphoneNumberOrAddress noa : contact.getNumbersOrAddresses()) {
                    PresenceModel pm =
                            contact.getFriend().getPresenceModelForUriOrTel(noa.getValue());
                    if (pm != null && pm.getBasicStatus().equals(PresenceBasicStatus.Open)) {
                        data.sipContacts.add(contact);
                        break;
                    }
                }
            }

            if (!mContext.getResources().getBoolean(R.bool.hide_sip_contacts_without_presence)) {
                if (contact.hasAddress() && !data.sipContacts.contains(contact)) {
                    data.sipContacts.add(contact);
                }
            }
            data.contacts.add(contact);
        }

        androidContactsCache.clear();

        Collections.sort(data.contacts);
        Collections.sort(data.sipContacts);

        Log.i("[Contacts Manager] Background synchronization finished");
        return data;
    }

    @Override
    protected void onPostExecute(AsyncContactsData data) {
        for (LinphoneContact contact : data.contacts) {
            contact.createOrUpdateFriendFromNativeContact();
        }

        // Now that contact fetching is asynchronous, this is required to ensure
        // presence subscription event will be sent with all friends
        if (LinphonePreferences.instance().isFriendlistsubscriptionEnabled()) {
            for (FriendList list : LinphoneManager.getLc().getFriendsLists()) {
                list.updateSubscriptions();
            }
        }

        ContactsManager.getInstance().setContacts(data.contacts);
        ContactsManager.getInstance().setSipContacts(data.sipContacts);

        for (ContactsUpdatedListener listener :
                ContactsManager.getInstance().getContactsListeners()) {
            listener.onContactsUpdated();
        }
        Log.i("[Contacts Manager] Synchronization finished");
    }

    class AsyncContactsData {
        final List<LinphoneContact> contacts;
        final List<LinphoneContact> sipContacts;

        AsyncContactsData() {
            contacts = new ArrayList<>();
            sipContacts = new ArrayList<>();
        }
    }
}
