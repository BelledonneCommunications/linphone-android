package org.linphone.compatibility;

/*
ApiTwentyEightPlus.java
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

import static org.linphone.compatibility.Compatibility.CHAT_NOTIFICATIONS_GROUP;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Person;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import org.linphone.R;
import org.linphone.notifications.Notifiable;
import org.linphone.notifications.NotifiableMessage;

@TargetApi(28)
class ApiTwentyEightPlus {
    public static Notification createMessageNotification(
            Context context, Notifiable notif, Bitmap contactIcon, PendingIntent intent) {

        Person me = new Person.Builder().setName(notif.getMyself()).build();
        Notification.MessagingStyle style = new Notification.MessagingStyle(me);
        for (NotifiableMessage message : notif.getMessages()) {
            Icon userIcon = Icon.createWithBitmap(message.getSenderBitmap());
            Person user =
                    new Person.Builder().setName(message.getSender()).setIcon(userIcon).build();
            Notification.MessagingStyle.Message msg =
                    new Notification.MessagingStyle.Message(
                            message.getMessage(), message.getTime(), user);
            if (message.getFilePath() != null)
                msg.setData(message.getFileMime(), message.getFilePath());
            style.addMessage(msg);
        }
        if (notif.isGroup()) {
            style.setConversationTitle(notif.getGroupTitle());
        }
        style.setGroupConversation(notif.isGroup());

        return new Notification.Builder(
                        context, context.getString(R.string.notification_channel_id))
                .setSmallIcon(R.drawable.topbar_chat_notification)
                .setAutoCancel(true)
                .setContentIntent(intent)
                .setDefaults(
                        Notification.DEFAULT_SOUND
                                | Notification.DEFAULT_VIBRATE
                                | Notification.DEFAULT_LIGHTS)
                .setLargeIcon(contactIcon)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setGroup(CHAT_NOTIFICATIONS_GROUP)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPriority(Notification.PRIORITY_HIGH)
                .setNumber(notif.getMessages().size())
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setColor(context.getColor(R.color.notification_led_color))
                .setStyle(style)
                .addAction(ApiTwentyFourPlus.getReplyMessageAction(context, notif))
                .addAction(ApiTwentyFourPlus.getMarkMessageAsReadAction(context, notif))
                .build();
    }

    public static boolean isAppUserRestricted(Context context) {
        ActivityManager activityManager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        return activityManager.isBackgroundRestricted();
    }

    public static int getAppStandbyBucket(Context context) {
        UsageStatsManager usageStatsManager =
                (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        return usageStatsManager.getAppStandbyBucket();
    }

    public static String getAppStandbyBucketNameFromValue(int bucket) {
        switch (bucket) {
            case UsageStatsManager.STANDBY_BUCKET_ACTIVE:
                return "STANDBY_BUCKET_ACTIVE";
            case UsageStatsManager.STANDBY_BUCKET_FREQUENT:
                return "STANDBY_BUCKET_FREQUENT";
            case UsageStatsManager.STANDBY_BUCKET_RARE:
                return "STANDBY_BUCKET_RARE";
            case UsageStatsManager.STANDBY_BUCKET_WORKING_SET:
                return "STANDBY_BUCKET_WORKING_SET";
        }
        return null;
    }
}
