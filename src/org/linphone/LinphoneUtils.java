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

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneCore;
import org.linphone.mediastream.Log;
import org.linphone.mediastream.Version;
import org.linphone.mediastream.video.capture.hwconf.Hacks;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;

/**
 * Helpers.
 * @author Guillaume Beraudo
 *
 */
public final class LinphoneUtils {

	private LinphoneUtils(){}

	private static boolean preventVolumeBarToDisplay = false;
	private static final String sipAddressRegExp = "^(sip:)?(\\+)?[a-z0-9]+([_\\.-][a-z0-9]+)*@([a-z0-9]+([\\.-][a-z0-9]+)*)+\\.[a-z]{2,}(:[0-9]{2,5})?$";
	private static final String strictSipAddressRegExp = "^sip:(\\+)?[a-z0-9]+([_\\.-][a-z0-9]+)*@([a-z0-9]+([\\.-][a-z0-9]+)*)+\\.[a-z]{2,}$";

	public static boolean isSipAddress(String numberOrAddress) {
		Pattern p = Pattern.compile(sipAddressRegExp);
		Matcher m = p.matcher(numberOrAddress);
		return m != null && m.matches();
	}
	
	public static boolean isStrictSipAddress(String numberOrAddress) {
		Pattern p = Pattern.compile(strictSipAddressRegExp);
		Matcher m = p.matcher(numberOrAddress);
		return m != null && m.matches();
	}
	
	public static String getUsernameFromAddress(String address) {
		if (address.contains("sip:"))
			address = address.replace("sip:", "");
		
		if (address.contains("@"))
			address = address.split("@")[0];
		
		return address;
	}
	
	public static boolean onKeyBackGoHome(Activity activity, int keyCode, KeyEvent event) {
		if (!(keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0)) {
			return false; // continue
		}

		activity.startActivity(new Intent()
			.setAction(Intent.ACTION_MAIN)
			.addCategory(Intent.CATEGORY_HOME));
		return true;
	}

	public static boolean onKeyVolumeAdjust(int keyCode) {
		if (!((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
				&& (Hacks.needSoftvolume())|| Build.VERSION.SDK_INT >= 15)) {
			return false; // continue
		}

		if (!LinphoneService.isReady()) {
			Log.i("Couldn't change softvolume has service is not running");
			return true;
		} else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
			LinphoneManager.getInstance().adjustVolume(1);
		} else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
			LinphoneManager.getInstance().adjustVolume(-1);
		}
		return preventVolumeBarToDisplay;
	}


	/**
	 * @param contact sip uri
	 * @return url/uri of the resource
	 */
