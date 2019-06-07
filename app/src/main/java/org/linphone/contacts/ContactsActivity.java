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
import android.app.Fragment;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import org.linphone.R;
import org.linphone.activities.MainActivity;

public class ContactsActivity extends MainActivity {
    private boolean mEditOnClick;
    private String mEditSipUri, mEditDisplayName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPermissionsToHave =
                new String[] {
                    Manifest.permission.WRITE_CONTACTS, Manifest.permission.READ_CONTACTS
                };
    }

    @Override
    protected void onStart() {
        super.onStart();

        Fragment currentFragment = getFragmentManager().findFragmentById(R.id.fragmentContainer);
        if (currentFragment == null) {
            showContactsList();

            if (getIntent() != null && getIntent().getExtras() != null) {
                Bundle extras = getIntent().getExtras();
                handleIntentExtras(extras);
            } else if (getIntent() != null && getIntent().getData() != null) {
                Uri uri = getIntent().getData();
                Bundle bundle = new Bundle();
                bundle.putString("ContactUri", uri.toString());
                handleIntentExtras(bundle);
            } else {
                if (isTablet()) {
                    showEmptyChildFragment();
                }
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        Bundle bundle = intent.getExtras();
        if (bundle == null) {
            bundle = new Bundle();
        }

        // Clean fragments stack upon return
        while (getFragmentManager().getBackStackEntryCount() > 0) {
            getFragmentManager().popBackStackImmediate();
        }

        if (intent.getData() != null) {
            bundle.putString("ContactUri", intent.getDataString());
        }

        handleIntentExtras(bundle);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mContactsSelected.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("EditSipUri", mEditSipUri);
        outState.putString("EditDisplayName", mEditDisplayName);
        outState.putBoolean("EditOnClick", mEditOnClick);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mEditOnClick = savedInstanceState.getBoolean("EditOnClick", false);
        mEditSipUri = savedInstanceState.getString("EditSipUri", null);
        mEditDisplayName = savedInstanceState.getString("EditDisplayName", null);
    }

    @Override
    public void goBack() {
        // 1 is for the empty fragment on tablets
        if (!isTablet() || getFragmentManager().getBackStackEntryCount() > 1) {
            if (popBackStack()) {
                mEditOnClick = false;
                return;
            }
        }
        super.goBack();
    }

    private void handleIntentExtras(Bundle extras) {
        if (extras == null) return;

        Fragment currentFragment = getFragmentManager().findFragmentById(R.id.fragmentContainer);
        if (currentFragment == null || !(currentFragment instanceof ContactsFragment)) {
            showContactsList();
        }

        if (extras.containsKey("ContactUri")) {
            String uri = extras.getString("ContactUri");
            Uri contactUri = Uri.parse(uri);
            String id = ContactsManager.getInstance().getAndroidContactIdFromUri(contactUri);

            LinphoneContact linphoneContact =
                    ContactsManager.getInstance().findContactFromAndroidId(id);
            if (linphoneContact != null) {
                showContactDetails(linphoneContact);
            }
        } else if (extras.containsKey("Contact")) {
            LinphoneContact contact = (LinphoneContact) extras.get("Contact");
            if (extras.containsKey("Edit")) {
                showContactEdit(contact, extras, true);
            } else {
                showContactDetails(contact);
            }
        } else if (extras.containsKey("CreateOrEdit")) {
            mEditOnClick = extras.getBoolean("CreateOrEdit");
            mEditSipUri = extras.getString("SipUri", null);
            mEditDisplayName = extras.getString("DisplayName", null);

            Toast.makeText(this, R.string.toast_choose_contact_for_edition, Toast.LENGTH_LONG)
                    .show();
        }
    }

    public void showContactDetails(LinphoneContact contact) {
        showContactDetails(contact, true);
    }

    public void showContactEdit(LinphoneContact contact) {
        showContactEdit(contact, true);
    }

    private void showContactsList() {
        ContactsFragment fragment = new ContactsFragment();
        changeFragment(fragment, "Contacts", false);
    }

    private void showContactDetails(LinphoneContact contact, boolean isChild) {
        if (mEditOnClick) {
            showContactEdit(contact, isChild);
            return;
        }

        Bundle extras = new Bundle();
        if (contact != null) {
            extras.putSerializable("Contact", contact);
        }

        ContactDetailsFragment fragment = new ContactDetailsFragment();
        fragment.setArguments(extras);
        changeFragment(fragment, "Contact detail", isChild);
    }

    private void showContactEdit(LinphoneContact contact, boolean isChild) {
        showContactEdit(contact, new Bundle(), isChild);
    }

    private void showContactEdit(LinphoneContact contact, Bundle extras, boolean isChild) {
        if (contact != null) {
            extras.putSerializable("Contact", contact);
        }
        if (mEditOnClick) {
            mEditOnClick = false;
            extras.putString("SipUri", mEditSipUri);
            extras.putString("DisplayName", mEditDisplayName);
            mEditSipUri = null;
            mEditDisplayName = null;
        }
        ContactEditorFragment fragment = new ContactEditorFragment();
        fragment.setArguments(extras);
        changeFragment(fragment, "Contact editor", isChild);
    }
}
