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
import org.linphone.R;
import org.linphone.core.LinphoneCoreException;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.Toast;

/**
 * @author Guillaume Beraudo
 *
 */
public class CallButton extends ImageButton implements OnClickListener, AddressAware {

	private AddressText mAddress;
	public void setAddressWidget(AddressText a) {mAddress = a;}

	private OnClickListener externalClickListener;
	public void setExternalClickListener(OnClickListener e) {externalClickListener = e;}

	public CallButton(Context context, AttributeSet attrs) {
		super(context, attrs);
		setOnClickListener(this);
	}

	public void onClick(View v) {
		try {
			if (!LinphoneManager.getInstance().acceptCallIfIncomingPending()) {
				if (mAddress.getText().length() >0) { 
					LinphoneManager.getInstance().newOutgoingCall(mAddress);
				}
			}
		} catch (LinphoneCoreException e) {
			LinphoneManager.getInstance().terminateCall();
			onWrongDestinationAddress();
		};

		if (externalClickListener != null) externalClickListener.onClick(v);
	}

	
	protected void onWrongDestinationAddress() {
		Toast toast = Toast.makeText(getContext()
				,String.format(getResources().getString(R.string.warning_wrong_destination_address),mAddress.getText().toString())
				,Toast.LENGTH_LONG);
		toast.show();
	}


}
