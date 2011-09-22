/*
SoftVolume.java
Copyright (C) 2011  Belledonne Communications, Grenoble, France

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

import org.linphone.mediastream.video.capture.hwconf.Hacks;

import android.app.Activity;
import android.view.KeyEvent;

/**
 * Activity which handles softvolume.
 * @author Guillaume Beraudo
 *
 */
public class SoftVolumeActivity extends Activity {

	private static boolean preventVolumeBarToDisplay = false;

	
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
				&& (Hacks.needSoftvolume() || LinphonePreferenceManager.getInstance().useSoftvolume())) {

			if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
				LinphoneManager.getInstance().adjustSoftwareVolume(1);
			} else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
				LinphoneManager.getInstance().adjustSoftwareVolume(-1);
			}
		}
		
		if (!preventVolumeBarToDisplay) {
			return super.onKeyDown(keyCode, event);
		} else return true;
	}


}

