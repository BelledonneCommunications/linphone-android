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
package org.linphone.call;

import org.linphone.core.CallParams;

public class BandwidthManager {
    private static final int HIGH_RESOLUTION = 0;
    private static final int LOW_RESOLUTION = 1;
    private static final int LOW_BANDWIDTH = 2;

    private static final int currentProfile = HIGH_RESOLUTION;

    public BandwidthManager() {
        // FIXME register a listener on NetworkManager to get notified of network state
        // FIXME register a listener on Preference to get notified of change in video enable value

        // FIXME initially get those values
    }

    public void destroy() {}

    public void updateWithProfileSettings(CallParams callParams) {
        if (callParams != null) { // in call
            // Update video parm if
            if (!isVideoPossible()) { // NO VIDEO
                callParams.enableVideo(false);
                callParams.setAudioBandwidthLimit(40);
            } else {
                callParams.enableVideo(true);
                callParams.setAudioBandwidthLimit(0); // disable limitation
            }
        }
    }

    private boolean isVideoPossible() {
        return currentProfile != LOW_BANDWIDTH;
    }
}
