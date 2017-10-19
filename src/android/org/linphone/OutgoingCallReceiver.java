package org.linphone;

/*
OutgoingCallReceiver.java
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;


public class OutgoingCallReceiver extends BroadcastReceiver {
    private final static String TAG = "CallHandler";
    private final String ACTION_CALL_LINPHONE  = "org.linphone.intent.action.CallLaunched";

    private LinphonePreferences mPrefs;

    @Override
    public void onReceive(Context context, Intent intent) {
        mPrefs = LinphonePreferences.instance();
        Log.e(TAG, "===>>>> Linphone OutgoingCallReceiver ");
        if (intent.getAction().equals(Intent.ACTION_NEW_OUTGOING_CALL)) {
            Log.e(TAG, "===>>>> Linphone OutgoingCallReceiver : ACTION_NEW_OUTGOING_CALL");
            String number = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
            if(mPrefs.getConfig() != null && mPrefs.getNativeDialerCall()){
                abortBroadcast();
                setResultData(null);
                Intent newIntent = new Intent(ACTION_CALL_LINPHONE);
                newIntent.setFlags(FLAG_ACTIVITY_NEW_TASK);
                newIntent.putExtra("StartCall", true);
                newIntent.putExtra("NumberToCall", number);
                context.startActivity(newIntent);
            }
        }
    }
}