package org.linphone.call;

/*
CallIncomingActivity.java
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
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.TextureView;
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
import org.linphone.mediastream.Log;
import org.linphone.settings.LinphonePreferences;
import org.linphone.utils.ImageUtils;
import org.linphone.utils.LinphoneGenericActivity;
import org.linphone.utils.LinphoneUtils;
import org.linphone.views.CallIncomingAnswerButton;
import org.linphone.views.CallIncomingButtonListener;
import org.linphone.views.CallIncomingDeclineButton;

public class CallIncomingActivity extends LinphoneGenericActivity {
    private static CallIncomingActivity sInstance;

    private TextView mName, mNumber;
    private ImageView mContactPicture, mAcceptIcon;
    private CallIncomingAnswerButton mAccept;
    private CallIncomingDeclineButton mDecline;
    private Call mCall;
    private CoreListenerStub mListener;
    private boolean mAlreadyAcceptedOrDeniedCall;
    private KeyguardManager mKeyguardManager;
    private TextureView mVideoDisplay;

    public static CallIncomingActivity instance() {
        return sInstance;
    }

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
        setContentView(R.layout.call_incoming);

        mName = findViewById(R.id.contact_name);
        mNumber = findViewById(R.id.contact_number);
        mContactPicture = findViewById(R.id.contact_picture);
        mVideoDisplay = findViewById(R.id.videoSurface);

        // set this flag so this activity will stay in front of the keyguard
        int flags =
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
        getWindow().addFlags(flags);

        mAccept = findViewById(R.id.answer_button);
        mDecline = findViewById(R.id.decline_button);
        mAcceptIcon = findViewById(R.id.acceptIcon);
        lookupCurrentCall();

        if (LinphonePreferences.instance() != null
                && mCall != null
                && mCall.getRemoteParams() != null
                && LinphonePreferences.instance().shouldAutomaticallyAcceptVideoRequests()
                && mCall.getRemoteParams().videoEnabled()) {
            mAcceptIcon.setImageResource(R.drawable.call_video_start);
        }

        mKeyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        boolean doNotUseSliders =
                getResources()
                        .getBoolean(
                                R.bool.do_not_use_sliders_to_answer_hangup_call_if_phone_unlocked);
        if (doNotUseSliders && !mKeyguardManager.inKeyguardRestrictedInputMode()) {
            mAccept.setSliderMode(false);
            mDecline.setSliderMode(false);
        } else {
            mAccept.setSliderMode(true);
            mDecline.setSliderMode(true);
            mAccept.setDeclineButton(mDecline);
            mDecline.setAnswerButton(mAccept);
        }
        mAccept.setListener(
                new CallIncomingButtonListener() {
                    @Override
                    public void onAction() {
                        answer();
                    }
                });
        mDecline.setListener(
                new CallIncomingButtonListener() {
                    @Override
                    public void onAction() {
                        decline();
                    }
                });

        mListener =
                new CoreListenerStub() {
                    @Override
                    public void onCallStateChanged(
                            Core lc, Call call, State state, String message) {
                        if (call == mCall && State.End == state) {
                            finish();
                        } else if (state == State.Connected) {
                            startActivity(
                                    new Intent(CallIncomingActivity.this, CallActivity.class));
                        } else if (state == State.StreamsRunning) {
                            Log.e(
                                    "CallIncommingActivity - onCreate -  State.StreamsRunning - speaker = "
                                            + LinphoneManager.getInstance().isSpeakerEnabled());
                            // The following should not be needed except some devices need it (e.g.
                            // Galaxy S).
                            LinphoneManager.getInstance()
                                    .enableSpeaker(
                                            LinphoneManager.getInstance().isSpeakerEnabled());
                        }
                    }
                };

        super.onCreate(savedInstanceState);
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

        mAlreadyAcceptedOrDeniedCall = false;
        mCall = null;

        // Only one call ringing at a time is allowed
        lookupCurrentCall();
        if (mCall == null) {
            // The incoming call no longer exists.
            Log.d("Couldn't find incoming call");
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

        if (LinphonePreferences.instance().acceptIncomingEarlyMedia()) {
            mCall.getCore().setNativeVideoWindowId(mVideoDisplay);
        }
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
        if (LinphonePreferences.instance().acceptIncomingEarlyMedia()) {
            mCall.getCore().setNativeVideoWindowId(null);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sInstance = null;
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

    private void lookupCurrentCall() {
        if (LinphoneManager.getLcIfManagerNotDestroyedOrNull() != null) {
            for (Call call : LinphoneManager.getLc().getCalls()) {
                if (State.IncomingReceived == call.getState()
                        || State.IncomingEarlyMedia == call.getState()) {
                    mCall = call;
                    break;
                }
            }
        }
    }

    private void decline() {
        if (mAlreadyAcceptedOrDeniedCall) {
            return;
        }
        mAlreadyAcceptedOrDeniedCall = true;

        LinphoneManager.getLc().terminateCall(mCall);
        finish();
    }

    private void answer() {
        if (mAlreadyAcceptedOrDeniedCall) {
            return;
        }
        mAlreadyAcceptedOrDeniedCall = true;

        if (!LinphoneManager.getInstance().acceptCall(mCall)) {
            // the above method takes care of Samsung Galaxy S
            Toast.makeText(this, R.string.couldnt_accept_call, Toast.LENGTH_LONG).show();
        } else {
            if (!LinphoneActivity.isInstanciated()) {
                return;
            }
            LinphoneManager.getInstance().routeAudioToReceiver();
            LinphoneActivity.instance().startIncallActivity();
        }
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