//	public static Uri findUriPictureOfContactAndSetDisplayName(LinphoneAddress address, ContentResolver resolver) {
//		return Compatibility.findUriPictureOfContactAndSetDisplayName(address, resolver);
//	}
	
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
			if (Version.sdkAboveOrEqual(Version.API06_ECLAIR_201)) {
				view.setImageURI(uri);
			} else {
				@SuppressWarnings("deprecation")
				Bitmap bitmap = android.provider.Contacts.People.loadContactPhoto(c, uri, notFoundResource, null);
				view.setImageBitmap(bitmap);
			}
		}
	}

	public static final List<LinphoneCall> getLinphoneCallsNotInConf(LinphoneCore lc) {
		List<LinphoneCall> l=new ArrayList<LinphoneCall>();
		for(LinphoneCall c : lc.getCalls()){
			if (!c.isInConference()){
				l.add(c);
			}
		}
		return l;
	}

	public static final List<LinphoneCall> getLinphoneCallsInConf(LinphoneCore lc) {
		List<LinphoneCall> l=new ArrayList<LinphoneCall>();
		for(LinphoneCall c : lc.getCalls()){
			if (c.isInConference()){
				l.add(c);
			}
		}
		return l;
	}
	
	public static final List<LinphoneCall> getLinphoneCalls(LinphoneCore lc) {
		// return a modifiable list
		return new ArrayList<LinphoneCall>(Arrays.asList(lc.getCalls()));
	}

	public static final boolean hasExistingResumeableCall(LinphoneCore lc) {
		for (LinphoneCall c : getLinphoneCalls(lc)) {
			if (c.getState() == State.Paused) {
				return true;
			}
		}
		return false;
	}

	public static final List<LinphoneCall> getCallsInState(LinphoneCore lc, Collection<State> states) {
		List<LinphoneCall> foundCalls = new ArrayList<LinphoneCall>();
		for (LinphoneCall call : getLinphoneCalls(lc)) {
			if (states.contains(call.getState())) {
				foundCalls.add(call);
			}
		}
		return foundCalls;
	}
	public static final List<LinphoneCall> getRunningOrPausedCalls(LinphoneCore lc) {
		return getCallsInState(lc, Arrays.asList(
				State.Paused,
				State.PausedByRemote,
				State.StreamsRunning));
	}

	public static final int countConferenceCalls(LinphoneCore lc) {
		int count = lc.getConferenceSize();
		if (lc.isInConference()) count--;
		return count;
	}

	public static int countVirtualCalls(LinphoneCore lc) {
		return lc.getCallsNb() - countConferenceCalls(lc);
	}
	public static int countNonConferenceCalls(LinphoneCore lc) {
		return lc.getCallsNb() - countConferenceCalls(lc);
	}

	public static void setVisibility(View v, int id, boolean visible) {
		v.findViewById(id).setVisibility(visible ? VISIBLE : GONE);
	}
	public static void setVisibility(View v, boolean visible) {
		v.setVisibility(visible ? VISIBLE : GONE);
	}
	public static void enableView(View root, int id, OnClickListener l, boolean enable) {
		View v = root.findViewById(id);
		v.setVisibility(enable ? VISIBLE : GONE);
		v.setOnClickListener(l);
	}

	public static int pixelsToDpi(Resources res, int pixels) {
		return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, (float) pixels, res.getDisplayMetrics());
	}
	
	public static boolean isCallRunning(LinphoneCall call)
	{
		if (call == null) {
			return false;
		}
		
		LinphoneCall.State state = call.getState();
		
		return state == LinphoneCall.State.Connected ||
				state == LinphoneCall.State.CallUpdating ||
				state == LinphoneCall.State.CallUpdatedByRemote ||
				state == LinphoneCall.State.StreamsRunning ||
				state == LinphoneCall.State.Resuming;
	}
	
	public static boolean isCallEstablished(LinphoneCall call) {
		if (call == null) {
			return false;
		}
		
		LinphoneCall.State state = call.getState();
		
		return isCallRunning(call) || 
				state == LinphoneCall.State.Paused ||
				state == LinphoneCall.State.PausedByRemote ||
				state == LinphoneCall.State.Pausing;
	}
	
	public static boolean isHightBandwidthConnection(Context context){
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        return (info != null && info.isConnected() && isConnectionFast(info.getType(),info.getSubtype()));
    }
	
	private static boolean isConnectionFast(int type, int subType){
        if (type == ConnectivityManager.TYPE_WIFI) {
            return true;
        } else if (type == ConnectivityManager.TYPE_MOBILE) {
            switch (subType) {
	            case TelephonyManager.NETWORK_TYPE_1xRTT:
	                return false; // ~ 50-100 kbps
	            case TelephonyManager.NETWORK_TYPE_CDMA:
	                return false; // ~ 14-64 kbps
	            case TelephonyManager.NETWORK_TYPE_EDGE:
	                return false; // ~ 50-100 kbps
	            case TelephonyManager.NETWORK_TYPE_GPRS:
	                return false; // ~ 100 kbps
	            case TelephonyManager.NETWORK_TYPE_EVDO_0:
	                return false; // ~25 kbps 
	            case TelephonyManager.NETWORK_TYPE_LTE:
	                return true; // ~ 400-1000 kbps
	            case TelephonyManager.NETWORK_TYPE_EVDO_A:
	                return true; // ~ 600-1400 kbps
	            case TelephonyManager.NETWORK_TYPE_HSDPA:
	                return true; // ~ 2-14 Mbps
	            case TelephonyManager.NETWORK_TYPE_HSPA:
	                return true; // ~ 700-1700 kbps
	            case TelephonyManager.NETWORK_TYPE_HSUPA:
	                return true; // ~ 1-23 Mbps
	            case TelephonyManager.NETWORK_TYPE_UMTS:
	                return true; // ~ 400-7000 kbps
	            case TelephonyManager.NETWORK_TYPE_EHRPD:
	                return true; // ~ 1-2 Mbps
	            case TelephonyManager.NETWORK_TYPE_EVDO_B:
	                return true; // ~ 5 Mbps
	            case TelephonyManager.NETWORK_TYPE_HSPAP:
	                return true; // ~ 10-20 Mbps
	            case TelephonyManager.NETWORK_TYPE_IDEN:
	                return true; // ~ 10+ Mbps
	            case TelephonyManager.NETWORK_TYPE_UNKNOWN:
	            default:
	                return false;
            }
        } else {
            return false;
        }
    }
}

