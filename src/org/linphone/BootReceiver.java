/*
BootReceiver.java
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
package org.linphone;

import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LpConfig;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		
		String path = context.getFilesDir().getAbsolutePath() + "/.linphonerc";
		LpConfig lpConfig = LinphoneCoreFactory.instance().createLpConfig(path);
		if (lpConfig.getBool("app", "auto_start", false)) {
			Intent lLinphoneServiceIntent = new Intent(Intent.ACTION_MAIN);
			lLinphoneServiceIntent.setClass(context, LinphoneService.class);
			context.startService(lLinphoneServiceIntent);
		}
	}
}
