package org.linphone.main;

/*
DialerActivity.java
Copyright (C) 2019 Belledonne Communications, Grenoble, France

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
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.call.CallActivity;
import org.linphone.contacts.ContactsActivity;
import org.linphone.core.Call;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.views.AddressAware;
import org.linphone.views.AddressText;
import org.linphone.views.CallButton;
import org.linphone.views.EraseButton;

public class DialerActivity extends MainActivity implements AddressText.AddressChangedListener {
    private AddressAware mNumpad;
    private AddressText mAddress;
    private CallButton mStartCall, mAddCall, mTransferCall;
    private ImageView mAddContact, mBackToCall;

    private boolean mIsTransfer;
    private CoreListenerStub mListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Uses the fragment container layout to inflate the dialer view instead of using a fragment
        View dialerView = LayoutInflater.from(this).inflate(R.layout.dialer, null, false);
        LinearLayout fragmentContainer = findViewById(R.id.fragmentContainer);
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        fragmentContainer.addView(dialerView, params);

        if (isTablet()) {
            findViewById(R.id.fragmentContainer2).setVisibility(View.GONE);
        }

        mAddress = findViewById(R.id.address);
        mAddress.setAddressListener(this);

        EraseButton erase = findViewById(R.id.erase);
        erase.setAddressWidget(mAddress);

        mStartCall = findViewById(R.id.start_call);
        mStartCall.setAddressWidget(mAddress);
        mStartCall.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        LinphoneManager.getInstance().newOutgoingCall(mAddress);
                    }
                });

        mAddCall = findViewById(R.id.add_call);
        mAddCall.setAddressWidget(mAddress);
        mAddCall.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        LinphoneManager.getInstance().newOutgoingCall(mAddress);
                    }
                });

        mTransferCall = findViewById(R.id.transfer_call);
        mTransferCall.setAddressWidget(mAddress);
        mTransferCall.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Core core = LinphoneManager.getLc();
                        if (core.getCurrentCall() == null) {
                            return;
                        }
                        core.transferCall(core.getCurrentCall(), mAddress.getText().toString());
                    }
                });

        mNumpad = findViewById(R.id.numpad);
        if (mNumpad != null) {
            mNumpad.setAddressWidget(mAddress);
        }

        mAddContact = findViewById(R.id.add_contact);
        mAddContact.setEnabled(false);
        mAddContact.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(DialerActivity.this, ContactsActivity.class);
                        intent.putExtra("EditOnClick", true);
                        intent.putExtra("SipAddress", mAddress.getText().toString());
                        startActivity(intent);
                    }
                });

        mBackToCall = findViewById(R.id.back_to_call);
        mBackToCall.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(new Intent(DialerActivity.this, CallActivity.class));
                    }
                });

        mIsTransfer = false;
        if (getIntent() != null) {
            mIsTransfer = getIntent().getBooleanExtra("Transfer", false);
            mAddress.setText(getIntent().getStringExtra("SipUri"));
        }

        mListener =
                new CoreListenerStub() {
                    @Override
                    public void onCallStateChanged(
                            Core lc, Call call, Call.State state, String message) {
                        updateLayout();
                    }
                };

        // On dialer we ask for all permissions
        mPermissionsToHave =
                new String[] {
                    // This one is to allow floating notifications
                    Manifest.permission.SYSTEM_ALERT_WINDOW,
                    // Required starting Android 9 to be able to start a foreground service
                    "android.permission.FOREGROUND_SERVICE",
                    Manifest.permission.WRITE_CONTACTS,
                    Manifest.permission.READ_CONTACTS
                };
    }

    @Override
    protected void onResume() {
        super.onResume();

        mDialerSelected.setVisibility(View.VISIBLE);

        Core core = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (core != null) {
            core.addListener(mListener);
        }

        boolean isOrientationLandscape =
                getResources().getConfiguration().orientation
                        == Configuration.ORIENTATION_LANDSCAPE;
        if (isOrientationLandscape && !isTablet()) {
            ((LinearLayout) mNumpad).setVisibility(View.GONE);
        } else {
            ((LinearLayout) mNumpad).setVisibility(View.VISIBLE);
        }

        updateLayout();
    }

    @Override
    protected void onPause() {
        super.onPause();

        Core core = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (core != null) {
            core.removeListener(mListener);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("isTransfer", mIsTransfer);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mIsTransfer = savedInstanceState.getBoolean("isTransfer");
    }

    public void updateLayout() {
        Core core = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (core == null) {
            return;
        }

        boolean atLeastOneCall = core.getCallsNb() > 0;
        mStartCall.setVisibility(atLeastOneCall ? View.GONE : View.VISIBLE);
        mAddContact.setVisibility(atLeastOneCall ? View.GONE : View.VISIBLE);
        if (!atLeastOneCall) {
            if (core.getVideoActivationPolicy().getAutomaticallyInitiate()) {
                mStartCall.setImageResource(R.drawable.call_video_start);
            } else {
                mStartCall.setImageResource(R.drawable.call_audio_start);
            }
        }

        mBackToCall.setVisibility(atLeastOneCall ? View.VISIBLE : View.GONE);
        mAddCall.setVisibility(atLeastOneCall && !mIsTransfer ? View.VISIBLE : View.GONE);
        mTransferCall.setVisibility(atLeastOneCall && mIsTransfer ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onAddressChanged() {
        mAddContact.setEnabled(
                LinphoneManager.getLcIfManagerNotDestroyedOrNull() != null
                                && LinphoneManager.getLc().getCallsNb() > 0
                        || !mAddress.getText().toString().equals(""));
    }
}
