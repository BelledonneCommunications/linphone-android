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
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.linphone.LinphoneManager;
import org.linphone.LinphonePreferences;
import org.linphone.LinphoneService;
import org.linphone.LinphoneUtils;
import org.linphone.R;
import org.linphone.activities.LinphoneActivity;
import org.linphone.activities.LinphoneGenericActivity;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.Call;
import org.linphone.core.Call.State;
import org.linphone.core.CallParams;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.mediastream.Log;

import java.util.ArrayList;
import java.util.List;

public class CallIncomingActivity extends LinphoneGenericActivity {
    private static CallIncomingActivity instance;

    private TextView name, number;
    private ImageView contactPicture, accept, decline, arrow;
    private Call mCall;
    private CoreListenerStub mListener;
    private LinearLayout acceptUnlock;
    private LinearLayout declineUnlock;
    private boolean alreadyAcceptedOrDeniedCall, begin;
    private float answerX, oldMove;
    private float declineX;

    public static CallIncomingActivity instance() {
        return instance;
    }

    public static boolean isInstanciated() {
        return instance != null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getResources().getBoolean(R.bool.orientation_portrait_only)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.call_incoming);

        name = findViewById(R.id.contact_name);
        number = findViewById(R.id.contact_number);
        contactPicture = findViewById(R.id.contact_picture);

        // set this flag so this activity will stay in front of the keyguard
        int flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
        getWindow().addFlags(flags);

        final int screenWidth = getResources().getDisplayMetrics().widthPixels;

        acceptUnlock = findViewById(R.id.acceptUnlock);
        declineUnlock = findViewById(R.id.declineUnlock);

