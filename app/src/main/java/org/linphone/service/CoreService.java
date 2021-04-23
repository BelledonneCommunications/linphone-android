/*
 * Copyright (c) 2010-2021 Belledonne Communications SARL.
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

package org.linphone.service;

import android.app.Notification;
import androidx.core.app.NotificationCompat;
import org.linphone.R;

public class CoreService extends org.linphone.core.tools.service.CoreService {
    @Override
    public void createServiceNotification() {
        mServiceNotification =
                new NotificationCompat.Builder(this, SERVICE_NOTIFICATION_CHANNEL_ID)
                        .setContentTitle(SERVICE_NOTIFICATION_TITLE)
                        .setContentText(SERVICE_NOTIFICATION_CONTENT)
                        .setSmallIcon(getApplicationInfo().icon)
                        .setAutoCancel(false)
                        .setCategory(Notification.CATEGORY_SERVICE)
                        .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                        .setWhen(System.currentTimeMillis())
                        .setShowWhen(true)
                        .setOngoing(true)
                        .setSmallIcon(R.drawable.linphone_notification_icon)
                        .build();
    }
}
