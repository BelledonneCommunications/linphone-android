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
import android.widget.Button;
import android.widget.TextView;

public class Digit extends Button {

	private TextView mAddress;
	private String mDisplayName; // FIXME not linked with dialeractivity

	private final void createWidget(Context context, AttributeSet attrs) {
		String ns = "http://schemas.android.com/apk/res/android";
		String dtmf = attrs.getAttributeValue(ns, "text");
		DialKeyListener lListener = new DialKeyListener(dtmf);
		setOnClickListener(lListener);
		setOnTouchListener(lListener);
		
		if ("0+".equals(dtmf)) {
			setOnLongClickListener(new OnLongClickListener() {
				public boolean onLongClick(View arg0) {
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
			});
		}
	}
	
	public Digit(Context context, AttributeSet attrs, int style) {
		super(context, attrs, style);
		createWidget(context, attrs);
	}
	
	public Digit(Context context, AttributeSet attrs) {
		super(context, attrs);
		createWidget(context, attrs);
	}

	
	
	private class DialKeyListener implements  OnClickListener ,OnTouchListener {
		final CharSequence mKeyCode;
		boolean mIsDtmfStarted=false;
		DialKeyListener(String aKeyCode) {
			mKeyCode = aKeyCode.subSequence(0, 1);
		}
		public void onClick(View v) {
			LinphoneCore lc = LinphoneManager.getLc();
			stopDtmf(); 
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
				mDisplayName="";
			}
		}
		public boolean onTouch(View v, MotionEvent event) {
			if (event.getAction() == MotionEvent.ACTION_DOWN && mIsDtmfStarted ==false) {
				LinphoneCore lc = LinphoneManager.getLc();
				lc.playDtmf(mKeyCode.charAt(0), -1);
				mIsDtmfStarted=true;
			} else {
				if (event.getAction() == MotionEvent.ACTION_UP) 
					stopDtmf();
			}
			return false;
		}

		private void stopDtmf() {
			LinphoneCore lc = LinphoneManager.getLc();
			lc.stopDtmf();
			mIsDtmfStarted =false;
		}
		
	};
	
	public void setWidgets(TextView address, String displayName) {
		mAddress = address;
		mDisplayName = displayName;
	}
}
