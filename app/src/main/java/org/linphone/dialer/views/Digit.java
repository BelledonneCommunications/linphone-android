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
package org.linphone.dialer.views;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import org.linphone.LinphoneContext;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.Call;
import org.linphone.core.Core;
import org.linphone.core.tools.Log;
import org.linphone.settings.LinphonePreferences;

@SuppressLint("AppCompatCustomView")
public class Digit extends Button implements AddressAware {
    private boolean mPlayDtmf;
    private AddressText mAddress;

    public Digit(Context context, AttributeSet attrs, int style) {
        super(context, attrs, style);
        init(context, attrs);
    }

    public Digit(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public Digit(Context context) {
        super(context);
        setLongClickable(true);
    }

    private void init(Context context, AttributeSet attrs) {
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.Numpad);
        mPlayDtmf = 1 == a.getInt(R.styleable.Numpad_play_dtmf, 1);
        a.recycle();

        setLongClickable(true);
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int before, int after) {
        super.onTextChanged(text, start, before, after);

        if (text == null || text.length() < 1) {
            return;
        }

        DialKeyListener lListener = new DialKeyListener();
        setOnClickListener(lListener);
        setOnTouchListener(lListener);

        if ("0+".equals(text.toString())) {
            setOnLongClickListener(lListener);
        }

        if ("1".equals(text.toString())) {
            setOnLongClickListener(lListener);
        }
    }

    @Override
    public void setAddressWidget(AddressText address) {
        mAddress = address;
    }

    private class DialKeyListener implements OnClickListener, OnTouchListener, OnLongClickListener {
        final char mKeyCode;
        boolean mIsDtmfStarted;

        DialKeyListener() {
            mKeyCode = Digit.this.getText().subSequence(0, 1).charAt(0);
        }

        private boolean linphoneServiceReady() {
            if (!LinphoneContext.isReady()) {
                Log.e("[Numpad] Service is not ready while pressing digit");
                return false;
            }
            return true;
        }

        public void onClick(View v) {
            if (mPlayDtmf) {
                if (!linphoneServiceReady()) return;
                Core core = LinphoneManager.getCore();
                core.stopDtmf();
                mIsDtmfStarted = false;
                Call call = core.getCurrentCall();
                if (call != null) {
                    call.sendDtmf(mKeyCode);
                }
            }

            if (mAddress != null) {
                int begin = mAddress.getSelectionStart();
                if (begin == -1) {
                    begin = mAddress.length();
                }
                if (begin >= 0) {
                    mAddress.getEditableText().insert(begin, String.valueOf(mKeyCode));
                }

                if (LinphonePreferences.instance().getDebugPopupAddress() != null
                        && mAddress.getText()
                                .toString()
                                .equals(LinphonePreferences.instance().getDebugPopupAddress())) {
                    displayDebugPopup();
                }
            }
        }

        void displayDebugPopup() {
            AlertDialog.Builder alertDialog = new AlertDialog.Builder(getContext());
            alertDialog.setTitle(getContext().getString(R.string.debug_popup_title));
            if (LinphonePreferences.instance().isDebugEnabled()) {
                alertDialog.setItems(
                        getContext().getResources().getStringArray(R.array.popup_send_log),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                if (which == 0) {
                                    LinphonePreferences.instance().setDebugEnabled(false);
                                }
                                if (which == 1) {
                                    Core core = LinphoneManager.getCore();
                                    if (core != null) {
                                        core.uploadLogCollection();
                                    }
                                }
                            }
                        });

            } else {
                alertDialog.setItems(
                        getContext().getResources().getStringArray(R.array.popup_enable_log),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                if (which == 0) {
                                    LinphonePreferences.instance().setDebugEnabled(true);
                                }
                            }
                        });
            }
            alertDialog.show();
            mAddress.getEditableText().clear();
        }

        public boolean onTouch(View v, MotionEvent event) {
            if (!mPlayDtmf) return false;
            if (!linphoneServiceReady()) return true;

            LinphoneManager.getCallManager().resetCallControlsHidingTimer();

            Core core = LinphoneManager.getCore();
            if (event.getAction() == MotionEvent.ACTION_DOWN && !mIsDtmfStarted) {
                LinphoneManager.getCallManager()
                        .playDtmf(getContext().getContentResolver(), mKeyCode);
                mIsDtmfStarted = true;
            } else {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    core.stopDtmf();
                    mIsDtmfStarted = false;
                }
            }
            return false;
        }

        public boolean onLongClick(View v) {
            int id = v.getId();
            Core core = LinphoneManager.getCore();

            if (mPlayDtmf) {
                if (!linphoneServiceReady()) return true;
                // Called if "0+" dtmf
                core.stopDtmf();
            }

            if (id == R.id.Digit1 && core.getCalls().length == 0) {
                String voiceMail = LinphonePreferences.instance().getVoiceMailUri();
                mAddress.getEditableText().clear();
                if (voiceMail != null) {
                    mAddress.getEditableText().append(voiceMail);
                    LinphoneManager.getCallManager().newOutgoingCall(mAddress);
                }
                return true;
            }

            if (mAddress == null) return true;

            int begin = mAddress.getSelectionStart();
            if (begin == -1) {
                begin = mAddress.getEditableText().length();
            }
            if (begin >= 0) {
                mAddress.getEditableText().insert(begin, "+");
            }
            return true;
        }
    }
}
