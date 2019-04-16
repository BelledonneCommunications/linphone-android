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
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import java.io.Serializable;
import java.util.ArrayList;
import org.linphone.LinphoneManager;
import org.linphone.LinphoneService;
import org.linphone.R;
import org.linphone.core.tools.Log;

class AndroidContact implements Serializable {
    protected String mAndroidId, mAndroidRawId, mAndroidLookupKey;
    protected boolean isAndroidRawIdLinphone;

    private transient ArrayList<ContentProviderOperation> mChangesToCommit;

    protected AndroidContact() {
        mChangesToCommit = new ArrayList<>();
        isAndroidRawIdLinphone = false;
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
                Long.parseLong(mAndroidId), getAndroidLookupKey());
    }

    protected boolean isAndroidContact() {
        return mAndroidId != null;
    }

    protected void addChangesToCommit(ContentProviderOperation operation) {
        Log.i("[Contact] Added operation " + operation);
        mChangesToCommit.add(operation);
    }

    protected void saveChangesCommited() {
        if (ContactsManager.getInstance().hasReadContactsAccess() && mChangesToCommit.size() > 0) {
            try {
                ContentResolver contentResolver = LinphoneService.instance().getContentResolver();
                ContentProviderResult[] results =
                        contentResolver.applyBatch(ContactsContract.AUTHORITY, mChangesToCommit);
                if (results != null
                        && results.length > 0
                        && results[0] != null
                        && results[0].uri != null) {
                    String rawId = String.valueOf(ContentUris.parseId(results[0].uri));
                    if (mAndroidId == null) {
                        Log.i("[Contact] Contact created with RAW ID " + rawId);

                        final String[] projection =
                                new String[] {ContactsContract.RawContacts.CONTACT_ID};
                        final Cursor cursor =
                                contentResolver.query(results[0].uri, projection, null, null, null);
                        if (cursor != null) {
                            cursor.moveToNext();
                            long contactId = cursor.getLong(0);
                            mAndroidId = String.valueOf(contactId);
                            cursor.close();
                            Log.i("[Contact] Contact created with ID " + mAndroidId);
                        }
                    } else {
                        if (mAndroidRawId == null || !isAndroidRawIdLinphone) {
                            Log.i(
                                    "[Contact] Linphone RAW ID "
                                            + rawId
                                            + " created from existing RAW ID "
                                            + mAndroidRawId);
                            mAndroidRawId = rawId;
                            isAndroidRawIdLinphone = true;
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("[Contact] Exception while saving changes: " + e);
            } finally {
                mChangesToCommit.clear();
            }
        }
    }

    protected void createAndroidContact() {
        if (LinphoneManager.getInstance()
                .getContext()
                .getResources()
                .getBoolean(R.bool.use_linphone_tag)) {
            Log.i("[Contact] Creating contact using linphone account type");
            addChangesToCommit(
                    ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                            .withValue(
                                    RawContacts.ACCOUNT_TYPE,
                                    ContactsManager.getInstance()
                                            .getString(R.string.sync_account_type))
                            .withValue(
                                    RawContacts.ACCOUNT_NAME,
                                    ContactsManager.getInstance()
                                            .getString(R.string.sync_account_name))
                            .withValue(
                                    RawContacts.AGGREGATION_MODE,
                                    RawContacts.AGGREGATION_MODE_DEFAULT)
                            .build());
            isAndroidRawIdLinphone = true;
        } else {
            Log.i("[Contact] Creating contact using default account type");
            addChangesToCommit(
                    ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                            .withValue(RawContacts.ACCOUNT_TYPE, null)
                            .withValue(RawContacts.ACCOUNT_NAME, null)
                            .withValue(
                                    RawContacts.AGGREGATION_MODE,
                                    RawContacts.AGGREGATION_MODE_DEFAULT)
                            .build());
        }
    }

    protected void deleteAndroidContact() {
        ContactsManager.getInstance().delete(mAndroidId);
    }

    protected Uri getContactThumbnailPictureUri() {
        Uri person = ContentUris.withAppendedId(Contacts.CONTENT_URI, Long.parseLong(mAndroidId));
        return Uri.withAppendedPath(person, Contacts.Photo.CONTENT_DIRECTORY);
    }

    protected Uri getContactPictureUri() {
        Uri person = ContentUris.withAppendedId(Contacts.CONTENT_URI, Long.parseLong(mAndroidId));
        return Uri.withAppendedPath(person, Contacts.Photo.DISPLAY_PHOTO);
    }

    protected void setName(String fn, String ln) {
        if ((fn == null || fn.isEmpty()) && (ln == null || ln.isEmpty())) {
            Log.e("[Contact] Can't set both first and last name to null or empty");
            return;
        }

        if (mAndroidId == null) {
            Log.i("[Contact] Setting given & family name " + fn + " " + ln + " to new contact.");
            addChangesToCommit(
                    ContentProviderOperation.newInsert(Data.CONTENT_URI)
                            .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                            .withValue(
                                    Data.MIMETYPE, CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                            .withValue(CommonDataKinds.StructuredName.GIVEN_NAME, fn)
                            .withValue(CommonDataKinds.StructuredName.FAMILY_NAME, ln)
                            .build());
        } else {
            Log.i(
                    "[Contact] Setting given & family name "
                            + fn
                            + " "
                            + ln
                            + " to existing contact "
                            + mAndroidId
                            + " ("
                            + mAndroidRawId
                            + ")");
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

            addChangesToCommit(
                    ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                            .withSelection(select, args)
                            .withValue(
                                    ContactsContract.Data.MIMETYPE,
                                    ContactsContract.CommonDataKinds.StructuredName
                                            .CONTENT_ITEM_TYPE)
                            .withValue(
                                    ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, fn)
                            .withValue(
                                    ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, ln)
                            .build());
        }
    }

    protected void addNumberOrAddress(String value, String oldValueToReplace, boolean isSIP) {
        if (value == null || value.isEmpty()) {
            Log.e("[Contact] Can't add null or empty number or address");
            return;
        }

        if (oldValueToReplace != null) {
            if (mAndroidId == null) {
                Log.e("[Contact] Can't update a number or address in non existing contact");
                return;
            }

            Log.i(
                    "[Contact] Updating "
                            + oldValueToReplace
                            + " by "
                            + value
                            + " in contact "
                            + mAndroidId
                            + " ("
                            + mAndroidRawId
                            + ")");
            if (isSIP) {
                String select =
                        ContactsContract.Data.CONTACT_ID
                                + "=? AND ("
                                + ContactsContract.Data.MIMETYPE
                                + "=? OR "
                                + ContactsContract.Data.MIMETYPE
                                + "=? OR "
                                + ContactsContract.Data.MIMETYPE
                                + "=?) AND data1=?";
                String[] args =
                        new String[] {
                            mAndroidId,
                            "vnd.android.cursor.item/org.linphone.profile", // Old value
                            ContactsManager.getInstance()
                                    .getString(R.string.linphone_address_mime_type),
                            ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE,
                            oldValueToReplace
                        };

                addChangesToCommit(
                        ContentProviderOperation.newUpdate(Data.CONTENT_URI)
                                .withSelection(select, args)
                                .withValue(
                                        Data.MIMETYPE,
                                        ContactsManager.getInstance()
                                                .getString(R.string.linphone_address_mime_type))
                                .withValue("data1", value) // Value
                                .withValue(
                                        "data2",
                                        ContactsManager.getInstance()
                                                .getString(R.string.app_name)) // Summary
                                .withValue("data3", value) // Detail
                                .build());
            } else {
                String select =
                        ContactsContract.Data.CONTACT_ID
                                + "=? AND "
                                + ContactsContract.Data.MIMETYPE
                                + "=? AND data1=?";
                String[] args =
                        new String[] {
                            mAndroidId,
                            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                            oldValueToReplace
                        };

                addChangesToCommit(
                        ContentProviderOperation.newUpdate(Data.CONTENT_URI)
                                .withSelection(select, args)
                                .withValue(Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, value)
                                .withValue(
                                        ContactsContract.CommonDataKinds.Phone.TYPE,
                                        CommonDataKinds.Phone.TYPE_MOBILE)
                                .build());
            }
        } else {
            if (mAndroidId == null) {
                Log.i("[Contact] Adding number or address " + value + " to new contact.");
                if (isSIP) {
                    addChangesToCommit(
                            ContentProviderOperation.newInsert(Data.CONTENT_URI)
                                    .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                                    .withValue(
                                            Data.MIMETYPE,
                                            ContactsManager.getInstance()
                                                    .getString(R.string.linphone_address_mime_type))
                                    .withValue("data1", value) // Value
                                    .withValue(
                                            "data2",
                                            ContactsManager.getInstance()
                                                    .getString(R.string.app_name)) // Summary
                                    .withValue("data3", value) // Detail
                                    .build());
                } else {
                    addChangesToCommit(
                            ContentProviderOperation.newInsert(Data.CONTENT_URI)
                                    .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                                    .withValue(
                                            Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, value)
                                    .withValue(
                                            ContactsContract.CommonDataKinds.Phone.TYPE,
                                            CommonDataKinds.Phone.TYPE_MOBILE)
                                    .build());
                }
            } else {
                Log.i(
                        "[Contact] Adding number or address "
                                + value
                                + " to existing contact "
                                + mAndroidId
                                + " ("
                                + mAndroidRawId
                                + ")");
                if (isSIP) {
                    addChangesToCommit(
                            ContentProviderOperation.newInsert(Data.CONTENT_URI)
                                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, mAndroidRawId)
                                    .withValue(
                                            Data.MIMETYPE,
                                            ContactsManager.getInstance()
                                                    .getString(R.string.linphone_address_mime_type))
                                    .withValue("data1", value) // Value
                                    .withValue(
                                            "data2",
                                            ContactsManager.getInstance()
                                                    .getString(R.string.app_name)) // Summary
                                    .withValue("data3", value) // Detail
                                    .build());
                } else {
                    addChangesToCommit(
                            ContentProviderOperation.newInsert(Data.CONTENT_URI)
                                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, mAndroidRawId)
                                    .withValue(
                                            Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, value)
                                    .withValue(
                                            ContactsContract.CommonDataKinds.Phone.TYPE,
                                            CommonDataKinds.Phone.TYPE_MOBILE)
                                    .build());
                }
            }
        }
    }

    protected void removeNumberOrAddress(String noa, boolean isSIP) {
        if (noa == null || noa.isEmpty()) {
            Log.e("[Contact] Can't remove null or empty number or address.");
            return;
        }

        if (mAndroidId == null) {
            Log.e("[Contact] Can't remove a number or address from non existing contact");
            return;
        } else {
            Log.i(
                    "[Contact] Removing number or address "
                            + noa
                            + " from existing contact "
                            + mAndroidId
                            + " ("
                            + mAndroidRawId
                            + ")");
            if (isSIP) {
                String select =
                        ContactsContract.Data.CONTACT_ID
                                + "=? AND ("
                                + ContactsContract.Data.MIMETYPE
                                + "=? OR "
                                + ContactsContract.Data.MIMETYPE
                                + "=?) AND data1=?";
                String[] args =
                        new String[] {
                            mAndroidId,
                            ContactsManager.getInstance()
                                    .getString(R.string.linphone_address_mime_type),
                            ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE,
                            noa
                        };

                addChangesToCommit(
                        ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                                .withSelection(select, args)
                                .build());
            } else {
                String select =
                        ContactsContract.Data.CONTACT_ID
                                + "=? AND "
                                + ContactsContract.Data.MIMETYPE
                                + "=? AND data1=?";
                String[] args =
                        new String[] {
                            mAndroidId,
                            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                            noa
                        };

                addChangesToCommit(
                        ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                                .withSelection(select, args)
                                .build());
            }
        }
    }

    protected void setOrganization(String org, String previousValue) {
        if (org == null || org.isEmpty()) {
            if (mAndroidId == null) {
                Log.e("[Contact] Can't set organization to null or empty for new contact");
                return;
            }
        }
        if (mAndroidId == null) {
            Log.i("[Contact] Setting organization " + org + " to new contact.");
            addChangesToCommit(
                    ContentProviderOperation.newInsert(Data.CONTENT_URI)
                            .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                            .withValue(
                                    Data.MIMETYPE, CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                            .withValue(CommonDataKinds.Organization.COMPANY, org)
                            .build());
        } else {
            if (previousValue != null) {
                String select =
                        ContactsContract.Data.CONTACT_ID
                                + "=? AND "
                                + ContactsContract.Data.MIMETYPE
                                + "=? AND "
                                + ContactsContract.CommonDataKinds.Organization.COMPANY
                                + "=?";
                String[] args =
                        new String[] {
                            getAndroidId(),
                            ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE,
                            previousValue
                        };

                Log.i(
                        "[Contact] Updating organization "
                                + org
                                + " to existing contact "
                                + mAndroidId
                                + " ("
                                + mAndroidRawId
                                + ")");
                addChangesToCommit(
                        ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                                .withSelection(select, args)
                                .withValue(
                                        ContactsContract.Data.MIMETYPE,
                                        ContactsContract.CommonDataKinds.Organization
                                                .CONTENT_ITEM_TYPE)
                                .withValue(
                                        ContactsContract.CommonDataKinds.Organization.COMPANY, org)
                                .build());
            } else {
                Log.i(
                        "[Contact] Setting organization "
                                + org
                                + " to existing contact "
                                + mAndroidId
                                + " ("
                                + mAndroidRawId
                                + ")");
                addChangesToCommit(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                .withValue(ContactsContract.Data.RAW_CONTACT_ID, mAndroidRawId)
                                .withValue(
                                        ContactsContract.Data.MIMETYPE,
                                        ContactsContract.CommonDataKinds.Organization
                                                .CONTENT_ITEM_TYPE)
                                .withValue(
                                        ContactsContract.CommonDataKinds.Organization.COMPANY, org)
                                .build());
            }
        }
    }

    protected void setPhoto(byte[] photo) {
        if (photo == null) {
            Log.e("[Contact] Can't set null picture.");
            return;
        }

        if (mAndroidId == null) {
            Log.i("[Contact] Setting picture to new contact.");
            addChangesToCommit(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                            .withValue(
                                    ContactsContract.Data.MIMETYPE,
                                    ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, photo)
                            .build());
        } else {
            Log.i(
                    "[Contact] Setting picture to existing contact "
                            + mAndroidId
                            + " ("
                            + mAndroidRawId
                            + ")");
            addChangesToCommit(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValue(ContactsContract.Data.RAW_CONTACT_ID, mAndroidRawId)
                            .withValue(
                                    ContactsContract.Data.MIMETYPE,
                                    ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, photo)
                            .withValue(ContactsContract.Data.IS_PRIMARY, 1)
                            .withValue(ContactsContract.Data.IS_SUPER_PRIMARY, 1)
                            .build());
        }
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
                        new String[] {mAndroidId},
                        null);
        if (c != null) {
            if (c.moveToFirst()) {
                result = c.getString(c.getColumnIndex(ContactsContract.RawContacts._ID));
            }
            c.close();
        }
        return result;
    }

    protected void createRawLinphoneContactFromExistingAndroidContactIfNeeded(String fullName) {
        if (LinphoneManager.getInstance()
                .getContext()
                .getResources()
                .getBoolean(R.bool.use_linphone_tag)) {
            if (mAndroidId != null && (mAndroidRawId == null || !isAndroidRawIdLinphone)) {
                if (mAndroidRawId == null) {
                    Log.i("[Contact] RAW ID not found for contact " + mAndroidId);
                    mAndroidRawId = findRawContactID();
                }
                Log.i("[Contact] Found RAW ID for contact " + mAndroidId + " : " + mAndroidRawId);

                String linphoneRawId = findLinphoneRawContactId();
                if (linphoneRawId == null) {
                    Log.i("[Contact] Linphone RAW ID not found for contact " + mAndroidId);
                    createRawLinphoneContactFromExistingAndroidContact();
                } else {
                    Log.i(
                            "[Contact] Linphone RAW ID found for contact "
                                    + mAndroidId
                                    + " : "
                                    + linphoneRawId);
                    mAndroidRawId = linphoneRawId;
                }
                isAndroidRawIdLinphone = true;
            }
        }
    }

    private void createRawLinphoneContactFromExistingAndroidContact() {
        addChangesToCommit(
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

        addChangesToCommit(
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
                        .build());

        Log.i(
                "[Contact] Creating linphone RAW contact for contact "
                        + mAndroidId
                        + " linked with existing RAW contact "
                        + mAndroidRawId);
        saveChangesCommited();
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
                            mAndroidId,
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
}
