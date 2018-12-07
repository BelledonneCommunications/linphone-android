package org.linphone.contacts;

/*
AndroidContact.java
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

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import androidx.core.util.Pair;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.linphone.LinphoneManager;
import org.linphone.LinphoneService;
import org.linphone.R;
import org.linphone.mediastream.Log;

class AndroidContact implements Serializable {
    protected String mAndroidId, mAndroidRawId, mAndroidTagId, mAndroidLookupKey;

    private transient ArrayList<ContentProviderOperation> mChangesToCommit;

    protected AndroidContact() {
        mChangesToCommit = new ArrayList<>();
    }

    protected String getAndroidId() {
        return mAndroidId;
    }

    protected void setAndroidId(String id) {
        mAndroidId = id;
    }

    protected String getAndroidLookupKey() {
        return mAndroidLookupKey;
    }

    protected void setAndroidLookupKey(String lookupKey) {
        mAndroidLookupKey = lookupKey;
    }

    protected Uri getAndroidLookupUri() {
        return ContactsContract.Contacts.getLookupUri(
                Long.parseLong(getAndroidId()), getAndroidLookupKey());
    }

    protected boolean isAndroidContact() {
        return mAndroidId != null;
    }

    protected void addChangesToCommit(ContentProviderOperation operation) {
        Log.d("[Contact] Added operation " + operation);
        mChangesToCommit.add(operation);
    }

    protected void saveChangesCommited() {
        if (ContactsManager.getInstance().hasContactsAccess() && mChangesToCommit.size() > 0) {
            try {
                ContentProviderResult[] results =
                        LinphoneService.instance()
                                .getContentResolver()
                                .applyBatch(ContactsContract.AUTHORITY, mChangesToCommit);
                if (results != null && results.length > 0 && results[0] != null) {
                    Log.i("[Contact] Contact created with URI " + results[0].uri);
                }
            } catch (Exception e) {
                Log.e(e);
            } finally {
                mChangesToCommit.clear();
            }
        }
    }

    protected void createAndroidContact() {
        // TODO
        if (LinphoneManager.getInstance()
                .getContext()
                .getResources()
                .getBoolean(R.bool.use_linphone_tag)) {
            Log.i("[Contact] Creating contact using linphone account type");
            addChangesToCommit(
                    ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                            .withValue(
                                    ContactsContract.RawContacts.ACCOUNT_TYPE,
                                    ContactsManager.getInstance()
                                            .getString(R.string.sync_account_type))
                            .withValue(
                                    ContactsContract.RawContacts.ACCOUNT_NAME,
                                    ContactsManager.getInstance()
                                            .getString(R.string.sync_account_name))
                            .withValue(
                                    ContactsContract.RawContacts.AGGREGATION_MODE,
                                    ContactsContract.RawContacts.AGGREGATION_MODE_DEFAULT)
                            .build());
        } else {
            Log.i("[Contact] Creating contact using default account type");
            addChangesToCommit(
                    ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                            .withValue(
                                    ContactsContract.RawContacts.AGGREGATION_MODE,
                                    ContactsContract.RawContacts.AGGREGATION_MODE_DEFAULT)
                            .build());
        }
    }

    protected void deleteAndroidContact() {
        ContactsManager.getInstance().delete(getAndroidId());
    }

    protected Uri getContactThumbnailPictureUri() {
        Uri person =
                ContentUris.withAppendedId(
                        ContactsContract.Contacts.CONTENT_URI, Long.parseLong(getAndroidId()));
        return Uri.withAppendedPath(person, ContactsContract.Contacts.Photo.CONTENT_DIRECTORY);
    }

    protected Uri getContactPictureUri() {
        Uri person =
                ContentUris.withAppendedId(
                        ContactsContract.Contacts.CONTENT_URI, Long.parseLong(getAndroidId()));
        return Uri.withAppendedPath(person, ContactsContract.Contacts.Photo.DISPLAY_PHOTO);
    }

    protected void setName(String fn, String ln) {
        if ((fn == null || fn.isEmpty()) && (ln == null || ln.isEmpty())) {
            Log.e("[Contact] Can't set both first and last name to null or empty");
            return;
        }

        if (mAndroidId == null || "0".equals(mAndroidId)) {
            Log.i("[Contact] Setting given & family name " + fn + " " + ln + " to new contact.");
            // TODO
        } else {
            Log.i(
                    "[Contact] Setting given & family name "
                            + fn
                            + " "
                            + ln
                            + " to existing contact "
                            + mAndroidId);
            // TODO
        }
    }

    protected void setOrganization(String org) {
        if (org == null || org.isEmpty()) {
            if (mAndroidId == null) {
                Log.e("[Contact] Can't set organization to null or empty for new contact");
                return;
            }
        }
        if (mAndroidId == null || "0".equals(mAndroidId)) {
            Log.i("[Contact] Setting organization " + org + " to new contact.");
            // TODO
        } else {
            Log.i("[Contact] Setting organization " + org + " to existing contact " + mAndroidId);
            // TODO
        }
    }

    protected void setPhoto(byte[] photo) {
        if (photo == null) {
            Log.e("[Contact] Can't set null picture.");
            return;
        }

        if (mAndroidId == null || "0".equals(mAndroidId)) {
            Log.i("[Contact] Setting picture to new contact.");
            // TODO
        } else {
            Log.i("[Contact] Setting picture to existing contact " + mAndroidId);
            // TODO
        }
    }

    protected void removeNumberOrAddress(String noa) {
        if (noa == null || noa.isEmpty()) {
            Log.e("[Contact] Can't remove null or empty number or address.");
            return;
        }

        if (mAndroidId == null || "0".equals(mAndroidId)) {
            Log.i("[Contact] Removing number or address " + noa + " from new contact.");
            // TODO
        } else {
            Log.i(
                    "[Contact] Removing number or address "
                            + noa
                            + " from existing contact "
                            + mAndroidId);
            // TODO
        }
    }

    protected void addNumberOrAddress(String value, String oldValueToReplace) {
        if (value == null || value.isEmpty()) {
            Log.e("[Contact] Can't add null or empty number or address");
            return;
        }

        if (oldValueToReplace != null) {
            if (mAndroidId != null) {
                Log.e("[Contact] Can't update a number or address in non existing contact");
                return;
            }

            Log.i(
                    "[Contact] Updating "
                            + oldValueToReplace
                            + " by "
                            + value
                            + " in contact "
                            + mAndroidId);
            // TODO
        } else {
            if (mAndroidId == null || "0".equals(mAndroidId)) {
                Log.i("[Contact] Adding number or address " + value + " to new contact.");
                // TODO
            } else {
                Log.i(
                        "[Contact] Adding number or address "
                                + value
                                + " to existing contact "
                                + mAndroidId);
                // TODO
            }
        }
    }

    protected void getAndroidIds() {
        mAndroidRawId = findRawContactID();
        if (LinphoneManager.getInstance()
                .getContext()
                .getResources()
                .getBoolean(R.bool.use_linphone_tag)) {
            mAndroidTagId = findLinphoneRawContactId();
        }
    }

    protected Pair<String, String> getContactNames() {
        Pair<String, String> names = null;
        ContentResolver resolver = LinphoneService.instance().getContentResolver();
        String[] proj =
                new String[] {
                    ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
                    ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME
                };
        String select =
                ContactsContract.Data.CONTACT_ID
                        + "=? AND "
                        + ContactsContract.Data.MIMETYPE
                        + "=?";
        String[] args =
                new String[] {
                    getAndroidId(),
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                };
        Cursor c = resolver.query(ContactsContract.Data.CONTENT_URI, proj, select, args, null);
        if (c != null) {
            if (c.moveToFirst()) {
                String fn =
                        c.getString(
                                c.getColumnIndex(
                                        ContactsContract.CommonDataKinds.StructuredName
                                                .GIVEN_NAME));
                String ln =
                        c.getString(
                                c.getColumnIndex(
                                        ContactsContract.CommonDataKinds.StructuredName
                                                .FAMILY_NAME));
                names = new Pair<>(fn, ln);
            }
            c.close();
        }
        return names;
    }

    protected String getNativeContactOrganization() {
        String org = null;
        ContentResolver resolver = LinphoneService.instance().getContentResolver();
        String[] proj = new String[] {ContactsContract.CommonDataKinds.Organization.COMPANY};
        String select =
                ContactsContract.Data.CONTACT_ID
                        + "=? AND "
                        + ContactsContract.Data.MIMETYPE
                        + "=?";
        String[] args =
                new String[] {
                    getAndroidId(), ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE
                };
        Cursor c = resolver.query(ContactsContract.Data.CONTENT_URI, proj, select, args, null);
        if (c != null) {
            if (c.moveToFirst()) {
                org =
                        c.getString(
                                c.getColumnIndex(
                                        ContactsContract.CommonDataKinds.Organization.COMPANY));
            }
            c.close();
        }
        return org;
    }

    protected String findRawContactID() {
        ContentResolver resolver = LinphoneService.instance().getContentResolver();
        String result = null;
        String[] projection = {ContactsContract.RawContacts._ID};

        String selection = ContactsContract.RawContacts.CONTACT_ID + "=?";
        Cursor c =
                resolver.query(
                        ContactsContract.RawContacts.CONTENT_URI,
                        projection,
                        selection,
                        new String[] {getAndroidId()},
                        null);
        if (c != null) {
            if (c.moveToFirst()) {
                result = c.getString(c.getColumnIndex(ContactsContract.RawContacts._ID));
            }
            c.close();
        }
        return result;
    }

    protected List<LinphoneNumberOrAddress> getAddressesAndNumbersForAndroidContact() {
        List<LinphoneNumberOrAddress> result = new ArrayList<>();
        ContentResolver resolver = LinphoneService.instance().getContentResolver();

        String select =
                ContactsContract.Data.CONTACT_ID
                        + " =? AND ("
                        + ContactsContract.Data.MIMETYPE
                        + "=? OR "
                        + ContactsContract.Data.MIMETYPE
                        + "=? OR "
                        + ContactsContract.Data.MIMETYPE
                        + "=?)";
        String[] projection =
                new String[] {
                    "data1", "data4", ContactsContract.Data.MIMETYPE
                }; // PHONE_NUMBER == SIP_ADDRESS == "data1"...
        Cursor c =
                resolver.query(
                        ContactsContract.Data.CONTENT_URI,
                        projection,
                        select,
                        new String[] {
                            getAndroidId(),
                            ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE,
                            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                            ContactsManager.getInstance().getString(R.string.sync_mimetype)
                        },
                        null);
        if (c != null) {
            while (c.moveToNext()) {
                String mime = c.getString(c.getColumnIndex(ContactsContract.Data.MIMETYPE));
                if (mime != null && mime.length() > 0) {
                    if (mime.equals(ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE)
                            || mime.equals(
                                    ContactsManager.getInstance()
                                            .getString(R.string.sync_mimetype))) {
                        String number = c.getString(c.getColumnIndex("data1")); // SIP_ADDRESS
                        result.add(new LinphoneNumberOrAddress(number, true));
                    } else if (mime.equals(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)) {
                        String number = c.getString(c.getColumnIndex("data1")); // PHONE_NUMBER
                        String normalized_number =
                                c.getString(c.getColumnIndex("data4")); // NORMALIZED_PHONE_NUMBER
                        result.add(new LinphoneNumberOrAddress(number, normalized_number));
                    }
                }
            }
            c.close();
        }
        Collections.sort(result);
        return result;
    }

    protected void createLinphoneTagIfNeeded() {
        if (LinphoneManager.getInstance()
                .getContext()
                .getResources()
                .getBoolean(R.bool.use_linphone_tag)) {
            if (mAndroidTagId == null && findLinphoneRawContactId() == null) {
                createLinphoneContactTag();
            }
        }
    }

    private String findLinphoneRawContactId() {
        ContentResolver resolver = LinphoneService.instance().getContentResolver();
        String result = null;
        String[] projection = {ContactsContract.RawContacts._ID};

        String selection =
                ContactsContract.RawContacts.CONTACT_ID
                        + "=? AND "
                        + ContactsContract.RawContacts.ACCOUNT_TYPE
                        + "=?";
        Cursor c =
                resolver.query(
                        ContactsContract.RawContacts.CONTENT_URI,
                        projection,
                        selection,
                        new String[] {
                            getAndroidId(),
                            ContactsManager.getInstance().getString(R.string.sync_account_type)
                        },
                        null);
        if (c != null) {
            if (c.moveToFirst()) {
                result = c.getString(c.getColumnIndex(ContactsContract.RawContacts._ID));
            }
            c.close();
        }
        return result;
    }

    private void createLinphoneContactTag() {
        ArrayList<ContentProviderOperation> batch = new ArrayList<>();

        /*batch.add(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                        .withValue(
                                ContactsContract.RawContacts.ACCOUNT_TYPE,
                                ContactsManager.getInstance().getString(R.string.sync_account_type))
                        .withValue(
                                ContactsContract.RawContacts.ACCOUNT_NAME,
                                ContactsManager.getInstance().getString(R.string.sync_account_name))
                        .withValue(
                                ContactsContract.RawContacts.AGGREGATION_MODE,
                                ContactsContract.RawContacts.AGGREGATION_MODE_DEFAULT)
                        .build());

        batch.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(
                                ContactsContract.Data.MIMETYPE,
                                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                        .withValue(
                                ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                                getFullName())
                        .build());

        batch.add(
                ContentProviderOperation.newUpdate(
                                ContactsContract.AggregationExceptions.CONTENT_URI)
                        .withValue(
                                ContactsContract.AggregationExceptions.TYPE,
                                ContactsContract.AggregationExceptions.TYPE_KEEP_TOGETHER)
                        .withValue(
                                ContactsContract.AggregationExceptions.RAW_CONTACT_ID1,
                                mAndroidRawId)
                        .withValueBackReference(
                                ContactsContract.AggregationExceptions.RAW_CONTACT_ID2, 0)
                        .build());*/
        // TODO
        Log.i("[Contact] Creating linphone tag");

        try {
            LinphoneService.instance()
                    .getContentResolver()
                    .applyBatch(ContactsContract.AUTHORITY, batch);
            mAndroidTagId = findLinphoneRawContactId();
        } catch (Exception e) {
            Log.e(e);
        }
    }
}
