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
package org.linphone.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import org.linphone.LinphoneManager;
import org.linphone.core.tools.Log;

public class HeadsetReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (isInitialStickyBroadcast()) {
            Log.i("[Headset] Received broadcast from sticky cache, ignoring...");
            return;
        }

        String action = intent.getAction();
        if (action.equals(AudioManager.ACTION_HEADSET_PLUG)) {
            // This happens when the user plugs a Jack headset to the device for example
            // https://developer.android.com/reference/android/media/AudioManager.html#ACTION_HEADSET_PLUG
            int state = intent.getIntExtra("state", 0);
            String name = intent.getStringExtra("name");
            int hasMicrophone = intent.getIntExtra("microphone", 0);

            if (state == 0) {
                Log.i("[Headset] Headset disconnected:" + name);
            } else if (state == 1) {
                Log.i("[Headset] Headset connected:" + name);
                if (hasMicrophone == 1) {
                    Log.i("[Headset] Headset " + name + " has a microphone");
                }
            } else {
                Log.w("[Headset] Unknown headset plugged state: " + state);
            }

            LinphoneManager.getAudioManager().routeAudioToEarPiece();
            LinphoneManager.getCallManager().refreshInCallActions();
        } else if (action.equals(AudioManager.ACTION_AUDIO_BECOMING_NOISY)) {
            // This happens when the user disconnect a headset, so we shouldn't play audio loudly
            Log.i("[Headset] Noisy state detected, most probably a headset has been disconnected");
            LinphoneManager.getAudioManager().routeAudioToEarPiece();
            LinphoneManager.getCallManager().refreshInCallActions();
        } else {
            Log.w("[Headset] Unknown action: " + action);
        }
    }
}
