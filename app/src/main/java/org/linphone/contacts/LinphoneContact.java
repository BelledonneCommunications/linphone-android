package org.linphone.contacts;

/*
LinphoneContact.java
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

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds;

import org.linphone.LinphoneManager;
import org.linphone.LinphoneService;
import org.linphone.R;
import org.linphone.core.Address;
import org.linphone.core.Core;
import org.linphone.core.Friend;
import org.linphone.core.FriendList;
import org.linphone.core.PresenceBasicStatus;
import org.linphone.core.PresenceModel;
import org.linphone.core.SubscribePolicy;
import org.linphone.mediastream.Log;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class LinphoneContact implements Serializable, Comparable<LinphoneContact> {
    private static final long serialVersionUID = 9015568163905205244L;

    private transient Friend friend;
    private String fullName, firstName, lastName, androidId, androidRawId, androidTagId, organization;
    private transient Uri photoUri, thumbnailUri;
    private List<LinphoneNumberOrAddress> addresses;
    private transient ArrayList<ContentProviderOperation> changesToCommit;
    private transient ArrayList<ContentProviderOperation> changesToCommit2;
    private boolean hasSipAddress;

    public LinphoneContact() {
        addresses = new ArrayList<>();
        androidId = null;
        thumbnailUri = null;
        photoUri = null;
        changesToCommit = new ArrayList<>();
        changesToCommit2 = new ArrayList<>();
        hasSipAddress = false;
    }

    @Override
    public int compareTo(LinphoneContact contact) {
        String fullName = getFullName() != null ? getFullName().toUpperCase(Locale.getDefault()) : "";
        String contactFullName = contact.getFullName() != null ? contact.getFullName().toUpperCase(Locale.getDefault()) : "";

        if (fullName.equals(contactFullName)) {
            if (getAndroidId() != null) {
                if (contact.getAndroidId() != null) {
                    int idComp = getAndroidId().compareTo(contact.getAndroidId());
                    if (idComp == 0) return 0;
                    List<LinphoneNumberOrAddress> noas1 = getNumbersOrAddresses();
                    List<LinphoneNumberOrAddress> noas2 = contact.getNumbersOrAddresses();
                    if (noas1.size() == noas2.size()) {
                        if (noas1.containsAll(noas2) && noas2.containsAll(noas1)) {
                            return 0;
                        }
                        return -1;
                    }
                    return ((Integer)noas1.size()).compareTo(noas2.size());
                }
                return -1;
            }
            if (contact.getAndroidId() != null) return 1;
            return 0;
        }
        return fullName.compareTo(contactFullName);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj.getClass() != LinphoneContact.class) return false;
        LinphoneContact contact = (LinphoneContact) obj;
        return (this.compareTo(contact) == 0);
    }

    private void addChangesToCommit(ContentProviderOperation operation, boolean doItAfterAccountCreation) {
        if (doItAfterAccountCreation) {
            changesToCommit2.add(operation);
        } else {
            changesToCommit.add(operation);
        }
    }

    public void setFullName(String name) {
        fullName = name;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFirstNameAndLastName(String fn, String ln, boolean commitChanges) {
        if (fn != null && fn.length() == 0 && ln != null && ln.length() == 0) return;
        if (fn != null && fn.equals(firstName) && ln != null &&  ln.equals(lastName)) return;

        if (isAndroidContact() && commitChanges) {
            if (firstName != null || lastName != null) {
                String select = ContactsContract.Data.CONTACT_ID + "=? AND " + ContactsContract.Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE + "'";
                String[] args = new String[]{getAndroidId()};

                addChangesToCommit(ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                        .withSelection(select, args)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, fn)
                        .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, ln)
                        .build()
                , false);
            } else {
                addChangesToCommit(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, fn)
                        .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, ln)
                        .build()
                , false);
            }
        }

        firstName = fn;
        lastName = ln;
        if (fullName == null) {
            if (firstName != null && lastName != null && firstName.length() > 0 && lastName.length() > 0) {
                fullName = firstName + " " + lastName;
            } else if (firstName != null && firstName.length() > 0) {
                fullName = firstName;
            } else if (lastName != null && lastName.length() > 0) {
                fullName = lastName;
            }
        }
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String org, boolean commitChanges) {
        if (org != null && org.equals(organization)) return;

        if (isAndroidContact() && commitChanges) {
            if (androidRawId != null) {
                String select = ContactsContract.Data.CONTACT_ID + "=? AND " + ContactsContract.Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE + "'";
                String[] args = new String[]{getAndroidId()};

                if (organization != null) {
                    addChangesToCommit(ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                            .withSelection(select, args)
                            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, org)
                            .build()
                    , false);
                } else {
                    addChangesToCommit(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValue(ContactsContract.Data.RAW_CONTACT_ID, androidRawId)
                            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, org)
                            .build()
                    , false);
                }
            } else {
                addChangesToCommit(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, org)
                        .build()
                , false);
            }
        }

        organization = org;
    }

    public boolean hasPhoto() {
        return photoUri != null;
    }

    public void setPhotoUri(Uri uri) {
        if (uri.equals(photoUri)) return;
        photoUri = uri;
    }

    public Uri getPhotoUri() {
        return photoUri;
    }

    public void setThumbnailUri(Uri uri) {
        if (uri.equals(thumbnailUri)) return;
        thumbnailUri = uri;
    }

    public Uri getThumbnailUri() {
        return thumbnailUri;
    }

    public void setPhoto(byte[] photo) {
        if (photo != null) {
            if (isAndroidContact()) {
                if (androidRawId != null) {
                    addChangesToCommit(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValue(ContactsContract.Data.RAW_CONTACT_ID, androidRawId)
                            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, photo)
                            .withValue(ContactsContract.Data.IS_PRIMARY, 1)
                            .withValue(ContactsContract.Data.IS_SUPER_PRIMARY, 1)
                            .build()
                    , false);
                } else {
                    addChangesToCommit(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, photo)
                            .build()
                    , false);
                }
            }
        }
    }

    public void addNumberOrAddress(LinphoneNumberOrAddress noa) {
        if (noa == null) return;
        if (noa.isSIPAddress()) {
            hasSipAddress = true;
            addresses.add(noa);
        } else {
            boolean found = false;
            // Check for duplicated phone numbers but with different formats
            for (LinphoneNumberOrAddress number : addresses) {
                if (!number.isSIPAddress() && noa.getNormalizedPhone().equals(number.getNormalizedPhone())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                addresses.add(noa);
            }
        }
    }

    public List<LinphoneNumberOrAddress> getNumbersOrAddresses() {
        return addresses;
    }

    public boolean hasAddress(String address) {
        for (LinphoneNumberOrAddress noa : getNumbersOrAddresses()) {
            if (noa.isSIPAddress()) {
                String value = noa.getValue();
                if (address.startsWith(value) || value.equals("sip:" + address)) { // Startswith is to workaround the fact the address may have a ;gruu= at the end...
                    return true;
                }
            }
        }
        return false;
    }

    public boolean hasAddress() {
        return hasSipAddress;
    }

    public void removeNumberOrAddress(LinphoneNumberOrAddress noa) {
        if (noa != null && noa.getOldValue() != null) {
            if (isAndroidContact()) {
                if (androidTagId != null && noa.isSIPAddress()) {
                    String select = ContactsContract.Data.RAW_CONTACT_ID + "=? AND " + ContactsContract.Data.DATA1 + "=?";
                    String[] args = new String[]{androidTagId, noa.getOldValue()};

                    addChangesToCommit(ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                            .withSelection(select, args)
                            .build()
                    , false);
                } else {
                    String select;
                    if (noa.isSIPAddress()) {
                        select = ContactsContract.Data.CONTACT_ID + "=? AND " + ContactsContract.Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE + "' AND " + ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS + "=?";
                    } else {
                        select = ContactsContract.Data.CONTACT_ID + "=? AND " + ContactsContract.Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE + "' AND " + ContactsContract.CommonDataKinds.Phone.NUMBER + "=?";
                    }
                    String[] args = new String[]{getAndroidId(), noa.getOldValue()};

                    addChangesToCommit(ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                            .withSelection(select, args)
                            .build()
                    , false);
                }
            }

            if (isFriend()) {
                if (noa.isSIPAddress()) {
                    if (!noa.getOldValue().startsWith("sip:")) {
                        noa.setOldValue("sip:" + noa.getOldValue());
                    }
                }
                LinphoneNumberOrAddress toRemove = null;
                for (LinphoneNumberOrAddress address : addresses) {
                    if (noa.getOldValue().equals(address.getValue()) && noa.isSIPAddress() == address.isSIPAddress()) {
                        toRemove = address;
                        break;
                    }
                }
                if (toRemove != null) {
                    addresses.remove(toRemove);
                }
            }
        }
    }

    public void addOrUpdateNumberOrAddress(LinphoneNumberOrAddress noa) {
        if (noa != null && noa.getValue() != null) {
            if (isAndroidContact()) {
                if (noa.getOldValue() == null) {
                    if (noa.isSIPAddress() && LinphoneManager.getInstance().getContext().getResources().getBoolean(R.bool.use_linphone_tag)) {
                        if (androidTagId != null) {
                            addChangesToCommit(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, androidTagId)
                                    .withValue(ContactsContract.Data.MIMETYPE, ContactsManager.getInstance().getString(R.string.sync_mimetype))
                                    .withValue(ContactsContract.Data.DATA1, noa.getValue())
                                    .withValue(ContactsContract.Data.DATA2, ContactsManager.getInstance().getString(R.string.app_name))
                                    .withValue(ContactsContract.Data.DATA3, noa.getValue())
                                    .build()
                            , false);
                        } else {
                            addChangesToCommit(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                                    .withValue(ContactsContract.Data.MIMETYPE, ContactsManager.getInstance().getString(R.string.sync_mimetype))
                                    .withValue(ContactsContract.Data.DATA1, noa.getValue())
                                    .withValue(ContactsContract.Data.DATA2, ContactsManager.getInstance().getString(R.string.app_name))
                                    .withValue(ContactsContract.Data.DATA3, noa.getValue())
                                    .build()
                            , true);
                        }
                    } else {
                        ContentValues values = new ContentValues();
                        if (noa.isSIPAddress()) {
                            values.put(ContactsContract.Data.MIMETYPE, CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE);
                            values.put(ContactsContract.CommonDataKinds.SipAddress.DATA, noa.getValue());
                            values.put(CommonDataKinds.SipAddress.TYPE, CommonDataKinds.SipAddress.TYPE_CUSTOM);
                            values.put(CommonDataKinds.SipAddress.LABEL, ContactsManager.getInstance().getString(R.string.addressbook_label));
                        } else {
                            values.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
                            values.put(ContactsContract.CommonDataKinds.Phone.NUMBER, noa.getValue());
                            values.put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM);
                            values.put(ContactsContract.CommonDataKinds.Phone.LABEL, ContactsManager.getInstance().getString(R.string.addressbook_label));
                        }
                        if (androidRawId != null) {
                            addChangesToCommit(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, androidRawId)
                                    .withValues(values)
                                    .build()
                            , false);
                        } else {
                            addChangesToCommit(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                                    .withValues(values)
                                    .build()
                            , false);
                        }
                    }
                } else {
                    if (noa.isSIPAddress() && LinphoneManager.getInstance().getContext().getResources().getBoolean(R.bool.use_linphone_tag)) {
                        if (androidTagId != null) {
                            addChangesToCommit(ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                                    .withSelection(ContactsContract.Data.RAW_CONTACT_ID + "=? AND " + ContactsContract.Data.DATA1 + "=? ", new String[]{androidTagId, noa.getOldValue()})
                                    .withValue(ContactsContract.Data.DATA1, noa.getValue())
                                    .withValue(ContactsContract.Data.DATA2, ContactsManager.getInstance().getString(R.string.app_name))
                                    .withValue(ContactsContract.Data.DATA3, noa.getValue())
                                    .build()
                            , false);
                        } else {
                            addChangesToCommit(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                                    .withValue(ContactsContract.Data.MIMETYPE, ContactsManager.getInstance().getString(R.string.sync_mimetype))
                                    .withValue(ContactsContract.Data.DATA1, noa.getValue())
                                    .withValue(ContactsContract.Data.DATA2, ContactsManager.getInstance().getString(R.string.app_name))
                                    .withValue(ContactsContract.Data.DATA3, noa.getValue())
                                    .build()
                            , true);
                        }
                    } else {
                        ContentValues values = new ContentValues();
                        String select;
                        String[] args = new String[]{getAndroidId(), noa.getOldValue()};

                        if (noa.isSIPAddress()) {
                            select = ContactsContract.Data.CONTACT_ID + "=? AND " + ContactsContract.Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE + "' AND " + ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS + "=?";
                            values.put(ContactsContract.Data.MIMETYPE, CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE);
                            values.put(ContactsContract.CommonDataKinds.SipAddress.DATA, noa.getValue());
                        } else {
                            select = ContactsContract.Data.CONTACT_ID + "=? AND " + ContactsContract.Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE + "' AND " + ContactsContract.CommonDataKinds.Phone.NUMBER + "=?";
                            values.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
                            values.put(ContactsContract.CommonDataKinds.Phone.NUMBER, noa.getValue());
                        }
                        addChangesToCommit(ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                                .withSelection(select, args)
                                .withValues(values)
                                .build()
                        , false);
                    }
                }
            }
            if (isFriend()) {
                if (noa.isSIPAddress()) {
                    if (!noa.getValue().startsWith("sip:")) {
                        noa.setValue("sip:" + noa.getValue());
                    }
                }
                if (noa.getOldValue() != null) {
                    if (noa.isSIPAddress()) {
                        if (!noa.getOldValue().startsWith("sip:")) {
                            noa.setOldValue("sip:" + noa.getOldValue());
                        }
                    }
                    for (LinphoneNumberOrAddress address : addresses) {
                        if (noa.getOldValue().equals(address.getValue()) && noa.isSIPAddress() == address.isSIPAddress()) {
                            address.setValue(noa.getValue());
                            break;
                        }
                    }
                } else {
                    addresses.add(noa);
                }
            }
        }
    }

    public void setAndroidId(String id) {
        androidId = id;
        setThumbnailUri(getContactThumbnailPictureUri());
        setPhotoUri(getContactPictureUri());
    }

    public String getAndroidId() {
        return androidId;
    }

    public Friend getFriend() {
        return friend;
    }

    private void createOrUpdateFriend() {
        boolean created = false;
        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc == null) return;

        if (!isFriend()) {
            friend = lc.createFriend();
            friend.enableSubscribes(false);
            friend.setIncSubscribePolicy(SubscribePolicy.SPDeny);
            if (isAndroidContact()) {
                friend.setRefKey(getAndroidId());
            }
            friend.setUserData(this);
            created = true;
        }
        if (isFriend()) {
            friend.edit();
            friend.setName(fullName);
            friend.getVcard().setFamilyName(lastName);
            friend.getVcard().setGivenName(firstName);
            if (organization != null) {
                friend.getVcard().setOrganization(organization);
            }

            if (!created) {
                for (Address address : friend.getAddresses()) {
                    friend.removeAddress(address);
                }
                for (String phone : friend.getPhoneNumbers()) {
                    friend.removePhoneNumber(phone);
                }
            }
            for (LinphoneNumberOrAddress noa : addresses) {
                if (noa.isSIPAddress()) {
                    Address addr = lc.interpretUrl(noa.getValue());
                    if (addr != null) {
                        friend.addAddress(addr);
                    }
                } else {
                    friend.addPhoneNumber(noa.getValue());
                }
            }
            friend.done();
        }
        if (created) {
            lc.addFriend(friend);
        }

        if (!ContactsManager.getInstance().hasContactsAccess()) {
            // This refresh is only needed if app has no contacts permission to refresh the list of Friends.
            // Otherwise contacts will be refreshed due to changes in native contact and the handler in ContactsManager
            ContactsManager.getInstance().fetchContactsAsync();
        }
    }

    public void save() {
        if (isAndroidContact() && ContactsManager.getInstance().hasContactsAccess() && changesToCommit.size() > 0) {
            try {
                LinphoneService.instance().getContentResolver().applyBatch(ContactsContract.AUTHORITY, changesToCommit);
                createLinphoneTagIfNeeded();
            } catch (Exception e) {
                Log.e(e);
            } finally {
                changesToCommit = new ArrayList<>();
                changesToCommit2 = new ArrayList<>();
            }
        }

        createOrUpdateFriend();
    }

    public void delete() {
        if (isAndroidContact()) {
            ContactsManager.getInstance().delete(getAndroidId());
        }
        if (isFriend()) {
            deleteFriend();
        }
    }

    public void deleteFriend() {
        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (friend != null && lc != null) {
            for (FriendList list : lc.getFriendsLists()) {
                list.removeFriend(friend);
            }
        }
    }

    public void clearAddresses() {
        addresses.clear();
    }

    public void refresh() {
        addresses = new ArrayList<>();
        if (isAndroidContact()) {
            getContactNames();
            getNativeContactOrganization();
            getAndroidIds();
            hasSipAddress = false;
            for (LinphoneNumberOrAddress noa : getAddressesAndNumbersForAndroidContact()) {
                addNumberOrAddress(noa);
            }
        } else if (isFriend()) {
            fullName = friend.getName();
            lastName = friend.getVcard().getFamilyName();
            firstName = friend.getVcard().getGivenName();
            thumbnailUri = null;
            photoUri = null;
            hasSipAddress = friend.getAddress() != null;
            organization = friend.getVcard().getOrganization();

            Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
            if (lc != null && lc.vcardSupported()) {
                for (Address addr : friend.getAddresses()) {
                    if (addr != null) {
                        addNumberOrAddress(new LinphoneNumberOrAddress(addr.asStringUriOnly(), true));
                    }
                }
                for (String tel : friend.getPhoneNumbers()) {
                    if (tel != null) {
                        addNumberOrAddress(new LinphoneNumberOrAddress(tel, false));
                    }
                }
            } else {
                Address addr = friend.getAddress();
                addNumberOrAddress(new LinphoneNumberOrAddress(addr.asStringUriOnly(), true));
            }
        }
    }

    public void createOrUpdateFriendFromNativeContact() {
        if (isAndroidContact()) {
            createOrUpdateFriend();
        }
    }

    public boolean isAndroidContact() {
        return androidId != null;
    }

    public boolean isFriend() {
        return friend != null;
    }

    public boolean isInFriendList() {
        if (friend == null) return false;
        for (LinphoneNumberOrAddress noa : addresses) {
            PresenceModel pm = friend.getPresenceModelForUriOrTel(noa.getValue());
            if (pm != null && pm.getBasicStatus().equals(PresenceBasicStatus.Open)) {
                return true;
            }
        }
        return false;
    }

    public String getPresenceModelForUriOrTel(String uri) {
        if (friend != null && friend.getPresenceModelForUriOrTel(uri) != null) {
            return friend.getPresenceModelForUriOrTel(uri).getContact();
        }
        return null;
    }

    public void setFriend(Friend f) {
        friend = f;
        friend.setUserData(this);
    }

    public void getAndroidIds() {
        androidRawId = findRawContactID();
        if (LinphoneManager.getInstance().getContext().getResources().getBoolean(R.bool.use_linphone_tag)) {
            androidTagId = findLinphoneRawContactId();
        }
    }

    public static LinphoneContact createContact() {
        if (ContactsManager.getInstance().hasContactsAccess()) {
            return createAndroidContact();
        }
        return createFriend();
    }

    private Uri getContactThumbnailPictureUri() {
        Uri person = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, Long.parseLong(getAndroidId()));
        return Uri.withAppendedPath(person, ContactsContract.Contacts.Photo.CONTENT_DIRECTORY);
    }

    private Uri getContactPictureUri() {
        Uri person = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, Long.parseLong(getAndroidId()));
        return Uri.withAppendedPath(person, ContactsContract.Contacts.Photo.DISPLAY_PHOTO);
    }

    private void getContactNames() {
        ContentResolver resolver = LinphoneService.instance().getContentResolver();
        String[] proj = new String[]{ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME};
        String select = ContactsContract.Data.CONTACT_ID + "=? AND " + ContactsContract.Data.MIMETYPE + "=?";
        String[] args = new String[]{getAndroidId(), ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE};
        Cursor c = resolver.query(ContactsContract.Data.CONTENT_URI, proj, select, args, null);
        if (c != null) {
            if (c.moveToFirst()) {
                firstName = c.getString(c.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME));
                lastName = c.getString(c.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME));
            }
            c.close();
        }
    }

    private void getNativeContactOrganization() {
        ContentResolver resolver = LinphoneService.instance().getContentResolver();
        String[] proj = new String[]{ContactsContract.CommonDataKinds.Organization.COMPANY};
        String select = ContactsContract.Data.CONTACT_ID + "=? AND " + ContactsContract.Data.MIMETYPE + "=?";
        String[] args = new String[]{getAndroidId(), ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE};
        Cursor c = resolver.query(ContactsContract.Data.CONTENT_URI, proj, select, args, null);
        if (c != null) {
            if (c.moveToFirst()) {
                organization = c.getString(c.getColumnIndex(ContactsContract.CommonDataKinds.Organization.COMPANY));
            }
            c.close();
        }
    }

    private String findRawContactID() {
        ContentResolver resolver = LinphoneService.instance().getContentResolver();
        String result = null;
        String[] projection = {ContactsContract.RawContacts._ID};

        String selection = ContactsContract.RawContacts.CONTACT_ID + "=?";
        Cursor c = resolver.query(ContactsContract.RawContacts.CONTENT_URI, projection, selection, new String[]{getAndroidId()}, null);
        if (c != null) {
            if (c.moveToFirst()) {
                result = c.getString(c.getColumnIndex(ContactsContract.RawContacts._ID));
            }
            c.close();
        }
        return result;
    }

    private List<LinphoneNumberOrAddress> getAddressesAndNumbersForAndroidContact() {
        List<LinphoneNumberOrAddress> result = new ArrayList<>();
        ContentResolver resolver = LinphoneService.instance().getContentResolver();

        String select = ContactsContract.Data.CONTACT_ID + " =? AND (" + ContactsContract.Data.MIMETYPE + "=? OR " + ContactsContract.Data.MIMETYPE + "=? OR " + ContactsContract.Data.MIMETYPE + "=?)";
        String[] projection = new String[]{"data1", "data4", ContactsContract.Data.MIMETYPE}; // PHONE_NUMBER == SIP_ADDRESS == "data1"...
        Cursor c = resolver.query(ContactsContract.Data.CONTENT_URI, projection, select, new String[]{getAndroidId(), ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE, ContactsManager.getInstance().getString(R.string.sync_mimetype)}, null);
        if (c != null) {
            while (c.moveToNext()) {
                String mime = c.getString(c.getColumnIndex(ContactsContract.Data.MIMETYPE));
                if (mime != null && mime.length() > 0) {
                    if (mime.equals(ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE) || mime.equals(ContactsManager.getInstance().getString(R.string.sync_mimetype))) {
                        String number = c.getString(c.getColumnIndex("data1")); // SIP_ADDRESS
                        result.add(new LinphoneNumberOrAddress(number, true));
                    } else if (mime.equals(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)) {
                        String number = c.getString(c.getColumnIndex("data1")); // PHONE_NUMBER
                        String normalized_number = c.getString(c.getColumnIndex("data4")); // NORMALIZED_PHONE_NUMBER
                        result.add(new LinphoneNumberOrAddress(number, normalized_number));
                    }
                }
            }
            c.close();
        }
        Collections.sort(result);
        return result;
    }

    private static LinphoneContact createAndroidContact() {
        LinphoneContact contact = new LinphoneContact();

        contact.changesToCommit.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .withValue(ContactsContract.RawContacts.AGGREGATION_MODE, ContactsContract.RawContacts.AGGREGATION_MODE_DEFAULT)
                .build());
        contact.setAndroidId("0");

        return contact;
    }

    private static LinphoneContact createFriend() {
        LinphoneContact contact = new LinphoneContact();
        Friend friend = LinphoneManager.getLc().createFriend();
        // Disable subscribes for now
        friend.enableSubscribes(false);
        friend.setIncSubscribePolicy(SubscribePolicy.SPDeny);
        contact.friend = friend;
        friend.setUserData(contact);
        return contact;
    }

    private String findLinphoneRawContactId() {
        ContentResolver resolver = LinphoneService.instance().getContentResolver();
        String result = null;
        String[] projection = {ContactsContract.RawContacts._ID};

        String selection = ContactsContract.RawContacts.CONTACT_ID + "=? AND " + ContactsContract.RawContacts.ACCOUNT_TYPE + "=?";
        Cursor c = resolver.query(ContactsContract.RawContacts.CONTENT_URI, projection, selection, new String[]{getAndroidId(), ContactsManager.getInstance().getString(R.string.sync_account_type)}, null);
        if (c != null) {
            if (c.moveToFirst()) {
                result = c.getString(c.getColumnIndex(ContactsContract.RawContacts._ID));
            }
            c.close();
        }
        return result;
    }

    private void createLinphoneTagIfNeeded() {
        if (LinphoneManager.getInstance().getContext().getResources().getBoolean(R.bool.use_linphone_tag)) {
            if (androidTagId == null && findLinphoneRawContactId() == null) {
                createLinphoneContactTag();
            }
        }
    }

    private void createLinphoneContactTag() {
        ArrayList<ContentProviderOperation> batch = new ArrayList<>();

        batch.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, ContactsManager.getInstance().getString(R.string.sync_account_type))
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, ContactsManager.getInstance().getString(R.string.sync_account_name))
                .withValue(ContactsContract.RawContacts.AGGREGATION_MODE, ContactsContract.RawContacts.AGGREGATION_MODE_DEFAULT)
                .build());

        batch.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, getFullName())
                .build());

        batch.add(ContentProviderOperation.newUpdate(ContactsContract.AggregationExceptions.CONTENT_URI)
                .withValue(ContactsContract.AggregationExceptions.TYPE, ContactsContract.AggregationExceptions.TYPE_KEEP_TOGETHER)
                .withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID1, androidRawId)
                .withValueBackReference(ContactsContract.AggregationExceptions.RAW_CONTACT_ID2, 0)
                .build());

        if (changesToCommit2.size() > 0) {
            for (ContentProviderOperation cpo : changesToCommit2) {
                batch.add(cpo);
            }
        }

        try {
            LinphoneService.instance().getContentResolver().applyBatch(ContactsContract.AUTHORITY, batch);
            androidTagId = findLinphoneRawContactId();
        } catch (Exception e) {
            Log.e(e);
        }
    }
}
