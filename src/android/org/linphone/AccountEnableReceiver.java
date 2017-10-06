package org.linphone;

/*
AccountEnableReceiver.java
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class AccountEnableReceiver extends BroadcastReceiver {
	private static final String TAG = "AccountEnableReceiver";
	private static final String FIELD_ID = "id";
	private static final String FIELD_ACTIVE = "active";

	@Override
	public void onReceive(Context context, Intent intent) {
		int prefsAccountIndex = (int)(long)intent.getLongExtra(FIELD_ID, -1);
		boolean enable = intent.getBooleanExtra(FIELD_ACTIVE, true);
		Log.i(TAG, "Received broadcast for index=" + Integer.toString(prefsAccountIndex) + ",enable=" + Boolean.toString(enable));
		if (prefsAccountIndex < 0 || prefsAccountIndex >= LinphonePreferences.instance().getAccountCount())
			return;
		LinphonePreferences.instance().setAccountEnabled(prefsAccountIndex, enable);
	}
}

