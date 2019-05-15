package org.linphone.receivers;

/*
BootReceiver.java
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
import org.linphone.LinphoneService;
import org.linphone.compatibility.Compatibility;
import org.linphone.settings.LinphonePreferences;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equalsIgnoreCase(Intent.ACTION_SHUTDOWN)) {
            android.util.Log.d(
                    "Linphone",
                    "[Boot Receiver] Device is shutting down, destroying Core to unregister");
            context.stopService(
                    new Intent(Intent.ACTION_MAIN).setClass(context, LinphoneService.class));
        } else {
            LinphonePreferences.instance().setContext(context);
            boolean autostart = LinphonePreferences.instance().isAutoStartEnabled();
            android.util.Log.i(
                    "Linphone", "[Boot Receiver] Device is starting, auto_start is " + autostart);
            if (autostart && !LinphoneService.isReady()) {
                Intent serviceIntent = new Intent(Intent.ACTION_MAIN);
                serviceIntent.setClass(context, LinphoneService.class);
                serviceIntent.putExtra("ForceStartForeground", true);
                Compatibility.startService(context, serviceIntent);
            }
        }
    }
}
