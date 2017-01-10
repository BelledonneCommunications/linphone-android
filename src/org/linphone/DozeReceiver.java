package org.linphone;

import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.mediastream.Log;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;

/*
 * Purpose of this receiver is to disable keep alives when device is on idle
 * */
public class DozeReceiver extends android.content.BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        PowerManager pm;
        if (!LinphoneService.isReady()) return;

        boolean isDebugEnabled = LinphonePreferences.instance().isDebugEnabled();
        LinphoneCoreFactory.instance().enableLogCollection(isDebugEnabled);
        LinphoneCoreFactory.instance().setDebugMode(isDebugEnabled, context.getString(R.string.app_name));
        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc == null) return;

        pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            boolean dozeM = pm.isDeviceIdleMode();
            Log.i("[DozeReceiver] Idle Mode: " + dozeM);
            LinphoneManager.getInstance().setDozeModeEnabled(dozeM);
            LinphoneManager.getInstance().updateNetworkReachability();
        }
    }
}
