/*
 * Copyright (c) 2010-2019 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.activities;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import org.linphone.LinphoneManager;
import org.linphone.LinphoneService;
import org.linphone.R;
import org.linphone.assistant.MenuAssistantActivity;
import org.linphone.chat.ChatActivity;
import org.linphone.contacts.ContactsActivity;
import org.linphone.history.HistoryActivity;
import org.linphone.settings.LinphonePreferences;
import org.linphone.utils.ServiceWaitThread;
import org.linphone.utils.ServiceWaitThreadListener;

/** Creates LinphoneService and wait until Core is ready to start main Activity */
public class LinphoneLauncherActivity extends Activity implements ServiceWaitThreadListener {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getResources().getBoolean(R.bool.orientation_portrait_only)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        if (!getResources().getBoolean(R.bool.use_full_screen_image_splashscreen)) {
            setContentView(R.layout.launch_screen);
        } // Otherwise use drawable/launch_screen layer list up until first activity starts
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (LinphoneService.isReady()) {
            onServiceReady();
        } else {
            startService(
                    new Intent().setClass(LinphoneLauncherActivity.this, LinphoneService.class));
            new ServiceWaitThread(this).start();
        }
    }

    @Override
    public void onServiceReady() {
        final Class<? extends Activity> classToStart;

        boolean useFirstLoginActivity =
                getResources().getBoolean(R.bool.display_account_assistant_at_first_start);
        if (useFirstLoginActivity && LinphonePreferences.instance().isFirstLaunch()) {
            classToStart = MenuAssistantActivity.class;
        } else {
            if (getIntent().getExtras() != null) {
                String activity = getIntent().getExtras().getString("Activity", null);
                if (ChatActivity.NAME.equals(activity)) {
                    classToStart = ChatActivity.class;
                } else if (HistoryActivity.NAME.equals(activity)) {
                    classToStart = HistoryActivity.class;
                } else if (ContactsActivity.NAME.equals(activity)) {
                    classToStart = ContactsActivity.class;
                } else {
                    classToStart = DialerActivity.class;
                }
            } else {
                classToStart = DialerActivity.class;
            }
        }

        if (getResources().getBoolean(R.bool.check_for_update_when_app_starts)) {
            LinphoneManager.getInstance().checkForUpdate();
        }

        Intent intent = new Intent();
        intent.setClass(LinphoneLauncherActivity.this, classToStart);
        if (getIntent() != null && getIntent().getExtras() != null) {
            intent.putExtras(getIntent().getExtras());
        }
        intent.setAction(getIntent().getAction());
        intent.setType(getIntent().getType());
        intent.setData(getIntent().getData());
        startActivity(intent);

        LinphoneManager.getInstance().changeStatusToOnline();
    }
}
