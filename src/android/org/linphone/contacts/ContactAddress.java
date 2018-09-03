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

import org.linphone.core.Address;
import org.linphone.core.Factory;
import org.linphone.core.SearchResult;

import java.io.Serializable;

public class ContactAddress implements Serializable {
    private LinphoneContact contact;
    private SearchResult result;
    private String address;
    private String phoneNumber;
    private boolean isLinphoneContact;
    private boolean isSelect = false;
    private boolean isAdmin = false;
    private transient View view;

    public boolean isAdmin() {
        return isAdmin;
    }

    public void setAdmin(boolean admin) {
        isAdmin = admin;
    }

    public boolean isSelect() {
        return isSelect;
    }

    public void setView(View v) {
        view = v;
    }

    public View getView() {
        return view;
    }

    public LinphoneContact getContact() {
        return contact;
    }

    public SearchResult getResult() {
        return result;
    }

    public void setResult(SearchResult result) {
        this.result = result;
    }

    public String getAddressAsDisplayableString() {
        Address addr = getAddress();
        if (addr != null && addr.getUsername() != null) return addr.asStringUriOnly();
        return address;
    }

    public Address getAddress() {
        String presence = contact.getPresenceModelForUriOrTel((phoneNumber != null && !phoneNumber.isEmpty()) ? phoneNumber : address);
        Address addr = Factory.instance().createAddress(presence != null ? presence : address);
        // Remove the user=phone URI param if existing, it will break everything otherwise
        if (addr.hasUriParam("user")) {
            addr.removeUriParam("user");
        }
        return addr;
    }

    public String getDisplayName() {
        if (address != null) {
            Address addr = Factory.instance().createAddress(address);
            if (addr != null) {
                return addr.getDisplayName();
            }
        }
        return null;
    }

    public String getUsername() {
        if (address != null) {
            Address addr = Factory.instance().createAddress(address);
            if (addr != null) {
                return addr.getUsername();
            }
        }
        return null;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setSelect(boolean select) {
        isSelect = select;
    }

    public boolean isLinphoneContact() {
        return isLinphoneContact;
    }

    private void init(LinphoneContact c, String a, String pn, boolean isLC) {
        contact = c;
        address = a;
        phoneNumber = pn;
        isLinphoneContact = isLC;
    }

    public ContactAddress(LinphoneContact c, String a, String pn, boolean isLC) {
        init(c, a, pn, isLC);
    }

    public ContactAddress(LinphoneContact c, String a, String pn, boolean isLC, boolean isAdmin) {
        init(c, a, pn, isLC);
        this.isAdmin = isAdmin;
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) return false;
        if (other == this) return true;
        if (!(other instanceof ContactAddress)) return false;
        if (((ContactAddress) other).getAddressAsDisplayableString() == this.getAddressAsDisplayableString())
            return true;
        return false;
    }
}
