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

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.compatibility.Compatibility;
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
        ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
        ContactsContract.Data.MIMETYPE,
        ContactsContract.Contacts.STARRED,
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
        if (LinphonePreferences.instance().isFriendlistsubscriptionEnabled()) {
            String rls = mContext.getString(R.string.rls_uri);
            for (FriendList list : LinphoneManager.getCore().getFriendsLists()) {
                if (list.getRlsAddress() == null
                        || !list.getRlsAddress().asStringUriOnly().equals(rls)) {
                    list.setRlsUri(rls);
                }
                list.addListener(ContactsManager.getInstance());
            }
        }
    }

    @Override
    protected AsyncContactsData doInBackground(Void... params) {
        Log.i("[Contacts Manager] Background synchronization started");

        HashMap<String, LinphoneContact> androidContactsCache = new HashMap<>();
        AsyncContactsData data = new AsyncContactsData();
        List<String> nativeIds = new ArrayList<>();

        Core core = LinphoneManager.getCore();
        if (core != null) {
            FriendList[] friendLists = core.getFriendsLists();
            for (FriendList list : friendLists) {
                Friend[] friends = list.getFriends();
                for (Friend friend : friends) {
                    if (isCancelled()) {
                        Log.w("[Contacts Manager] Task cancelled");
                        return data;
                    }

                    LinphoneContact contact = (LinphoneContact) friend.getUserData();
                    if (contact != null) {
                        if (contact.getAndroidId() != null) {
                            contact.clearAddresses();
                            androidContactsCache.put(contact.getAndroidId(), contact);
                            nativeIds.add(contact.getAndroidId());
                        } else {
                            data.contacts.add(contact);
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

        if (ContactsManager.getInstance().hasReadContactsAccess()) {
            String selection = null;
            if (mContext.getResources().getBoolean(R.bool.fetch_contacts_from_default_directory)) {
                Log.i("[Contacts Manager] Only fetching contacts in default directory");
                selection = ContactsContract.Data.IN_DEFAULT_DIRECTORY + " == 1";
            }

            Cursor c =
                    mContext.getContentResolver()
                            .query(
                                    ContactsContract.Data.CONTENT_URI,
                                    PROJECTION,
                                    selection,
                                    null,
                                    null);
            if (c != null) {
                Log.i("[Contacts Manager] Found " + c.getCount() + " entries in cursor");
                while (c.moveToNext()) {
                    if (isCancelled()) {
                        Log.w("[Contacts Manager] Task cancelled");
                        return data;
                    }

                    try {
                        String id = c.getString(c.getColumnIndex(ContactsContract.Data.CONTACT_ID));
                        boolean starred =
                                c.getInt(c.getColumnIndex(ContactsContract.Contacts.STARRED)) == 1;

                        LinphoneContact contact = androidContactsCache.get(id);
                        if (contact == null) {
                            Log.d(
                                    "[Contacts Manager] Creating LinphoneContact with native ID "
                                            + id
                                            + ", favorite flag is "
                                            + starred);
                            nativeIds.add(id);
                            contact = new LinphoneContact();
                            contact.setAndroidId(id);
                            contact.setIsFavourite(starred);
                            androidContactsCache.put(id, contact);
                        }

                        contact.syncValuesFromAndroidCusor(c);
                    } catch (IllegalStateException ise) {
                        Log.e(
                                "[Contacts Manager] Couldn't get values from cursor, exception: ",
                                ise);
                    }
                }
                c.close();
            }

            FriendList[] friendLists = core.getFriendsLists();
            for (FriendList list : friendLists) {
                Friend[] friends = list.getFriends();
                for (Friend friend : friends) {
                    if (isCancelled()) {
                        Log.w("[Contacts Manager] Task cancelled");
                        return data;
                    }

                    LinphoneContact contact = (LinphoneContact) friend.getUserData();
                    if (contact != null && contact.isAndroidContact()) {
                        String id = contact.getAndroidId();
                        if (id != null && !nativeIds.contains(id)) {
                            Log.i("[Contacts Manager] Contact removed since last fetch: " + id);
                            // Has been removed since last fetch
                            androidContactsCache.remove(id);
                        }
                    }
                }
            }
            nativeIds.clear();
        }

        Collection<LinphoneContact> contacts = androidContactsCache.values();
        // New friends count will be 0 after the first contacts fetch
        Log.i(
                "[Contacts Manager] Found "
                        + contacts.size()
                        + " native contacts plus "
                        + data.contacts.size()
                        + " friends in the configuration file");
        for (LinphoneContact contact : contacts) {
            if (isCancelled()) {
                Log.w("[Contacts Manager] Task cancelled");
                return data;
            }
            if (contact.getNumbersOrAddresses().isEmpty()) {
                continue;
            }

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
        Log.i(
                "[Contacts Manager] "
                        + data.contacts.size()
                        + " contacts found in which "
                        + data.sipContacts.size()
                        + " are SIP");

        for (LinphoneContact contact : data.contacts) {
            contact.createOrUpdateFriendFromNativeContact();
        }

        // Now that contact fetching is asynchronous, this is required to ensure
        // presence subscription event will be sent with all friends
        if (LinphonePreferences.instance().isFriendlistsubscriptionEnabled()) {
            Log.i("[Contacts Manager] Matching friends created, updating subscription");
            FriendList[] friendLists = LinphoneManager.getCore().getFriendsLists();
            for (FriendList list : friendLists) {
                list.updateSubscriptions();
            }
        }

        ContactsManager.getInstance().setContacts(data.contacts);
        ContactsManager.getInstance().setSipContacts(data.sipContacts);

        for (ContactsUpdatedListener listener :
                ContactsManager.getInstance().getContactsListeners()) {
            listener.onContactsUpdated();
        }

        Compatibility.createChatShortcuts(mContext);
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
