package org.linphone;
/*
KeepAliveHandler.java
Copyright (C) 2013  Belledonne Communications, Grenoble, France

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

import org.linphone.mediastream.Log;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class KeepAliveHandler extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.i("Keep alive handler invoked");
		if (LinphoneManager.getLcIfManagerNotDestroyedOrNull() != null) {
			//first refresh registers
			LinphoneManager.getLc().refreshRegisters();
			//make sure iterate will have enough time, device will not sleep until exit from this method
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				Log.e("Cannot sleep for 2s", e);
			}
			
		}

	}

}
