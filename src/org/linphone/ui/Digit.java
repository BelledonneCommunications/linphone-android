/*
Digit.java
Copyright (C) 2010  Belledonne Communications, Grenoble, France

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
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
*/
package org.linphone.ui;

import org.linphone.CallActivity;
import org.linphone.LinphoneManager;
import org.linphone.LinphonePreferences;
import org.linphone.LinphoneService;
import org.linphone.R;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.mediastream.Log;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class Digit extends Button implements AddressAware {

	private AddressText mAddress;
	public void setAddressWidget(AddressText address) {
		mAddress = address;
	}

	private boolean mPlayDtmf;
	public void setPlayDtmf(boolean play) {
		mPlayDtmf = play;
	}

	@Override
	protected void onTextChanged(CharSequence text, int start, int before,
			int after) {
		super.onTextChanged(text, start, before, after);
		
		if (text == null || text.length() < 1) {
			return;
		}

		DialKeyListener lListener = new DialKeyListener();
		setOnClickListener(lListener);
		setOnTouchListener(lListener);
		
		if ("0+".equals(text)) {
			setOnLongClickListener(lListener);
		}
		
		if ("1".equals(text)) {
			setOnLongClickListener(lListener);
		}
	}
	
	public Digit(Context context, AttributeSet attrs, int style) {
		super(context, attrs, style);
		setLongClickable(true);
	}
	
	public Digit(Context context, AttributeSet attrs) {
		super(context, attrs);
		setLongClickable(true);
	}

	public Digit(Context context) {
		super(context);
		setLongClickable(true);
	}

	private class DialKeyListener implements OnClickListener, OnTouchListener, OnLongClickListener {
		final char mKeyCode;
		boolean mIsDtmfStarted;

		DialKeyListener() {
			mKeyCode = Digit.this.getText().subSequence(0, 1).charAt(0);
		}

		private boolean linphoneServiceReady() {
			if (!LinphoneService.isReady()) {
				Log.w("Service is not ready while pressing digit");
				Toast.makeText(getContext(), getContext().getString(R.string.skipable_error_service_not_ready), Toast.LENGTH_SHORT).show();
				return false;
			}
			return true;
		}

		public void onClick(View v) {
			if (mPlayDtmf) {
				if (!linphoneServiceReady()) return;
				LinphoneCore lc = LinphoneManager.getLc();
				lc.stopDtmf();
				mIsDtmfStarted =false;
				if (lc.isIncall()) {
					lc.sendDtmf(mKeyCode);
				}
			}
			
			if (mAddress != null) {
				int lBegin = mAddress.getSelectionStart();
				if (lBegin == -1) {
					lBegin = mAddress.length();
				}
				if (lBegin >= 0) {
					mAddress.getEditableText().insert(lBegin,String.valueOf(mKeyCode));
				}

				if(LinphonePreferences.instance().getDebugPopupAddress() != null
						&& mAddress.getText().toString().equals(LinphonePreferences.instance().getDebugPopupAddress())){
					displayDebugPopup();
				}
			}
		}

		public void displayDebugPopup(){
			AlertDialog.Builder alertDialog = new AlertDialog.Builder(getContext());
			alertDialog.setTitle(getContext().getString(R.string.debug_popup_title));
			if(LinphonePreferences.instance().isDebugLogsEnabled()) {
				alertDialog.setItems(getContext().getResources().getStringArray(R.array.popup_send_log), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						if(which == 0){
							LinphonePreferences.instance().enableDebugLogs(false);
							LinphoneCoreFactory.instance().enableLogCollection(false);
						}
						if(which == 1) {
							LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
							if (lc != null) {
								lc.uploadLogCollection();
							}
						}
					}
				});

			} else {
				alertDialog.setItems(getContext().getResources().getStringArray(R.array.popup_enable_log), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						if(which == 0) {
							LinphonePreferences.instance().enableDebugLogs(true);
							LinphoneCoreFactory.instance().enableLogCollection(true);
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

			if (CallActivity.isInstanciated()) {
				CallActivity.instance().resetControlsHidingCallBack();
			}
			
			LinphoneCore lc = LinphoneManager.getLc();
			if (event.getAction() == MotionEvent.ACTION_DOWN && !mIsDtmfStarted) {
				LinphoneManager.getInstance().playDtmf(getContext().getContentResolver(), mKeyCode);
				mIsDtmfStarted = true;
			} else {
				if (event.getAction() == MotionEvent.ACTION_UP) {
					lc.stopDtmf();
					mIsDtmfStarted = false;
				}
			}
			return false;
		}
		
		public boolean onLongClick(View v) {
			int id = v.getId();
			LinphoneCore lc = LinphoneManager.getLc();

			if (mPlayDtmf) {
				if (!linphoneServiceReady()) return true;
				// Called if "0+" dtmf
				lc.stopDtmf();
			}
			
			if(id == R.id.Digit1 && lc.getCalls().length == 0){
				String voiceMail = LinphonePreferences.instance().getVoiceMailUri();
				mAddress.getEditableText().clear();
				if(voiceMail != null){
					mAddress.getEditableText().append(voiceMail);
					LinphoneManager.getInstance().newOutgoingCall(mAddress);
				}
				return true;
			}
			
			
			if (mAddress == null) return true;

			int lBegin = mAddress.getSelectionStart();
			if (lBegin == -1) {
				lBegin = mAddress.getEditableText().length();
			}
			if (lBegin >= 0) {
			mAddress.getEditableText().insert(lBegin,"+");
			}
			return true;
		}
	}


}
