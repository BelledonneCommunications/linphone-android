/*
AddVideoButton.java
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

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;

public class AddVideoButton extends ImageButton implements OnClickListener {

	private AlreadyInVideoCallListener alreadyInVideoCallListener;

	public AddVideoButton(Context context, AttributeSet attrs) {
		super(context, attrs);
		setOnClickListener(this);
	}

	public void onClick(View v) {
		if (!LinphoneManager.getLc().isIncall()) return;

		// If not in video call; try to reinvite with video
		boolean alreadyInVideoCall = !LinphoneManager.reinviteWithVideo();
		if (alreadyInVideoCall && alreadyInVideoCallListener != null) {
			// In video call; going back to video call activity
			alreadyInVideoCallListener.onAlreadyInVideoCall();
		}
	}

	
	public void setOnAlreadyInVideoCallListener(AlreadyInVideoCallListener listener) {
		this.alreadyInVideoCallListener = listener;
	}



	public static interface AlreadyInVideoCallListener {
		void onAlreadyInVideoCall();
	}

}
