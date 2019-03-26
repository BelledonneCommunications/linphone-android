package org.linphone.settings;

public interface SettingListener {
    void onSettingClicked();

    void onValueChanged(Object newValue);
}
