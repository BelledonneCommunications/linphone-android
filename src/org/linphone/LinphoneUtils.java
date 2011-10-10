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

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCore;
import org.linphone.core.Log;
import org.linphone.mediastream.Version;
import org.linphone.mediastream.video.capture.hwconf.Hacks;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.KeyEvent;
import android.widget.ImageView;

/**
 * Helpers.
 * @author Guillaume Beraudo
 *
 */
public final class LinphoneUtils {

	private LinphoneUtils(){}

	private static boolean preventVolumeBarToDisplay = false;

	public static boolean onKeyBackGoHome(Activity activity, int keyCode) {
		if (!(keyCode == KeyEvent.KEYCODE_BACK)) {
			return false; // continue
		}

		activity.startActivity(new Intent()
			.setAction(Intent.ACTION_MAIN)
			.addCategory(Intent.CATEGORY_HOME));
		return true;
	}

	public static boolean onKeyVolumeSoftAdjust(int keyCode) {
		if (!((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
				&& Hacks.needSoftvolume())) {
			return false; // continue
		}

		if (!LinphoneService.isReady()) {
			Log.i("Couldn't change softvolume has service is not running");
			return true;
		} else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
			LinphoneManager.getInstance().adjustSoftwareVolume(1);
		} else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
			LinphoneManager.getInstance().adjustSoftwareVolume(-1);
		}
		return preventVolumeBarToDisplay;
	}


	/**
	 * @param contact sip uri
	 * @return url/uri of the resource
	 */
	public static Uri findUriPictureOfContactAndSetDisplayName(LinphoneAddress address, ContentResolver resolver) {
		ContactHelper helper = new ContactHelper(address, resolver);
		helper.query();
		return helper.getUri();
	}
	
	public static Bitmap downloadBitmap(Uri uri) {
		URL url;
		InputStream is = null;
		try {
			url = new URL(uri.toString());
			is = url.openStream();
			return BitmapFactory.decodeStream(is);
		} catch (MalformedURLException e) {
			Log.e(e, e.getMessage());
		} catch (IOException e) {
			Log.e(e, e.getMessage());
		} finally {
			try {is.close();} catch (IOException x) {}
		}
		return null;
	}

	public static void setImagePictureFromUri(Context c, ImageView view, Uri uri, int notFoundResource) {
		if (uri == null) {
			view.setImageResource(notFoundResource);
			return;
		}
		if (uri.getScheme().startsWith("http")) {
			Bitmap bm = downloadBitmap(uri);
			if (bm == null) view.setImageResource(notFoundResource);
			view.setImageBitmap(bm);
		} else {
			if (Version.sdkAboveOrEqual(Version.API06_ECLAIR_20)) {
				view.setImageURI(uri);
			} else {
				Bitmap bitmap = android.provider.Contacts.People.loadContactPhoto(c, uri, notFoundResource, null);
				view.setImageBitmap(bitmap);
			}
		}
	}

	@SuppressWarnings("unchecked")
	public static final List<LinphoneCall> getLinphoneCalls(LinphoneCore lc) {
		return (List<LinphoneCall>) lc.getCalls();
	}
}

