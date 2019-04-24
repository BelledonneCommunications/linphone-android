package org.linphone.receivers;

/*
HookReceiver.java
Copyright (C) 2017 Belledonne Communications, Grenoble, France

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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import org.linphone.LinphoneManager;
import org.linphone.core.tools.Log;

public class HookReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (isOrderedBroadcast()) abortBroadcast();
        Bundle extras = intent.getExtras();
        boolean handsetOn = extras.getBoolean("hookoff");
        Log.i("[Hook Receiver] Handset " + handsetOn);

        LinphoneManager.getCallManager().setHandsetMode(handsetOn);
        if (handsetOn) {
            LinphoneManager.getAudioManager().routeAudioToEarPiece();
        } else {
            LinphoneManager.getAudioManager().routeAudioToSpeaker();
        }
    }
}
