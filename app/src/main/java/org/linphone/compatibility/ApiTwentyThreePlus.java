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
import android.app.NotificationManager;
import android.content.Context;
import android.os.PowerManager;
import android.service.notification.StatusBarNotification;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.tools.Log;

@TargetApi(23)
class ApiTwentyThreePlus {

    public static boolean isAppIdleMode(Context context) {
        return ((PowerManager) context.getSystemService(Context.POWER_SERVICE)).isDeviceIdleMode();
    }

    public static boolean isDoNotDisturbSettingsAccessGranted(Context context) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        return notificationManager.isNotificationPolicyAccessGranted();
    }

    public static boolean isDoNotDisturbPolicyAllowingRinging(
            Context context, Address remoteAddress) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        int filter = notificationManager.getCurrentInterruptionFilter();
        if (filter == NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
            Log.w("[Audio Manager] Priority interruption filter detected");
            boolean accessGranted = notificationManager.isNotificationPolicyAccessGranted();
            if (!accessGranted) {
                Log.e(
                        "[Audio Manager] Access to policy is denied, let's assume it is not safe for ringing");
                return false;
            }

            NotificationManager.Policy policy = notificationManager.getNotificationPolicy();
            int callPolicy = policy.priorityCallSenders;
            if (callPolicy == NotificationManager.Policy.PRIORITY_SENDERS_ANY) {
                Log.i("[Audio Manager] Priority for calls is Any, we can ring");
            } else {
                if (remoteAddress == null) {
                    Log.e(
                            "[Audio Manager] Remote address is null, let's assume it is not safe for ringing");
                    return false;
                }

                LinphoneContact contact =
                        ContactsManager.getInstance().findContactFromAddress(remoteAddress);
                if (callPolicy == NotificationManager.Policy.PRIORITY_SENDERS_CONTACTS) {
                    Log.i("[Audio Manager] Priority for calls is Contacts, let's check");
                    if (contact == null) {
                        Log.w(
                                "[Audio Manager] Couldn't find a contact for address "
                                        + remoteAddress.asStringUriOnly());
                        return false;
                    } else {
                        Log.i(
                                "[Audio Manager] Contact found for address "
                                        + remoteAddress.asStringUriOnly()
                                        + ", we can ring");
                    }
                } else if (callPolicy == NotificationManager.Policy.PRIORITY_SENDERS_STARRED) {
                    Log.i("[Audio Manager] Priority for calls is Starred Contacts, let's check");
                    if (contact == null) {
                        Log.w(
                                "[Audio Manager] Couldn't find a contact for address "
                                        + remoteAddress.asStringUriOnly());
                        return false;
                    } else if (!contact.isFavourite()) {
                        Log.w(
                                "[Audio Manager] Contact found for address "
                                        + remoteAddress.asStringUriOnly()
                                        + ", but it isn't starred");
                        return false;
                    } else {
                        Log.i(
                                "[Audio Manager] Starred contact found for address "
                                        + remoteAddress.asStringUriOnly()
                                        + ", we can ring");
                    }
                }
            }
        } else if (filter == NotificationManager.INTERRUPTION_FILTER_ALARMS) {
            Log.w("[Audio Manager] Alarms interruption filter detected");
            return false;
        } else {
            Log.i("[Audio Manager] Interruption filter is " + filter + ", we can ring");
        }

        return true;
    }

    public static StatusBarNotification[] getActiveNotifications(NotificationManager manager) {
        return manager.getActiveNotifications();
    }
}
