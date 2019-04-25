package org.linphone.compatibility;
/*
Compatibility.java
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

import android.app.Activity;
import android.app.FragmentTransaction;
import android.app.Notification;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.provider.Settings;
import android.widget.TextView;
import org.linphone.mediastream.Version;
import org.linphone.notifications.Notifiable;

public class Compatibility {
    public static final String CHAT_NOTIFICATIONS_GROUP = "CHAT_NOTIF_GROUP";
    public static final String KEY_TEXT_REPLY = "key_text_reply";
    public static final String INTENT_NOTIF_ID = "NOTIFICATION_ID";
    public static final String INTENT_REPLY_NOTIF_ACTION = "org.linphone.REPLY_ACTION";
    public static final String INTENT_HANGUP_CALL_NOTIF_ACTION = "org.linphone.HANGUP_CALL_ACTION";
    public static final String INTENT_ANSWER_CALL_NOTIF_ACTION = "org.linphone.ANSWER_CALL_ACTION";
    public static final String INTENT_LOCAL_IDENTITY = "LOCAL_IDENTITY";
    public static final String INTENT_MARK_AS_READ_ACTION = "org.linphone.MARK_AS_READ_ACTION";

    public static String getDeviceName(Context context) {
        if (Version.sdkAboveOrEqual(25)) {
            return ApiTwentySixPlus.getDeviceName(context);
        }

        String name = BluetoothAdapter.getDefaultAdapter().getName();
        if (name == null) {
            name = Settings.Secure.getString(context.getContentResolver(), "bluetooth_name");
        }
        if (name == null) {
            name = Build.MANUFACTURER + " " + Build.MODEL;
        }
        return name;
    }

    public static void createNotificationChannels(Context context) {
        if (Version.sdkAboveOrEqual(Version.API26_O_80)) {
            ApiTwentySixPlus.createServiceChannel(context);
            ApiTwentySixPlus.createMessageChannel(context);
        }
    }

    public static Notification createSimpleNotification(
            Context context, String title, String text, PendingIntent intent) {
        if (Version.sdkAboveOrEqual(Version.API26_O_80)) {
            return ApiTwentySixPlus.createSimpleNotification(context, title, text, intent);
        }
        return ApiTwentyOnePlus.createSimpleNotification(context, title, text, intent);
    }

    public static Notification createMissedCallNotification(
            Context context, String title, String text, PendingIntent intent) {
        if (Version.sdkAboveOrEqual(Version.API26_O_80)) {
            return ApiTwentySixPlus.createMissedCallNotification(context, title, text, intent);
        }
        return ApiTwentyOnePlus.createMissedCallNotification(context, title, text, intent);
    }

    public static Notification createMessageNotification(
            Context context,
            Notifiable notif,
            String msgSender,
            String msg,
            Bitmap contactIcon,
            PendingIntent intent) {
        if (Version.sdkAboveOrEqual(28)) {
            return ApiTwentyEightPlus.createMessageNotification(
                    context, notif, contactIcon, intent);
        } else if (Version.sdkAboveOrEqual(Version.API26_O_80)) {
            return ApiTwentySixPlus.createMessageNotification(context, notif, contactIcon, intent);
        } else if (Version.sdkAboveOrEqual(Version.API24_NOUGAT_70)) {
            return ApiTwentyFourPlus.createMessageNotification(context, notif, contactIcon, intent);
        }
        return ApiTwentyOnePlus.createMessageNotification(
                context, notif.getMessages().size(), msgSender, msg, contactIcon, intent);
    }

    public static Notification createRepliedNotification(Context context, String reply) {
        if (Version.sdkAboveOrEqual(Version.API26_O_80)) {
            return ApiTwentySixPlus.createRepliedNotification(context, reply);
        } else if (Version.sdkAboveOrEqual(Version.API24_NOUGAT_70)) {
            return ApiTwentyFourPlus.createRepliedNotification(context, reply);
        }
        return null;
    }

    public static Notification createInCallNotification(
            Context context,
            int callId,
            boolean showAnswerAction,
            String msg,
            int iconID,
            Bitmap contactIcon,
            String contactName,
            PendingIntent intent) {
        if (Version.sdkAboveOrEqual(Version.API26_O_80)) {
            return ApiTwentySixPlus.createInCallNotification(
                    context,
                    callId,
                    showAnswerAction,
                    msg,
                    iconID,
                    contactIcon,
                    contactName,
                    intent);
        } else if (Version.sdkAboveOrEqual(Version.API24_NOUGAT_70)) {
            return ApiTwentyFourPlus.createInCallNotification(
                    context,
                    callId,
                    showAnswerAction,
                    msg,
                    iconID,
                    contactIcon,
                    contactName,
                    intent);
        }
        return ApiTwentyOnePlus.createInCallNotification(
                context, msg, iconID, contactIcon, contactName, intent);
    }

    public static Notification createNotification(
            Context context,
            String title,
            String message,
            int icon,
            int iconLevel,
            Bitmap largeIcon,
            PendingIntent intent,
            int priority) {
        if (Version.sdkAboveOrEqual(Version.API26_O_80)) {
            return ApiTwentySixPlus.createNotification(
                    context, title, message, icon, iconLevel, largeIcon, intent, priority);
        }
        return ApiTwentyOnePlus.createNotification(
                context, title, message, icon, iconLevel, largeIcon, intent, priority);
    }

    public static boolean canDrawOverlays(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(context);
        }
        return true;
    }

    public static void setTextAppearance(TextView textview, Context context, int style) {
        if (Version.sdkAboveOrEqual(Version.API23_MARSHMALLOW_60)) {
            ApiTwentyThreePlus.setTextAppearance(textview, style);
        } else {
            textview.setTextAppearance(context, style);
        }
    }

    public static void startService(Context context, Intent intent) {
        if (Version.sdkAboveOrEqual(Version.API26_O_80)) {
            ApiTwentySixPlus.startService(context, intent);
        } else {
            context.startService(intent);
        }
    }

    public static void setFragmentTransactionReorderingAllowed(
            FragmentTransaction transaction, boolean allowed) {
        if (Version.sdkAboveOrEqual(Version.API26_O_80)) {
            ApiTwentySixPlus.setFragmentTransactionReorderingAllowed(transaction, allowed);
        }
    }

    public static void closeContentProviderClient(ContentProviderClient client) {
        if (Version.sdkAboveOrEqual(Version.API24_NOUGAT_70)) {
            ApiTwentyFourPlus.closeContentProviderClient(client);
        } else {
            ApiTwentyOnePlus.closeContentProviderClient(client);
        }
    }

    public static boolean isAppUserRestricted(Context context) {
        if (Version.sdkAboveOrEqual(Version.API28_PIE_90)) {
            return ApiTwentyEightPlus.isAppUserRestricted(context);
        }
        return false;
    }

    public static boolean isAppIdleMode(Context context) {
        if (Version.sdkAboveOrEqual(Version.API23_MARSHMALLOW_60)) {
            return ApiTwentyThreePlus.isAppIdleMode(context);
        }
        return false;
    }

    public static int getAppStandbyBucket(Context context) {
        if (Version.sdkAboveOrEqual(Version.API28_PIE_90)) {
            return ApiTwentyEightPlus.getAppStandbyBucket(context);
        }
        return 0;
    }

    public static String getAppStandbyBucketNameFromValue(int bucket) {
        if (Version.sdkAboveOrEqual(Version.API28_PIE_90)) {
            return ApiTwentyEightPlus.getAppStandbyBucketNameFromValue(bucket);
        }
        return null;
    }

    public static void setShowWhenLocked(Activity activity, boolean enable) {
        if (Version.sdkStrictlyBelow(Version.API27_OREO_81)) {
            ApiTwentyOnePlus.setShowWhenLocked(activity, enable);
        }
    }

    public static void setTurnScreenOn(Activity activity, boolean enable) {
        if (Version.sdkStrictlyBelow(Version.API27_OREO_81)) {
            ApiTwentyOnePlus.setTurnScreenOn(activity, enable);
        }
    }
}
