package org.linphone;


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