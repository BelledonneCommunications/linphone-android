package org.linphone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import org.linphone.mediastream.Log;

public class HookReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if(isOrderedBroadcast())
            abortBroadcast();
        Bundle extras = intent.getExtras();
        boolean b = extras.getBoolean("hookoff");
        if(b){
            //handset on
            Log.i(" ======>>>>>> HookReceiver - handset ON");
            LinphoneManager.getLc().enableSpeaker(false);
            if(!LinphoneManager.getInstance().isHansetModeOn())
                LinphoneManager.getInstance().setHandsetMode(true);


        }else{
            //handset off
            Log.i(" ======>>>>>> HookReceiver - handset OFF");
            LinphoneManager.getLc().enableSpeaker(true);
            LinphoneManager.getInstance().setHandsetMode(false);
        }
    }
}
