/*
CallButton.java
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
import org.linphone.core.LinphoneCoreException;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;

public class CallButton extends ImageButton implements OnClickListener, AddressAwareWidget {

	private CallButtonListener callButtonListener;
	private AddressText mAddress;

	public CallButton(Context context, AttributeSet attrs) {
		super(context, attrs);
		setOnClickListener(this);
	}

	public void onClick(View v) {
		LinphoneCore lc =  LinphoneManager.getLc();
		if (lc.isInComingInvitePending()) {
			try {
				lc.acceptCall(lc.getCurrentCall());
			} catch (LinphoneCoreException e) {
				lc.terminateCall(lc.getCurrentCall());
				callButtonListener.onWrongDestinationAddress();
			}
			return;
		}
		if (mAddress.getText().length() >0) { 
			LinphoneManager.getInstance().newOutgoingCall(mAddress);
		}
	}

	

	public static interface CallButtonListener {
		void onWrongDestinationAddress();
	}


	public void setCallButtonListerner(CallButtonListener listener) {
		callButtonListener = listener;
	}

	public void setAddressWidget(AddressText address) {
		mAddress = address;
	}

}
