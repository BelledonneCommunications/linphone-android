package org.linphone.contacts;

/*
ContactAddress.java
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

import android.view.View;
import java.io.Serializable;
import org.linphone.core.Address;
import org.linphone.core.Factory;
import org.linphone.core.FriendCapability;

public class ContactAddress implements Serializable {
    private LinphoneContact mContact;
    private String mAddress;
    private String mPhoneNumber;
    private boolean mIsLinphoneContact;
    private boolean mIsSelect = false;
    private boolean mIsAdmin = false;
    private transient View mView;

    public ContactAddress(LinphoneContact c, String a, String pn, boolean isLC) {
        init(c, a, pn, isLC);
    }

    public ContactAddress(LinphoneContact c, String a, String pn, boolean isLC, boolean isAdmin) {
        init(c, a, pn, isLC);
        mIsAdmin = isAdmin;
    }

    public boolean isAdmin() {
        return mIsAdmin;
    }

    public void setAdmin(boolean admin) {
        mIsAdmin = admin;
    }

    public boolean isSelect() {
        return mIsSelect;
    }

    public void setSelect(boolean select) {
        mIsSelect = select;
    }

    public View getView() {
        return mView;
    }

    public void setView(View v) {
        mView = v;
    }

    public LinphoneContact getContact() {
        return mContact;
    }

    public String getAddressAsDisplayableString() {
        Address addr = getAddress();
        if (addr != null && addr.getUsername() != null) return addr.asStringUriOnly();
        return mAddress;
    }

    public Address getAddress() {
        String presence = null;
        if (mContact != null) {
            presence =
                    mContact.getContactFromPresenceModelForUriOrTel(
                            (mPhoneNumber != null && !mPhoneNumber.isEmpty())
                                    ? mPhoneNumber
                                    : mAddress);
        }
        Address addr = Factory.instance().createAddress(presence != null ? presence : mAddress);
        // Remove the user=phone URI param if existing, it will break everything otherwise
        if (addr.hasUriParam("user")) {
            addr.removeUriParam("user");
        }
        return addr;
    }

    public String getDisplayName() {
        if (mAddress != null) {
            Address addr = Factory.instance().createAddress(mAddress);
            if (addr != null) {
                return addr.getDisplayName();
            }
        }
        return null;
    }

    public String getUsername() {
        if (mAddress != null) {
            Address addr = Factory.instance().createAddress(mAddress);
            if (addr != null) {
                return addr.getUsername();
            }
        }
        return null;
    }

    public String getPhoneNumber() {
        return mPhoneNumber;
    }

    public boolean hasCapability(FriendCapability capability) {
        return mContact != null && mContact.hasFriendCapability(capability);
    }

    private void init(LinphoneContact c, String a, String pn, boolean isLC) {
        mContact = c;
        mAddress = a;
        mPhoneNumber = pn;
        mIsLinphoneContact = isLC;
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) return false;
        if (other == this) return true;
        if (!(other instanceof ContactAddress)) return false;
        return ((ContactAddress) other)
                .getAddressAsDisplayableString()
                .equals(getAddressAsDisplayableString());
    }
}