        accept = findViewById(R.id.accept);
        lookupCurrentCall();
        if (LinphonePreferences.instance() != null && mCall != null && mCall.getRemoteParams() != null &&
                LinphonePreferences.instance().shouldAutomaticallyAcceptVideoRequests() &&
                mCall.getRemoteParams().videoEnabled()) {
            accept.setImageResource(R.drawable.call_video_start);
        }
        decline = findViewById(R.id.decline);
        arrow = findViewById(R.id.arrow_hangup);
        accept.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                decline.setVisibility(View.GONE);
                acceptUnlock.setVisibility(View.VISIBLE);

            }
        });

        accept.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                float curX;
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        acceptUnlock.setVisibility(View.VISIBLE);
                        decline.setVisibility(View.GONE);
                        answerX = motionEvent.getX() + accept.getWidth() / 2;
                        begin = true;
                        oldMove = 0;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        curX = motionEvent.getX();
                        view.scrollBy((int) (answerX - curX), view.getScrollY());
                        oldMove -= answerX - curX;
                        answerX = curX;
                        if (oldMove < -25)
                            begin = false;
                        if (curX < arrow.getWidth() && !begin) {
                            answer();
                            return true;
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        view.scrollTo(0, view.getScrollY());
                        decline.setVisibility(View.VISIBLE);
                        acceptUnlock.setVisibility(View.GONE);
                        break;
                }
                return true;
            }
        });

        decline.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                float curX;
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        declineUnlock.setVisibility(View.VISIBLE);
                        accept.setVisibility(View.GONE);
                        declineX = motionEvent.getX();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        curX = motionEvent.getX();
                        view.scrollBy((int) (declineX - curX), view.getScrollY());
                        declineX = curX;
                        if (curX > (screenWidth - arrow.getWidth() * 4)) {
                            decline();
                            return true;
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        view.scrollTo(0, view.getScrollY());
                        accept.setVisibility(View.VISIBLE);
                        declineUnlock.setVisibility(View.GONE);
                        break;
                }
                return true;
            }
        });


        decline.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                accept.setVisibility(View.GONE);
                acceptUnlock.setVisibility(View.VISIBLE);
            }
        });

        mListener = new CoreListenerStub() {
            @Override
            public void onCallStateChanged(Core lc, Call call, State state, String message) {
                if (call == mCall && State.End == state) {
                    finish();
                }
                if (state == State.StreamsRunning) {
                    Log.e("CallIncommingActivity - onCreate -  State.StreamsRunning - speaker = " + LinphoneManager.getInstance().isSpeakerEnabled());
                    // The following should not be needed except some devices need it (e.g. Galaxy S).
                    LinphoneManager.getInstance().enableSpeaker(LinphoneManager.getInstance().isSpeakerEnabled());
                }
            }
        };

        super.onCreate(savedInstanceState);
        instance = this;
    }

    @Override
    protected void onResume() {
        super.onResume();
        instance = this;
        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.addListener(mListener);
        }

        alreadyAcceptedOrDeniedCall = false;
        mCall = null;

        // Only one call ringing at a time is allowed
        lookupCurrentCall();
        if (mCall == null) {
            //The incoming call no longer exists.
            Log.d("Couldn't find incoming call");
            finish();
            return;
        }


        Address address = mCall.getRemoteAddress();
        LinphoneContact contact = ContactsManager.getInstance().findContactFromAddress(address);
        if (contact != null) {
            LinphoneUtils.setImagePictureFromUri(this, contactPicture, contact.getPhotoUri(), contact.getThumbnailUri());
            name.setText(contact.getFullName());
        } else {
            name.setText(LinphoneUtils.getAddressDisplayName(address));
        }
        number.setText(address.asStringUriOnly());
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
        instance = null;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (LinphoneManager.isInstanciated() && (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME)) {
            LinphoneManager.getLc().terminateCall(mCall);
            finish();
        }
        return super.onKeyDown(keyCode, event);
    }

    private void lookupCurrentCall() {
        if (LinphoneManager.getLcIfManagerNotDestroyedOrNull() != null) {
            List<Call> calls = LinphoneUtils.getCalls(LinphoneManager.getLc());
            for (Call call : calls) {
                if (State.IncomingReceived == call.getState()) {
                    mCall = call;
                    break;
                }
            }
        }
    }

    private void decline() {
        if (alreadyAcceptedOrDeniedCall) {
            return;
        }
        alreadyAcceptedOrDeniedCall = true;

        LinphoneManager.getLc().terminateCall(mCall);
        finish();
    }

    private void answer() {
        if (alreadyAcceptedOrDeniedCall) {
            return;
        }
        alreadyAcceptedOrDeniedCall = true;

        CallParams params = LinphoneManager.getLc().createCallParams(mCall);

        boolean isLowBandwidthConnection = !LinphoneUtils.isHighBandwidthConnection(LinphoneService.instance().getApplicationContext());

        if (params != null) {
            params.enableLowBandwidth(isLowBandwidthConnection);
        } else {
            Log.e("Could not create call params for call");
        }

        if (params == null || !LinphoneManager.getInstance().acceptCallWithParams(mCall, params)) {
            // the above method takes care of Samsung Galaxy S
            Toast.makeText(this, R.string.couldnt_accept_call, Toast.LENGTH_LONG).show();
        } else {
            if (!LinphoneActivity.isInstanciated()) {
                return;
            }
            LinphoneManager.getInstance().routeAudioToReceiver();
            LinphoneActivity.instance().startIncallActivity(mCall);
        }
    }

    private void checkAndRequestCallPermissions() {
        ArrayList<String> permissionsList = new ArrayList<String>();

        int recordAudio = getPackageManager().checkPermission(Manifest.permission.RECORD_AUDIO, getPackageName());
        Log.i("[Permission] Record audio permission is " + (recordAudio == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));
        int camera = getPackageManager().checkPermission(Manifest.permission.CAMERA, getPackageName());
        Log.i("[Permission] Camera permission is " + (camera == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));

        if (recordAudio != PackageManager.PERMISSION_GRANTED) {
            if (LinphonePreferences.instance().firstTimeAskingForPermission(Manifest.permission.RECORD_AUDIO) || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
                Log.i("[Permission] Asking for record audio");
                permissionsList.add(Manifest.permission.RECORD_AUDIO);
            }
        }
        if (LinphonePreferences.instance().shouldInitiateVideoCall() || LinphonePreferences.instance().shouldAutomaticallyAcceptVideoRequests()) {
            if (camera != PackageManager.PERMISSION_GRANTED) {
                if (LinphonePreferences.instance().firstTimeAskingForPermission(Manifest.permission.CAMERA) || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
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
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        for (int i = 0; i < permissions.length; i++) {
            Log.i("[Permission] " + permissions[i] + " is " + (grantResults[i] == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));
        }
    }
}