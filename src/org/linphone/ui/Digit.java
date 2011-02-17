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

import org.linphone.LinphoneManager;
import org.linphone.core.LinphoneCore;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.widget.Button;

public class Digit extends Button implements OnLongClickListener, AddressAwareWidget {

	private AddressText mAddress;

	
	@Override
	protected void onTextChanged(CharSequence text, int start, int before,
			int after) {
		super.onTextChanged(text, start, before, after);
		
		if (text == null || text.length() < 1) return;

		DialKeyListener lListener = new DialKeyListener();
		setOnClickListener(lListener);
		setOnTouchListener(lListener);
		
		if ("0+".equals(text)) {
			setOnLongClickListener(this);
		}
	}
	
	public boolean onLongClick(View arg0) {
		// Called if "0+" dtmf
		LinphoneCore lc = LinphoneManager.getLc();
		lc.stopDtmf();
		int lBegin = mAddress.getSelectionStart();
		if (lBegin == -1) {
			lBegin = mAddress.getEditableText().length();
		}
		if (lBegin >=0) {
		mAddress.getEditableText().insert(lBegin,"+");
		}
		return true;
	}

	public Digit(Context context, AttributeSet attrs, int style) {
		super(context, attrs, style);
	}
	
	public Digit(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public Digit(Context context) {
		super(context);
	}




	private class DialKeyListener implements OnClickListener, OnTouchListener {
		final CharSequence mKeyCode;
		boolean mIsDtmfStarted=false;

		DialKeyListener() {
			mKeyCode = Digit.this.getText().subSequence(0, 1);
		}

		public void onClick(View v) {
			LinphoneCore lc = LinphoneManager.getLc();
			lc.stopDtmf();
			mIsDtmfStarted =false;

			if (lc.isIncall()) {
				lc.sendDtmf(mKeyCode.charAt(0));
			} else {
				int lBegin = mAddress.getSelectionStart();
				if (lBegin == -1) {
					lBegin = mAddress.getEditableText().length();
				}
				if (lBegin >=0) {
					mAddress.getEditableText().insert(lBegin,mKeyCode);
				}
				mAddress.clearDisplayedName();
			}
		}

		public boolean onTouch(View v, MotionEvent event) {
			LinphoneCore lc = LinphoneManager.getLc();
			if (event.getAction() == MotionEvent.ACTION_DOWN && mIsDtmfStarted ==false) {
				LinphoneManager.getInstance().playDtmf(mKeyCode.charAt(0));
				mIsDtmfStarted=true;
			} else {
				if (event.getAction() == MotionEvent.ACTION_UP) 
					lc.stopDtmf();
					mIsDtmfStarted =false;
			}
			return false;
		}
	};
	
	public void setAddressWidget(AddressText address) {
		mAddress = address;
	}
}
