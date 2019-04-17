package org.linphone.compatibility;

/*
ApiTwentyThreePlus.java
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

import static android.app.NotificationManager.INTERRUPTION_FILTER_ALARMS;

import android.annotation.TargetApi;
import android.app.NotificationManager;
import android.content.Context;
import android.os.PowerManager;
import android.widget.TextView;

@TargetApi(23)
class ApiTwentyThreePlus {
    public static void setTextAppearance(TextView textview, int style) {
        textview.setTextAppearance(style);
    }

    public static boolean isAppIdleMode(Context context) {
        return ((PowerManager) context.getSystemService(Context.POWER_SERVICE)).isDeviceIdleMode();
    }

    public static boolean isDoNotDisturbModeEnabledForCalls(Context context) {
        int filter =
                ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE))
                        .getCurrentInterruptionFilter();
        return filter == INTERRUPTION_FILTER_ALARMS;
    }
}
