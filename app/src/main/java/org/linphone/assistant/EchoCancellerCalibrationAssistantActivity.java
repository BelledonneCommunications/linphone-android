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
package org.linphone.assistant;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.EcCalibratorStatus;
import org.linphone.core.tools.Log;
import org.linphone.settings.LinphonePreferences;

public class EchoCancellerCalibrationAssistantActivity extends AssistantActivity {
    private static final int RECORD_AUDIO_PERMISSION_RESULT = 1;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.assistant_echo_canceller_calibration);
    }

    @Override
    protected void onStart() {
        super.onStart();

        checkRecordAudioPermissionForEchoCancellerCalibration();
    }

    @Override
    protected void onResume() {
        super.onResume();

        LinphonePreferences.instance().setEchoCancellationCalibrationDone(true);
        if (isRecordAudioPermissionGranted()) {
            startEchoCancellerCalibration();
        } else {
            goToLinphoneActivity();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, final int[] grantResults) {
        for (int i = 0; i < permissions.length; i++) {
            Log.i(
                    "[Permission] "
                            + permissions[i]
                            + " has been "
                            + (grantResults[i] == PackageManager.PERMISSION_GRANTED
                                    ? "granted"
                                    : "denied"));
        }

        if (requestCode == RECORD_AUDIO_PERMISSION_RESULT) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startEchoCancellerCalibration();
            } else {
                // TODO: permission denied, display something to the user
            }
        }
    }

    private boolean isRecordAudioPermissionGranted() {
        int permissionGranted =
                getPackageManager()
                        .checkPermission(Manifest.permission.RECORD_AUDIO, getPackageName());
        Log.i(
                "[Permission] Manifest.permission.RECORD_AUDIO is "
                        + (permissionGranted == PackageManager.PERMISSION_GRANTED
                                ? "granted"
                                : "denied"));

        return permissionGranted == PackageManager.PERMISSION_GRANTED;
    }

    private void checkRecordAudioPermissionForEchoCancellerCalibration() {
        if (!isRecordAudioPermissionGranted()) {
            Log.i("[Permission] Asking for " + Manifest.permission.RECORD_AUDIO);
            ActivityCompat.requestPermissions(
                    this,
                    new String[] {Manifest.permission.RECORD_AUDIO},
                    RECORD_AUDIO_PERMISSION_RESULT);
        }
    }

    private void startEchoCancellerCalibration() {
        LinphoneManager.getCore()
                .addListener(
                        new CoreListenerStub() {
                            @Override
                            public void onEcCalibrationResult(
                                    Core core, EcCalibratorStatus status, int delayMs) {
                                if (status == EcCalibratorStatus.InProgress) return;
                                core.removeListener(this);
                                LinphoneManager.getAudioManager().routeAudioToEarPiece();
                                goToLinphoneActivity();

                                ((AudioManager) getSystemService(Context.AUDIO_SERVICE))
                                        .setMode(AudioManager.MODE_NORMAL);
                            }
                        });
        LinphoneManager.getAudioManager().startEcCalibration();
    }
}
