package org.linphone.contacts;

/*
ContactsActivity.java
Copyright (C) 2019 Belledonne Communications, Grenoble, France

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

import android.Manifest;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import org.linphone.main.MainActivity;

public class ContactsActivity extends MainActivity {
    private LinphoneContact mDisplayedContact;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPermissionsToHave =
                new String[] {
                    Manifest.permission.WRITE_CONTACTS, Manifest.permission.READ_CONTACTS
                };

        ContactsFragment fragment = new ContactsFragment();
        changeFragment(fragment, "Contacts", false);
        if (isTablet()) {
            fragment.displayFirstContact();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        mContactsSelected.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(
                "DisplayedContact", mDisplayedContact != null ? mDisplayedContact : null);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        LinphoneContact contact = (LinphoneContact) savedInstanceState.get("DisplayedContact");
        if (contact != null) {
            showContactDetails(contact);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!isTablet() && keyCode == KeyEvent.KEYCODE_BACK) {
            if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                getSupportFragmentManager().popBackStackImmediate();
                mDisplayedContact = null;
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void goBack() {
        if (!isTablet()) {
            if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                getSupportFragmentManager().popBackStackImmediate();
                mDisplayedContact = null;
                return;
            }
        }
        super.goBack();
    }

    public void showContactDetails(LinphoneContact contact) {
        Bundle extras = new Bundle();
        if (contact != null) {
            extras.putSerializable("Contact", contact);
        }
        ContactDetailsFragment fragment = new ContactDetailsFragment();
        fragment.setArguments(extras);
        changeFragment(fragment, "Contact detail", true);
        mDisplayedContact = contact;
    }
}
