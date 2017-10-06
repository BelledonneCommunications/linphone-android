package org.linphone.firebase;

/*
FirebaseMessaging.java
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

import android.content.Intent;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.linphone.LinphoneManager;
import org.linphone.LinphoneService;
import org.linphone.UIThreadDispatcher;
import static android.content.Intent.ACTION_MAIN;


public class FirebaseMessaging extends FirebaseMessagingService {
    public FirebaseMessaging() {
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        android.util.Log.i("FirebaseMessaging","[Push Notification] Received");

        if (!LinphoneService.isReady()) {
            startService(new Intent(ACTION_MAIN).setClass(this, LinphoneService.class));
        } else if (LinphoneManager.isInstanciated() && LinphoneManager.getLc().getCallsNb() == 0) {
            UIThreadDispatcher.dispatch(new Runnable(){
                @Override
                public void run() {
                    if (LinphoneManager.isInstanciated() && LinphoneManager.getLc().getCallsNb() == 0){
                        LinphoneManager.getLc().setNetworkReachable(false);
                        LinphoneManager.getLc().setNetworkReachable(true);
                    }
                }
            });
        }
    }

}
