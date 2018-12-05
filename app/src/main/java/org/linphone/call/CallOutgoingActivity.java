package org.linphone.call;

/*
CallOutgoingActivity.java
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

import android.Manifest;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import java.util.ArrayList;
import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.Call;
import org.linphone.core.Call.State;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.Reason;
import org.linphone.mediastream.Log;
import org.linphone.settings.LinphonePreferences;
import org.linphone.utils.ImageUtils;
import org.linphone.utils.LinphoneGenericActivity;
import org.linphone.utils.LinphoneUtils;

public class CallOutgoingActivity extends LinphoneGenericActivity implements OnClickListener {
    private static CallOutgoingActivity sInstance;

    private TextView mName, mNumber;
    private ImageView mContactPicture, mMicro, mSpeaker, mHangUp;
    private Call mCall;
    private CoreListenerStub mListener;
    private boolean mIsMicMuted, mIsSpeakerEnabled;

    public static boolean isInstanciated() {
        return sInstance != null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getResources().getBoolean(R.bool.orientation_portrait_only)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.call_outgoing);

        mName = findViewById(R.id.contact_name);
        mNumber = findViewById(R.id.contact_number);
        mContactPicture = findViewById(R.id.contact_picture);

        mIsMicMuted = false;
        mIsSpeakerEnabled = false;

        mMicro = findViewById(R.id.micro);
        mMicro.setOnClickListener(this);
        mSpeaker = findViewById(R.id.speaker);
        mSpeaker.setOnClickListener(this);

        // set this flag so this activity will stay in front of the keyguard
        int flags =
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
        getWindow().addFlags(flags);

        mHangUp = findViewById(R.id.outgoing_hang_up);
        mHangUp.setOnClickListener(this);

        mListener =
                new CoreListenerStub() {
                    @Override
                    public void onCallStateChanged(
                            Core lc, Call call, Call.State state, String message) {
                        if (call == mCall && State.Connected == state) {
                            if (!LinphoneActivity.isInstanciated()) {
                                return;
                            }
                            LinphoneActivity.instance().startIncallActivity();
                            return;
                        } else if (state == State.Error) {
                            // Convert Core message for internalization
                            if (call.getErrorInfo().getReason() == Reason.Declined) {
                                displayCustomToast(
                                        getString(R.string.error_call_declined),
                                        Toast.LENGTH_SHORT);
                                decline();
                            } else if (call.getErrorInfo().getReason() == Reason.NotFound) {
                                displayCustomToast(
                                        getString(R.string.error_user_not_found),
                                        Toast.LENGTH_SHORT);
                                decline();
                            } else if (call.getErrorInfo().getReason() == Reason.NotAcceptable) {
                                displayCustomToast(
                                        getString(R.string.error_incompatible_media),
                                        Toast.LENGTH_SHORT);
                                decline();
                            } else if (call.getErrorInfo().getReason() == Reason.Busy) {
                                displayCustomToast(
                                        getString(R.string.error_user_busy), Toast.LENGTH_SHORT);
                                decline();
                            } else if (message != null) {
                                displayCustomToast(
                                        getString(R.string.error_unknown) + " - " + message,
                                        Toast.LENGTH_SHORT);
                                decline();
                            }
                        } else if (state == State.End) {
                            // Convert Core message for internalization
                            if (call.getErrorInfo().getReason() == Reason.Declined) {
                                displayCustomToast(
                                        getString(R.string.error_call_declined),
                                        Toast.LENGTH_SHORT);
                                decline();
                            }
                        }

                        if (LinphoneManager.getLc().getCallsNb() == 0) {
                            finish();
                        }
                    }
                };
        sInstance = this;
    }

    @Override
    protected void onResume() {
        super.onResume();
        sInstance = this;
        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.addListener(mListener);
        }

        mCall = null;

        // Only one call ringing at a time is allowed
        if (LinphoneManager.getLcIfManagerNotDestroyedOrNull() != null) {
            for (Call call : LinphoneManager.getLc().getCalls()) {
                State cstate = call.getState();
                if (State.OutgoingInit == cstate
                        || State.OutgoingProgress == cstate
                        || State.OutgoingRinging == cstate
                        || State.OutgoingEarlyMedia == cstate) {
                    mCall = call;
                    break;
                }
                if (State.StreamsRunning == cstate) {
                    if (!LinphoneActivity.isInstanciated()) {
                        return;
                    }
                    LinphoneActivity.instance().startIncallActivity();
                    return;
                }
            }
        }
        if (mCall == null) {
            Log.e("Couldn't find outgoing call");
            finish();
            return;
        }

        Address address = mCall.getRemoteAddress();
        LinphoneContact contact = ContactsManager.getInstance().findContactFromAddress(address);
        if (contact != null) {
            ImageUtils.setImagePictureFromUri(
                    this, mContactPicture, contact.getPhotoUri(), contact.getThumbnailUri());
            mName.setText(contact.getFullName());
        } else {
            mName.setText(LinphoneUtils.getAddressDisplayName(address));
        }
        mNumber.setText(address.asStringUriOnly());
    }

    @Override
    protected void onStart() {
        super.onStart();
        checkAndRequestCallPermissions();
    }

    @Override
    protected void onPause() {
        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.removeListener(mListener);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sInstance = null;
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();

        if (id == R.id.micro) {
            mIsMicMuted = !mIsMicMuted;
            if (mIsMicMuted) {
                mMicro.setImageResource(R.drawable.micro_selected);
            } else {
                mMicro.setImageResource(R.drawable.micro_default);
            }
            LinphoneManager.getLc().enableMic(!mIsMicMuted);
        }
        if (id == R.id.speaker) {
            mIsSpeakerEnabled = !mIsSpeakerEnabled;
            if (mIsSpeakerEnabled) {
                mSpeaker.setImageResource(R.drawable.speaker_selected);
            } else {
                mSpeaker.setImageResource(R.drawable.speaker_default);
            }
            LinphoneManager.getInstance().enableSpeaker(mIsSpeakerEnabled);
        }
        if (id == R.id.outgoing_hang_up) {
            decline();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (LinphoneManager.isInstanciated()
                && (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME)) {
            LinphoneManager.getLc().terminateCall(mCall);
            finish();
        }
        return super.onKeyDown(keyCode, event);
    }

    private void displayCustomToast(final String message, final int duration) {
        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.toast, (ViewGroup) findViewById(R.id.toastRoot));

        TextView toastText = layout.findViewById(R.id.toastMessage);
        toastText.setText(message);

        final Toast toast = new Toast(getApplicationContext());
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.setDuration(duration);
        toast.setView(layout);
        toast.show();
    }

    private void decline() {
        LinphoneManager.getLc().terminateCall(mCall);
        finish();
    }

    private void checkAndRequestCallPermissions() {
        ArrayList<String> permissionsList = new ArrayList<>();

        int recordAudio =
                getPackageManager()
                        .checkPermission(Manifest.permission.RECORD_AUDIO, getPackageName());
        Log.i(
                "[Permission] Record audio permission is "
                        + (recordAudio == PackageManager.PERMISSION_GRANTED
                                ? "granted"
                                : "denied"));
        int camera =
                getPackageManager().checkPermission(Manifest.permission.CAMERA, getPackageName());
        Log.i(
                "[Permission] Camera permission is "
                        + (camera == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));

        if (recordAudio != PackageManager.PERMISSION_GRANTED) {
            if (LinphonePreferences.instance()
                            .firstTimeAskingForPermission(Manifest.permission.RECORD_AUDIO)
                    || ActivityCompat.shouldShowRequestPermissionRationale(
                            this, Manifest.permission.RECORD_AUDIO)) {
                Log.i("[Permission] Asking for record audio");
                permissionsList.add(Manifest.permission.RECORD_AUDIO);
            }
        }
        if (LinphonePreferences.instance().shouldInitiateVideoCall()
                || LinphonePreferences.instance().shouldAutomaticallyAcceptVideoRequests()) {
            if (camera != PackageManager.PERMISSION_GRANTED) {
                if (LinphonePreferences.instance()
                                .firstTimeAskingForPermission(Manifest.permission.CAMERA)
                        || ActivityCompat.shouldShowRequestPermissionRationale(
                                this, Manifest.permission.CAMERA)) {
                    Log.i("[Permission] Asking for camera");
                    permissionsList.add(Manifest.permission.CAMERA);
                }
            }
        }

        if (permissionsList.size() > 0) {
            String[] permissions = new String[permissionsList.size()];
            permissions = permissionsList.toArray(permissions);
            ActivityCompat.requestPermissions(this, permissions, 0);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        for (int i = 0; i < permissions.length; i++) {
            Log.i(
                    "[Permission] "
                            + permissions[i]
                            + " is "
                            + (grantResults[i] == PackageManager.PERMISSION_GRANTED
                                    ? "granted"
                                    : "denied"));
        }
    }
}
