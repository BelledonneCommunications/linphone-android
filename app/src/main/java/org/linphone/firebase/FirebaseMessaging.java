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
package org.linphone.firebase;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import org.linphone.LinphoneContext;
import org.linphone.LinphoneManager;
import org.linphone.core.Core;
import org.linphone.core.tools.Log;
import org.linphone.settings.LinphonePreferences;
import org.linphone.utils.LinphoneUtils;

public class FirebaseMessaging extends FirebaseMessagingService {
    private Runnable mPushReceivedRunnable =
            new Runnable() {
                @Override
                public void run() {
                    if (!LinphoneContext.isReady()) {
                        android.util.Log.i(
                                "FirebaseMessaging", "[Push Notification] Starting context");
                        new LinphoneContext(getApplicationContext());
                        LinphoneContext.instance().start(false);
                    } else {
                        Log.i("[Push Notification] Notifying Core");
                        if (LinphoneManager.getInstance() != null) {
                            Core core = LinphoneManager.getCore();
                            if (core != null) {
                                core.ensureRegistered();
                            }
                        }
                    }
                }
            };

    public FirebaseMessaging() {}

    @Override
    public void onNewToken(final String token) {
        android.util.Log.i("FirebaseIdService", "[Push Notification] Refreshed token: " + token);

        LinphoneUtils.dispatchOnUIThread(
                new Runnable() {
                    @Override
                    public void run() {
                        LinphonePreferences.instance().setPushNotificationRegistrationID(token);
                    }
                });
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        android.util.Log.i("FirebaseMessaging", "[Push Notification] Received");
        LinphoneUtils.dispatchOnUIThread(mPushReceivedRunnable);
    }
}
