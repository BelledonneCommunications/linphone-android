package org.linphone.settings;
/*
ContactSettingsFragment.java
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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import org.linphone.R;
import org.linphone.settings.widget.SettingListenerBase;
import org.linphone.settings.widget.SwitchSetting;

public class ContactSettingsFragment extends SettingsFragment {
    private View mContactView;
    private SwitchSetting mContactPresenceNativeContact,
            mFriendListSubscribe,
            mDisplayDetailContact;
    private LinphonePreferences mPrefs;

    @Override
    public View onCreateView(
            LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        mContactView = inflater.inflate(R.layout.settings_contact, container, false);

        loadSettings();

        return mContactView;
    }

    @Override
    public void onResume() {
        super.onResume();

        mPrefs = LinphonePreferences.instance();

        updateValues();
    }

    private void loadSettings() {
        mFriendListSubscribe = mContactView.findViewById(R.id.pref_friendlist_subscribe);
        mContactPresenceNativeContact =
                mContactView.findViewById(R.id.pref_contact_presence_native_contact);
        mDisplayDetailContact = mContactView.findViewById(R.id.pref_contact_organization);
    }

    private void updateValues() {
        setListeners();

        mFriendListSubscribe.setChecked(mPrefs.isFriendlistsubscriptionEnabled());
        mContactPresenceNativeContact.setChecked(
                mPrefs.isPresenceStorageInNativeAndroidContactEnabled());

        if (getResources().getBoolean(R.bool.display_contact_organization)) {
            mDisplayDetailContact.setChecked(mPrefs.isDisplayContactOrganization());
        } else {
            mDisplayDetailContact.setVisibility(View.INVISIBLE);
        }
    }

    private void setListeners() {
        mContactPresenceNativeContact.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        mPrefs.enabledPresenceStorageInNativeAndroidContact(newValue);
                    }
                });

        mFriendListSubscribe.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        mPrefs.enabledFriendlistSubscription(newValue);
                        // Synchronization of the buttons between them, possibility to click on :
                        // "presence information"... only if "is friends subscript on enabled" is
                        // active
                        mContactPresenceNativeContact.setEnabled(
                                mPrefs.isFriendlistsubscriptionEnabled());

                        if (!newValue) {
                            mContactPresenceNativeContact.setChecked(false);
                        }
                    }
                });
        mDisplayDetailContact.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        mPrefs.enabledDisplayContactOrganization(newValue);
                    }
                });
    }
}
