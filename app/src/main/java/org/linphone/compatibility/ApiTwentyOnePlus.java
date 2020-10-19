/*
 * Copyright (c) 2010-2019 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.compatibility;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ContentProviderClient;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Vibrator;
import android.view.WindowManager;
import androidx.core.content.ContextCompat;
import org.linphone.R;

@TargetApi(21)
class ApiTwentyOnePlus {

    public static Notification createMessageNotification(
            Context context,
            int msgCount,
            String msgSender,
            String msg,
            Bitmap contactIcon,
            PendingIntent intent) {
        String title;
        if (msgCount == 1) {
            title = msgSender;
        } else {
            title =
                    context.getString(R.string.unread_messages)
                            .replace("%i", String.valueOf(msgCount));
        }

        return new Notification.Builder(context)
                .setContentTitle(title)
                .setContentText(msg)
                .setSmallIcon(R.drawable.topbar_chat_notification)
                .setAutoCancel(true)
                .setContentIntent(intent)
                .setDefaults(
                        Notification.DEFAULT_SOUND
                                | Notification.DEFAULT_VIBRATE
                                | Notification.DEFAULT_LIGHTS)
                .setLargeIcon(contactIcon)
                .setLights(
                        ContextCompat.getColor(context, R.color.notification_led_color),
                        context.getResources().getInteger(R.integer.notification_ms_on),
                        context.getResources().getInteger(R.integer.notification_ms_off))
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPriority(Notification.PRIORITY_HIGH)
                .setNumber(msgCount)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .build();
    }

    public static Notification createInCallNotification(
            Context context,
            String msg,
            int iconID,
            Bitmap contactIcon,
            String contactName,
            PendingIntent intent) {

        return new Notification.Builder(context)
                .setContentTitle(contactName)
                .setContentText(msg)
                .setSmallIcon(iconID)
                .setAutoCancel(false)
                .setContentIntent(intent)
                .setLargeIcon(contactIcon)
                .setCategory(Notification.CATEGORY_CALL)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_HIGH)
                .setOngoing(true)
                .setLights(
                        ContextCompat.getColor(context, R.color.notification_led_color),
                        context.getResources().getInteger(R.integer.notification_ms_on),
                        context.getResources().getInteger(R.integer.notification_ms_off))
                .setShowWhen(true)
                .build();
    }

    public static Notification createIncomingCallNotification(
            Context context,
            Bitmap contactIcon,
            String contactName,
            String sipUri,
            PendingIntent intent) {

        return new Notification.Builder(context)
                .setContentTitle(contactName)
                .setContentText(context.getString(R.string.incall_notif_incoming))
                .setSmallIcon(R.drawable.topbar_call_notification)
                .setAutoCancel(false)
                .setContentIntent(intent)
                .setLargeIcon(contactIcon)
                .setCategory(Notification.CATEGORY_CALL)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_HIGH)
                .setOngoing(true)
                .setLights(
                        ContextCompat.getColor(context, R.color.notification_led_color),
                        context.getResources().getInteger(R.integer.notification_ms_on),
                        context.getResources().getInteger(R.integer.notification_ms_off))
                .setShowWhen(true)
                .setFullScreenIntent(intent, true)
                .build();
    }

    public static Notification createNotification(
            Context context,
            String title,
            String message,
            int icon,
            int level,
            Bitmap largeIcon,
            PendingIntent intent,
            int priority,
            boolean ongoing) {
        Notification notif;

        if (largeIcon != null) {
            notif =
                    new Notification.Builder(context)
                            .setContentTitle(title)
                            .setContentText(message)
                            .setSmallIcon(icon, level)
                            .setLargeIcon(largeIcon)
                            .setContentIntent(intent)
                            .setCategory(Notification.CATEGORY_SERVICE)
                            .setVisibility(Notification.VISIBILITY_SECRET)
                            .setLights(
                                    ContextCompat.getColor(context, R.color.notification_led_color),
                                    context.getResources().getInteger(R.integer.notification_ms_on),
                                    context.getResources()
                                            .getInteger(R.integer.notification_ms_off))
                            .setWhen(System.currentTimeMillis())
                            .setPriority(priority)
                            .setShowWhen(true)
                            .setOngoing(ongoing)
                            .build();
        } else {
            notif =
                    new Notification.Builder(context)
                            .setContentTitle(title)
                            .setContentText(message)
                            .setSmallIcon(icon, level)
                            .setContentIntent(intent)
                            .setCategory(Notification.CATEGORY_SERVICE)
                            .setVisibility(Notification.VISIBILITY_SECRET)
                            .setLights(
                                    ContextCompat.getColor(context, R.color.notification_led_color),
                                    context.getResources().getInteger(R.integer.notification_ms_on),
                                    context.getResources()
                                            .getInteger(R.integer.notification_ms_off))
                            .setPriority(priority)
                            .setWhen(System.currentTimeMillis())
                            .setShowWhen(true)
                            .build();
        }

        return notif;
    }

    public static Notification createMissedCallNotification(
            Context context, String title, String text, PendingIntent intent, int count) {

        return new Notification.Builder(context)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.call_status_missed)
                .setAutoCancel(true)
                .setContentIntent(intent)
                .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE)
                .setCategory(Notification.CATEGORY_EVENT)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setLights(
                        ContextCompat.getColor(context, R.color.notification_led_color),
                        context.getResources().getInteger(R.integer.notification_ms_on),
                        context.getResources().getInteger(R.integer.notification_ms_off))
                .setPriority(Notification.PRIORITY_HIGH)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setNumber(count)
                .build();
    }

    public static Notification createSimpleNotification(
            Context context, String title, String text, PendingIntent intent) {

        return new Notification.Builder(context)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.linphone_logo)
                .setAutoCancel(true)
                .setContentIntent(intent)
                .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setLights(
                        ContextCompat.getColor(context, R.color.notification_led_color),
                        context.getResources().getInteger(R.integer.notification_ms_on),
                        context.getResources().getInteger(R.integer.notification_ms_off))
                .setWhen(System.currentTimeMillis())
                .setPriority(Notification.PRIORITY_HIGH)
                .setShowWhen(true)
                .build();
    }

    public static void closeContentProviderClient(ContentProviderClient client) {
        client.release();
    }

    public static void setShowWhenLocked(Activity activity, boolean enable) {
        if (enable) {
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }
    }

    public static void setTurnScreenOn(Activity activity, boolean enable) {
        if (enable) {
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
    }

    public static void vibrate(Vibrator vibrator) {
        long[] pattern = {0, 1000, 1000};
        vibrator.vibrate(pattern, 1);
    }
}
