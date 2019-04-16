package org.linphone.utils;

/*
DeviceUtils.java
Copyright (C) 2018  Belledonne Communications, Grenoble, France

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

import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import java.util.List;
import org.linphone.R;
import org.linphone.compatibility.Compatibility;
import org.linphone.core.tools.Log;
import org.linphone.settings.LinphonePreferences;

public class DeviceUtils {
    private static final Intent[] POWERMANAGER_INTENTS = {
        new Intent()
                .setComponent(
                        new ComponentName(
                                "com.miui.securitycenter",
                                "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        new Intent()
                .setComponent(
                        new ComponentName(
                                "com.letv.android.letvsafe",
                                "com.letv.android.letvsafe.AutobootManageActivity")),
        new Intent()
                .setComponent(
                        new ComponentName(
                                "com.huawei.systemmanager",
                                "com.huawei.systemmanager.optimize.process.ProtectActivity")),
        new Intent()
                .setComponent(
                        new ComponentName(
                                "com.huawei.systemmanager",
                                "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")),
        new Intent()
                .setComponent(
                        new ComponentName(
                                "com.coloros.safecenter",
                                "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
        new Intent()
                .setComponent(
                        new ComponentName(
                                "com.coloros.safecenter",
                                "com.coloros.safecenter.startupapp.StartupAppListActivity")),
        new Intent()
                .setComponent(
                        new ComponentName(
                                "com.oppo.safe",
                                "com.oppo.safe.permission.startup.StartupAppListActivity")),
        new Intent()
                .setComponent(
                        new ComponentName(
                                "com.iqoo.secure",
                                "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
        new Intent()
                .setComponent(
                        new ComponentName(
                                "com.iqoo.secure",
                                "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")),
        new Intent()
                .setComponent(
                        new ComponentName(
                                "com.vivo.permissionmanager",
                                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
        new Intent()
                .setComponent(
                        new ComponentName(
                                "com.samsung.android.lool",
                                "com.samsung.android.sm.ui.battery.BatteryActivity")),
        new Intent()
                .setComponent(
                        new ComponentName(
                                "com.htc.pitroad",
                                "com.htc.pitroad.landingpage.activity.LandingPageActivity")),
        new Intent()
                .setComponent(
                        new ComponentName(
                                "com.asus.mobilemanager", "com.asus.mobilemanager.MainActivity"))
    };

    public static Intent getDevicePowerManagerIntent(Context context) {
        for (Intent intent : POWERMANAGER_INTENTS) {
            if (DeviceUtils.isIntentCallable(context, intent)) {
                return intent;
            }
        }
        return null;
    }

    public static boolean hasDevicePowerManager(Context context) {
        return getDevicePowerManagerIntent(context) != null;
    }

    public static boolean isAppUserRestricted(Context context) {
        return Compatibility.isAppUserRestricted(context);
    }

    public static int getAppStandbyBucket(Context context) {
        return Compatibility.getAppStandbyBucket(context);
    }

    public static void displayDialogIfDeviceHasPowerManagerThatCouldPreventPushNotifications(
            final Context context) {
        for (final Intent intent : POWERMANAGER_INTENTS) {
            if (DeviceUtils.isIntentCallable(context, intent)) {
                Log.w(
                        "[Hacks] "
                                + android.os.Build.MANUFACTURER
                                + " device with power saver detected !");
                if (!LinphonePreferences.instance().hasPowerSaverDialogBeenPrompted()) {
                    Log.w("[Hacks] Asking power saver for whitelist !");

                    final Dialog dialog = new Dialog(context);
                    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                    Drawable d =
                            new ColorDrawable(
                                    ContextCompat.getColor(context, R.color.dark_grey_color));
                    d.setAlpha(200);
                    dialog.setContentView(R.layout.dialog);
                    dialog.getWindow()
                            .setLayout(
                                    WindowManager.LayoutParams.MATCH_PARENT,
                                    WindowManager.LayoutParams.MATCH_PARENT);
                    dialog.getWindow().setBackgroundDrawable(d);

                    TextView customText = dialog.findViewById(R.id.dialog_message);
                    customText.setText(R.string.device_power_saver_dialog_message);

                    TextView customTitle = dialog.findViewById(R.id.dialog_title);
                    customTitle.setText(R.string.device_power_saver_dialog_title);

                    dialog.findViewById(R.id.dialog_do_not_ask_again_layout)
                            .setVisibility(View.VISIBLE);
                    final CheckBox doNotAskAgain = dialog.findViewById(R.id.doNotAskAgain);
                    dialog.findViewById(R.id.doNotAskAgainLabel)
                            .setOnClickListener(
                                    new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            doNotAskAgain.setChecked(!doNotAskAgain.isChecked());
                                        }
                                    });

                    Button accept = dialog.findViewById(R.id.dialog_ok_button);
                    accept.setVisibility(View.VISIBLE);
                    accept.setText(R.string.device_power_saver_dialog_button_go_to_settings);
                    accept.setOnClickListener(
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    Log.w(
                                            "[Hacks] Power saver detected, user is going to settings :)");
                                    if (doNotAskAgain.isChecked()) {
                                        LinphonePreferences.instance()
                                                .powerSaverDialogPrompted(true);
                                    }

                                    context.startActivity(intent);
                                    dialog.dismiss();
                                }
                            });

                    Button cancel = dialog.findViewById(R.id.dialog_cancel_button);
                    cancel.setText(R.string.device_power_saver_dialog_button_later);
                    cancel.setOnClickListener(
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    Log.w(
                                            "[Hacks] Power saver detected, user didn't go to settings :(");
                                    if (doNotAskAgain.isChecked()) {
                                        LinphonePreferences.instance()
                                                .powerSaverDialogPrompted(true);
                                    }
                                    dialog.dismiss();
                                }
                            });

                    Button delete = dialog.findViewById(R.id.dialog_delete_button);
                    delete.setVisibility(View.GONE);

                    dialog.show();
                }
            }
        }
    }

    private static boolean isIntentCallable(Context context, Intent intent) {
        List<ResolveInfo> list =
                context.getPackageManager()
                        .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return list.size() > 0;
    }
}
