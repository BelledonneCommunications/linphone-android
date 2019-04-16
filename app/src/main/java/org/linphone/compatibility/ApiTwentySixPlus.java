package org.linphone.compatibility;

/*
ApiTwentySixPlus.java
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
import android.app.FragmentTransaction;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.provider.Settings;
import org.linphone.R;
import org.linphone.notifications.Notifiable;
import org.linphone.notifications.NotifiableMessage;

@TargetApi(26)
class ApiTwentySixPlus {
    public static String getDeviceName(Context context) {
        String name =
                Settings.Global.getString(
                        context.getContentResolver(), Settings.Global.DEVICE_NAME);
        if (name == null) {
            name = BluetoothAdapter.getDefaultAdapter().getName();
        }
        if (name == null) {
            name = Settings.Secure.getString(context.getContentResolver(), "bluetooth_name");
        }
        if (name == null) {
            name = Build.MANUFACTURER + " " + Build.MODEL;
        }
        return name;
    }

    public static Notification createRepliedNotification(Context context, String reply) {
        return new Notification.Builder(
                        context, context.getString(R.string.notification_channel_id))
                .setSmallIcon(R.drawable.topbar_chat_notification)
                .setContentText(
                        context.getString(R.string.notification_replied_label).replace("%s", reply))
                .build();
    }

    public static void createServiceChannel(Context context) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        // Create service/call notification channel
        String id = context.getString(R.string.notification_service_channel_id);
        CharSequence name = context.getString(R.string.content_title_notification_service);
        String description = context.getString(R.string.content_title_notification_service);
        NotificationChannel channel =
                new NotificationChannel(id, name, NotificationManager.IMPORTANCE_NONE);
        channel.setDescription(description);
        channel.enableVibration(false);
        channel.enableLights(false);
        channel.setShowBadge(false);
        notificationManager.createNotificationChannel(channel);
    }

    public static void createMessageChannel(Context context) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        // Create message notification channel
        String id = context.getString(R.string.notification_channel_id);
        String name = context.getString(R.string.content_title_notification);
        String description = context.getString(R.string.content_title_notification);
        NotificationChannel channel =
                new NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(description);
        channel.setLightColor(context.getColor(R.color.notification_led_color));
        channel.enableLights(true);
        channel.enableVibration(true);
        channel.setShowBadge(true);
        notificationManager.createNotificationChannel(channel);
    }

    public static Notification createMessageNotification(
            Context context, Notifiable notif, Bitmap contactIcon, PendingIntent intent) {

        Notification.MessagingStyle style = new Notification.MessagingStyle(notif.getMyself());
        for (NotifiableMessage message : notif.getMessages()) {
            Notification.MessagingStyle.Message msg =
                    new Notification.MessagingStyle.Message(
                            message.getMessage(), message.getTime(), message.getSender());
            if (message.getFilePath() != null)
                msg.setData(message.getFileMime(), message.getFilePath());
            style.addMessage(msg);
        }
        if (notif.isGroup()) {
            style.setConversationTitle(notif.getGroupTitle());
        }

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

    public static Notification createInCallNotification(
            Context context,
            int callId,
            boolean showAnswerAction,
            String msg,
            int iconID,
            Bitmap contactIcon,
            String contactName,
            PendingIntent intent) {

        Notification.Builder builder =
                new Notification.Builder(
                                context,
                                context.getString(R.string.notification_service_channel_id))
                        .setContentTitle(contactName)
                        .setContentText(msg)
                        .setSmallIcon(iconID)
                        .setAutoCancel(false)
                        .setContentIntent(intent)
                        .setLargeIcon(contactIcon)
                        .setCategory(Notification.CATEGORY_CALL)
                        .setVisibility(Notification.VISIBILITY_PUBLIC)
                        .setPriority(Notification.PRIORITY_HIGH)
                        .setWhen(System.currentTimeMillis())
                        .setShowWhen(true)
                        .setColor(context.getColor(R.color.notification_led_color))
                        .addAction(ApiTwentyFourPlus.getCallDeclineAction(context, callId));

        if (showAnswerAction) {
            builder.addAction(ApiTwentyFourPlus.getCallAnswerAction(context, callId));
        }
        return builder.build();
    }

    public static Notification createNotification(
            Context context,
            String title,
            String message,
            int icon,
            int level,
            Bitmap largeIcon,
            PendingIntent intent,
            int priority) {

        if (largeIcon != null) {
            return new Notification.Builder(
                            context, context.getString(R.string.notification_service_channel_id))
                    .setContentTitle(title)
                    .setContentText(message)
                    .setSmallIcon(icon, level)
                    .setLargeIcon(largeIcon)
                    .setContentIntent(intent)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setVisibility(Notification.VISIBILITY_SECRET)
                    .setPriority(priority)
                    .setWhen(System.currentTimeMillis())
                    .setShowWhen(true)
                    .setColor(context.getColor(R.color.notification_led_color))
                    .build();
        } else {
            return new Notification.Builder(
                            context, context.getString(R.string.notification_service_channel_id))
                    .setContentTitle(title)
                    .setContentText(message)
                    .setSmallIcon(icon, level)
                    .setContentIntent(intent)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setVisibility(Notification.VISIBILITY_SECRET)
                    .setPriority(priority)
                    .setWhen(System.currentTimeMillis())
                    .setShowWhen(true)
                    .setColor(context.getColor(R.color.notification_led_color))
                    .build();
        }
    }

    public static Notification createMissedCallNotification(
            Context context, String title, String text, PendingIntent intent) {
        return new Notification.Builder(
                        context, context.getString(R.string.notification_channel_id))
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.call_status_missed)
                .setAutoCancel(true)
                .setContentIntent(intent)
                .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE)
                .setCategory(Notification.CATEGORY_EVENT)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPriority(Notification.PRIORITY_HIGH)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setColor(context.getColor(R.color.notification_led_color))
                .build();
    }

    public static Notification createSimpleNotification(
            Context context, String title, String text, PendingIntent intent) {
        return new Notification.Builder(
                        context, context.getString(R.string.notification_channel_id))
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.linphone_logo)
                .setAutoCancel(true)
                .setContentIntent(intent)
                .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPriority(Notification.PRIORITY_HIGH)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setColorized(true)
                .setColor(context.getColor(R.color.notification_led_color))
                .build();
    }

    public static void startService(Context context, Intent intent) {
        context.startForegroundService(intent);
    }

    public static void setFragmentTransactionReorderingAllowed(
            FragmentTransaction transaction, boolean allowed) {
        transaction.setReorderingAllowed(allowed);
    }
}
