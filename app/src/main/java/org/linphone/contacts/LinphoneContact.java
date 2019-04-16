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

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.Address;
import org.linphone.core.Core;
import org.linphone.core.Friend;
import org.linphone.core.FriendCapability;
import org.linphone.core.FriendList;
import org.linphone.core.PresenceBasicStatus;
import org.linphone.core.PresenceModel;
import org.linphone.core.SubscribePolicy;

public class LinphoneContact extends AndroidContact
        implements Serializable, Comparable<LinphoneContact> {
    private static final long serialVersionUID = 9015568163905205244L;

    private transient Friend mFriend;
    private String mFullName, mFirstName, mLastName, mOrganization;
    private transient Uri mPhotoUri, mThumbnailUri;
    private List<LinphoneNumberOrAddress> mAddresses;
    private boolean mHasSipAddress;

    public LinphoneContact() {
        super();
        mAddresses = new ArrayList<>();
        mAndroidId = null;
        mAndroidLookupKey = null;
        mThumbnailUri = null;
        mPhotoUri = null;
        mHasSipAddress = false;
    }

    public static LinphoneContact createContact() {
        LinphoneContact contact = new LinphoneContact();
        if (ContactsManager.getInstance().hasReadContactsAccess()) {
            contact.createAndroidContact();
        } else {
            contact.createFriend();
        }
        return contact;
    }

    @Override
    public int compareTo(LinphoneContact contact) {
        String fullName =
                getFullName() != null ? getFullName().toUpperCase(Locale.getDefault()) : "";
        String contactFullName =
                contact.getFullName() != null
                        ? contact.getFullName().toUpperCase(Locale.getDefault())
                        : "";

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
                    return Integer.compare(noas1.size(), noas2.size());
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

    /*
       Name related
    */

    public String getFullName() {
        return mFullName;
    }

    public void setFullName(String name) {
        mFullName = name;
    }

    public void setFirstNameAndLastName(String fn, String ln, boolean commitChanges) {
        if (fn != null && fn.length() == 0 && ln != null && ln.length() == 0) return;
        if (fn != null && fn.equals(mFirstName) && ln != null && ln.equals(mLastName)) return;

        if (commitChanges) {
            setName(fn, ln);
        }

        mFirstName = fn;
        mLastName = ln;
        if (mFullName == null) {
            if (mFirstName != null
                    && mLastName != null
                    && mFirstName.length() > 0
                    && mLastName.length() > 0) {
                mFullName = mFirstName + " " + mLastName;
            } else if (mFirstName != null && mFirstName.length() > 0) {
                mFullName = mFirstName;
            } else if (mLastName != null && mLastName.length() > 0) {
                mFullName = mLastName;
            }
        }
    }

    public String getFirstName() {
        return mFirstName;
    }

    public String getLastName() {
        return mLastName;
    }

    /*
       Organization related
    */

    public String getOrganization() {
        return mOrganization;
    }

    public void setOrganization(String org, boolean commitChanges) {
        if ((org == null || org.isEmpty()) && (mOrganization == null || mOrganization.isEmpty()))
            return;
        if (org != null && org.equals(mOrganization)) return;

        if (commitChanges) {
            setOrganization(org, mOrganization);
        }

        mOrganization = org;
    }

    /*
       Picture related
    */

    public boolean hasPhoto() {
        return mPhotoUri != null;
    }

    public Uri getPhotoUri() {
        return mPhotoUri;
    }

    private void setPhotoUri(Uri uri) {
        if (uri.equals(mPhotoUri)) return;
        mPhotoUri = uri;
    }

    public Uri getThumbnailUri() {
        return mThumbnailUri;
    }

    private void setThumbnailUri(Uri uri) {
        if (uri.equals(mThumbnailUri)) return;
        mThumbnailUri = uri;
    }

    /*
       Number or address related
    */

    public void addNumberOrAddress(LinphoneNumberOrAddress noa) {
        if (noa == null) return;
        if (noa.isSIPAddress()) {
            mHasSipAddress = true;
            mAddresses.add(noa);
        } else {
            boolean found = false;
            // Check for duplicated phone numbers but with different formats
            for (LinphoneNumberOrAddress number : mAddresses) {
                if (!number.isSIPAddress()
                        && noa.getNormalizedPhone().equals(number.getNormalizedPhone())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                mAddresses.add(noa);
            }
        }
    }

    public List<LinphoneNumberOrAddress> getNumbersOrAddresses() {
        return mAddresses;
    }

    public boolean hasAddress(String address) {
        for (LinphoneNumberOrAddress noa : getNumbersOrAddresses()) {
            if (noa.isSIPAddress()) {
                String value = noa.getValue();
                if (address.startsWith(value) || value.equals("sip:" + address)) {
                    // Startswith is to workaround the fact that the
                    // address may have a ;gruu= at the end...
                    return true;
                }
            }
        }
        return false;
    }

    public boolean hasAddress() {
        return mHasSipAddress;
    }

    public void removeNumberOrAddress(LinphoneNumberOrAddress noa) {
        if (noa != null && noa.getOldValue() != null) {

            removeNumberOrAddress(noa.getOldValue(), noa.isSIPAddress());

            if (isFriend()) {
                if (noa.isSIPAddress()) {
                    if (!noa.getOldValue().startsWith("sip:")) {
                        noa.setOldValue("sip:" + noa.getOldValue());
                    }
                }
                LinphoneNumberOrAddress toRemove = null;
                for (LinphoneNumberOrAddress address : mAddresses) {
                    if (noa.getOldValue().equals(address.getValue())
                            && noa.isSIPAddress() == address.isSIPAddress()) {
                        toRemove = address;
                        break;
                    }
                }
                if (toRemove != null) {
                    mAddresses.remove(toRemove);
                }
            }
        }
    }

    public void addOrUpdateNumberOrAddress(LinphoneNumberOrAddress noa) {
        if (noa != null && noa.getValue() != null) {

            addNumberOrAddress(noa.getValue(), noa.getOldValue(), noa.isSIPAddress());

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
                    for (LinphoneNumberOrAddress address : mAddresses) {
                        if (noa.getOldValue().equals(address.getValue())
                                && noa.isSIPAddress() == address.isSIPAddress()) {
                            address.setValue(noa.getValue());
                            break;
                        }
                    }
                } else {
                    mAddresses.add(noa);
                }
            }
        }
    }

    public void clearAddresses() {
        mAddresses.clear();
    }

    /*
    Friend related
     */

    public Friend getFriend() {
        return mFriend;
    }

    private void createOrUpdateFriend() {
        boolean created = false;
        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc == null) return;

        if (!isFriend()) {
            mFriend = lc.createFriend();
            mFriend.enableSubscribes(false);
            mFriend.setIncSubscribePolicy(SubscribePolicy.SPDeny);
            if (isAndroidContact()) {
                mFriend.setRefKey(getAndroidId());
            }
            mFriend.setUserData(this);
            created = true;
        }
        if (isFriend()) {
            mFriend.edit();
            mFriend.setName(mFullName);
            if (mFriend.getVcard() != null) {
                mFriend.getVcard().setFamilyName(mLastName);
                mFriend.getVcard().setGivenName(mFirstName);
            }
            if (mOrganization != null) {
                mFriend.getVcard().setOrganization(mOrganization);
            }

            if (!created) {
                for (Address address : mFriend.getAddresses()) {
                    mFriend.removeAddress(address);
                }
                for (String phone : mFriend.getPhoneNumbers()) {
                    mFriend.removePhoneNumber(phone);
                }
            }
            for (LinphoneNumberOrAddress noa : mAddresses) {
                if (noa.isSIPAddress()) {
                    Address addr = lc.interpretUrl(noa.getValue());
                    if (addr != null) {
                        mFriend.addAddress(addr);
                    }
                } else {
                    mFriend.addPhoneNumber(noa.getValue());
                }
            }
            mFriend.done();
        }
        if (created) {
            lc.addFriend(mFriend);
        }

        if (!ContactsManager.getInstance().hasReadContactsAccess()) {
            // This refresh is only needed if app has no contacts permission to refresh the list of
            // Friends.
            // Otherwise contacts will be refreshed due to changes in native contact and the handler
            // in ContactsManager
            ContactsManager.getInstance().fetchContactsAsync();
        }
    }

    public void deleteFriend() {
        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (mFriend != null && lc != null) {
            for (FriendList list : lc.getFriendsLists()) {
                list.removeFriend(mFriend);
            }
        }
    }

    public void createOrUpdateFriendFromNativeContact() {
        if (isAndroidContact()) {
            createOrUpdateFriend();
        }
    }

    public boolean isFriend() {
        return mFriend != null;
    }

    public void setFriend(Friend f) {
        if (mFriend != null && (f == null || f != mFriend)) {
            mFriend.setUserData(null);
        }
        mFriend = f;
        if (mFriend != null) {
            mFriend.setUserData(this);
        }
    }

    public boolean isInFriendList() {
        if (mFriend == null) return false;
        for (LinphoneNumberOrAddress noa : mAddresses) {
            PresenceModel pm = mFriend.getPresenceModelForUriOrTel(noa.getValue());
            if (pm != null && pm.getBasicStatus().equals(PresenceBasicStatus.Open)) {
                return true;
            }
        }
        return false;
    }

    public String getContactFromPresenceModelForUriOrTel(String uri) {
        if (mFriend != null && mFriend.getPresenceModelForUriOrTel(uri) != null) {
            return mFriend.getPresenceModelForUriOrTel(uri).getContact();
        }
        return null;
    }

    public PresenceBasicStatus getBasicStatusFromPresenceModelForUriOrTel(String uri) {
        if (mFriend != null && mFriend.getPresenceModelForUriOrTel(uri) != null) {
            return mFriend.getPresenceModelForUriOrTel(uri).getBasicStatus();
        }
        return PresenceBasicStatus.Closed;
    }

    public boolean hasPresenceModelForUriOrTelCapability(String uri, FriendCapability capability) {
        if (mFriend != null && mFriend.getPresenceModelForUriOrTel(uri) != null) {
            return mFriend.getPresenceModelForUriOrTel(uri).hasCapability(capability);
        }
        return false;
    }

    private void createFriend() {
        LinphoneContact contact = new LinphoneContact();
        Friend friend = LinphoneManager.getLc().createFriend();
        // Disable subscribes for now
        friend.enableSubscribes(false);
        friend.setIncSubscribePolicy(SubscribePolicy.SPDeny);
        contact.mFriend = friend;
        friend.setUserData(contact);
    }

    /*
    Contact related
     */

    protected void setAndroidId(String id) {
        super.setAndroidId(id);
        setThumbnailUri(getContactThumbnailPictureUri());
        setPhotoUri(getContactPictureUri());
    }

    public void syncValuesFromFriend() {
        if (isFriend()) {
            mAddresses = new ArrayList<>();
            mFullName = mFriend.getName();
            mLastName = mFriend.getVcard().getFamilyName();
            mFirstName = mFriend.getVcard().getGivenName();
            mThumbnailUri = null;
            mPhotoUri = null;
            mHasSipAddress = mFriend.getAddress() != null;
            mOrganization = mFriend.getVcard().getOrganization();

            Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
            if (lc != null && lc.vcardSupported()) {
                for (Address addr : mFriend.getAddresses()) {
                    if (addr != null) {
                        addNumberOrAddress(
                                new LinphoneNumberOrAddress(addr.asStringUriOnly(), true));
                    }
                }
                for (String tel : mFriend.getPhoneNumbers()) {
                    if (tel != null) {
                        addNumberOrAddress(new LinphoneNumberOrAddress(tel, false));
                    }
                }
            } else {
                Address addr = mFriend.getAddress();
                addNumberOrAddress(new LinphoneNumberOrAddress(addr.asStringUriOnly(), true));
            }
        }
    }

    public void syncValuesFromAndroidContact(Context context) {
        Cursor c =
                context.getContentResolver()
                        .query(
                                ContactsContract.Data.CONTENT_URI,
                                AsyncContactsLoader.PROJECTION,
                                ContactsContract.Data.IN_VISIBLE_GROUP
                                        + " == 1 AND "
                                        + ContactsContract.Data.CONTACT_ID
                                        + " == "
                                        + mAndroidId,
                                null,
                                null);
        if (c != null) {
            mAddresses = new ArrayList<>();
            while (c.moveToNext()) {
                syncValuesFromAndroidCusor(c);
            }
            c.close();
        }
    }

    public void syncValuesFromAndroidCusor(Cursor c) {
        String displayName =
                c.getString(c.getColumnIndex(ContactsContract.Data.DISPLAY_NAME_PRIMARY));
        String mime = c.getString(c.getColumnIndex(ContactsContract.Data.MIMETYPE));
        String data1 = c.getString(c.getColumnIndex("data1"));
        String data2 = c.getString(c.getColumnIndex("data2"));
        String data3 = c.getString(c.getColumnIndex("data3"));
        String data4 = c.getString(c.getColumnIndex("data4"));
        String lookupKey = c.getString(c.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY));

        setAndroidLookupKey(lookupKey);
        setFullName(displayName);

        if (ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE.equals(mime)) {
            addNumberOrAddress(new LinphoneNumberOrAddress(data1, data4));
        } else if (ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE.equals(mime)
                || LinphoneManager.getInstance()
                        .getContext()
                        .getString(R.string.linphone_address_mime_type)
                        .equals(mime)) {
            addNumberOrAddress(new LinphoneNumberOrAddress(data1, true));
        } else if (ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE.equals(mime)) {
            setOrganization(data1, false);
        } else if (ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE.equals(mime)) {
            setFirstNameAndLastName(data2, data3, false);
        }
    }

    public void save() {
        saveChangesCommited();
        syncValuesFromAndroidContact(LinphoneActivity.instance());
        createOrUpdateFriend();
    }

    public void delete() {
        deleteAndroidContact();

        if (isFriend()) {
            deleteFriend();
        }
    }

    public boolean hasFriendCapability(FriendCapability capability) {
        if (!isFriend()) return false;

        return getFriend().hasCapability(capability);
    }
}
