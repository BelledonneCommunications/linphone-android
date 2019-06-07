package org.linphone.settings;

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
    private SwitchSetting mContactSynchronization;
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
        mContactSynchronization = mContactView.findViewById(R.id.pref_contact_synchronization);
    }

    private void updateValues() {
        mContactSynchronization.setChecked(mPrefs.isPresenceStorageInNativeAndroidContactEnabled());

        setListeners();
    }

    private void setListeners() {
        mContactSynchronization.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        mPrefs.enablePresenceStorageInNativeAndroidContact(newValue);
                    }
                });
    }
}
