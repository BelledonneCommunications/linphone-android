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
package org.linphone.service;

import org.linphone.utils.LinphoneUtils;

public class ServiceWaitThread extends Thread {
    private ServiceWaitThreadListener mListener;

    public ServiceWaitThread(ServiceWaitThreadListener listener) {
        mListener = listener;
    }

    @Override
    public void run() {
        while (!LinphoneService.isReady()) {
            try {
                sleep(30);
            } catch (InterruptedException e) {
                throw new RuntimeException("waiting thread sleep() has been interrupted");
            }
        }

        if (mListener != null) {
            LinphoneUtils.dispatchOnUIThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            mListener.onServiceReady();
                        }
                    });
        }
    }
}
