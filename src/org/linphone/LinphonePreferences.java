package org.linphone;
/*
ChatListFragment.java
Copyright (C) 2012  Belledonne Communications, Grenoble, France

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
import java.util.HashMap;
import java.util.Map;

import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LpConfig;
import org.linphone.mediastream.Log;

import android.content.res.Resources;

/**
 * @author Sylvain Berfini
 */
public class LinphonePreferences {
	private static LinphonePreferences instance;
	private Map<String,String> dict, changesDict;
	private LpConfig config;
	
	public static final synchronized LinphonePreferences getInstance() {
		if (instance == null) {
			instance = new LinphonePreferences();
			instance.load();
		}
		return instance;
	}
	
	private LinphonePreferences() {
		dict = new HashMap<String,String>();
		changesDict = new HashMap<String,String>();
		if (LinphoneManager.getLcIfManagerNotDestroyedOrNull() == null) {
			config = LinphoneCoreFactory.instance().createLpConfig(LinphoneManager.getInstance().mLinphoneConfigFile);
		} else {
			config = LinphoneManager.getLc().getConfig();
		}
	}
	
	public String get(String key) {
		if (dict.containsKey(key)) {
			return dict.get(key);
		}
		return null;
	}
	
	public String getNew(String key) {
		if (changesDict.containsKey(key)) {
			return changesDict.get(key);
		} else if (dict.containsKey(key)) {
			return dict.get(key);
		}
		return null;
	}
	
	public void set(String key, String value) {
		if (dict.containsKey(key)) {
            if (dict.get(key) != value || value.length() == 0) {
                changesDict.put(key, value);
            }
        } else {
            changesDict.put(key, value);
        }
	}
	
	public boolean hasValueChanged(String key) {
		return changesDict.containsKey(key);
	}
	
	public void load() {
		Log.w("Preferences loading...");
		loadSection("sip", R.array.lpconfig_sip_keys);
	}
	
	private void loadSection(String section, int resourcesID) {
		Log.w("Preferences loading for section " + section);
		for (String key : LinphoneService.instance().getResources().getStringArray(resourcesID)) {
			Log.w("Looking for value for key " + key);
			String value = config.getString("sip", key, "");
			if (value != null && value.length() > 0) {
				Log.w("Value read for key " + key + " : " + value);
				dict.put(key, value);
			}
		}
	}
	
	public void save() {
		Log.w("Preferences saving...");
		Resources res = LinphoneService.instance().getResources();
		for (String key : res.getStringArray(R.array.lpconfig_sip_keys)) {
			if (hasValueChanged(key)) {
				Log.w("Saving changed preference : " + getNew(key));
				config.setString("sip", key, getNew(key));
			}
		}
		config.sync();
	}
}
