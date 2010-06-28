/*
LinphonePreferencesActivity.java
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


import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.util.Log;

public class LinphonePreferencesActivity extends PreferenceActivity {
	   @Override
	    protected void onCreate(Bundle savedInstanceState) {
	        super.onCreate(savedInstanceState);
	        // Load the preferences from an XML resource
	        addPreferencesFromResource(R.xml.preferences);
	    }

	@Override
	protected void onPause() {
		super.onPause();
		if (isFinishing()) {
		try {
			LinphoneActivity.instance().initFromConf();
		} catch (LinphoneException e) {
			Log.e(LinphoneService.TAG, "cannot update config",e);
		}
		}
	}
	   
}
