package org.linphone;

/*
SoftVolume.java
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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.telephony.TelephonyManager;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import org.linphone.compatibility.Compatibility;
import org.linphone.contacts.ContactsManager;
import org.linphone.core.DialPlan;
import org.linphone.core.AccountCreator;
import org.linphone.core.Address;
import org.linphone.core.Call;
import org.linphone.core.Call.State;
import org.linphone.core.ChatMessage;
import org.linphone.core.Core;
import org.linphone.core.Factory;
import org.linphone.core.Friend;
import org.linphone.core.FriendList;
import org.linphone.core.ProxyConfig;
import org.linphone.mediastream.Log;
import org.linphone.mediastream.video.capture.hwconf.Hacks;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

/**
 * Helpers.
 */
public final class LinphoneUtils {
	private static Context context = null;
	private static Handler mHandler = new Handler(Looper.getMainLooper());

	private LinphoneUtils() {

	}

	public static void dispatchOnUIThread(Runnable r) {
		mHandler.post(r);
	}

	//private static final String sipAddressRegExp = "^(sip:)?(\\+)?[a-z0-9]+([_\\.-][a-z0-9]+)*@([a-z0-9]+([\\.-][a-z0-9]+)*)+\\.[a-z]{2,}(:[0-9]{2,5})?$";
	//private static final String strictSipAddressRegExp = "^sip:(\\+)?[a-z0-9]+([_\\.-][a-z0-9]+)*@([a-z0-9]+([\\.-][a-z0-9]+)*)+\\.[a-z]{2,}$";

	public static boolean isSipAddress(String numberOrAddress) {
		Factory.instance().createAddress(numberOrAddress);
		return true;
	}

	public static boolean isNumberAddress(String numberOrAddress) {
		ProxyConfig proxy = LinphoneManager.getLc().createProxyConfig();
		return proxy.normalizePhoneNumber(numberOrAddress) != null;
	}

	public static boolean isStrictSipAddress(String numberOrAddress) {
		return isSipAddress(numberOrAddress) && numberOrAddress.startsWith("sip:");
	}

	public static String getAddressDisplayName(String uri){
		Address lAddress;
		lAddress = Factory.instance().createAddress(uri);
		return getAddressDisplayName(lAddress);
	}

	public static String getAddressDisplayName(Address address) {
		if (address == null) return null;

		String displayName = address.getDisplayName();
		if (displayName == null || displayName.isEmpty()) {
			displayName = address.getUsername();
		}
		if (displayName == null || displayName.isEmpty()) {
			displayName = address.asStringUriOnly();
		}
		return displayName;
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

	public static String timestampToHumanDate(Context context, long timestamp, int resFormat) {
		return LinphoneUtils.timestampToHumanDate(context, timestamp, context.getString(resFormat));
	}

	public static String timestampToHumanDate(Context context, long timestamp, String format) {
		try {
			Calendar cal = Calendar.getInstance();
			cal.setTimeInMillis(timestamp * 1000); // Core returns timestamps in seconds...

			SimpleDateFormat dateFormat;
			if (isToday(cal)) {
				dateFormat = new SimpleDateFormat(context.getResources().getString(R.string.today_date_format), Locale.getDefault());
			} else {
				dateFormat = new SimpleDateFormat(format, Locale.getDefault());
			}

			return dateFormat.format(cal.getTime());
		} catch (NumberFormatException nfe) {
			return String.valueOf(timestamp);
		}
	}

	static boolean isToday(Calendar cal) {
		return isSameDay(cal, Calendar.getInstance());
	}

	static boolean isSameDay(Calendar cal1, Calendar cal2) {
		if (cal1 == null || cal2 == null) {
			return false;
		}

		return (cal1.get(Calendar.ERA) == cal2.get(Calendar.ERA) &&
				cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
				cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR));
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
		return true;
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


	public static void setImagePictureFromUri(Context c, ImageView view, Uri pictureUri, Uri thumbnailUri) {
		if (pictureUri == null && thumbnailUri == null) {
			view.setImageBitmap(ContactsManager.getInstance().getDefaultAvatarBitmap());
			return;
		}
		if (pictureUri.getScheme().startsWith("http")) {
			Bitmap bm = downloadBitmap(pictureUri);
			if (bm == null) view.setImageResource(R.drawable.avatar);
			view.setImageBitmap(bm);
		} else {
			Bitmap bm = null;
			try {
				bm = MediaStore.Images.Media.getBitmap(c.getContentResolver(), pictureUri);
			} catch (IOException e) {
				if (thumbnailUri != null) {
					try {
						bm = MediaStore.Images.Media.getBitmap(c.getContentResolver(), thumbnailUri);
					} catch (IOException ie) {
					}
				}
			}
			if (bm != null) {
				view.setImageBitmap(bm);
			} else {
				view.setImageBitmap(ContactsManager.getInstance().getDefaultAvatarBitmap());
			}
		}
	}

	public static void setThumbnailPictureFromUri(Context c, ImageView view, Uri tUri) {
		if (tUri == null) {
			view.setImageBitmap(ContactsManager.getInstance().getDefaultAvatarBitmap());
			return;
		}
		if (tUri.getScheme().startsWith("http")) {
			Bitmap bm = downloadBitmap(tUri);
			if (bm == null) view.setImageResource(R.drawable.avatar);
			view.setImageBitmap(bm);
		} else {
			Bitmap bm = null;
			try {
				bm = MediaStore.Images.Media.getBitmap(c.getContentResolver(),tUri);
			} catch (IOException e) {
				Log.e("Error in setThumbnailPictureFromUri: " + e);
				return;
			}
			if (bm != null) {
				view.setImageBitmap(bm);
			} else {
				view.setImageBitmap(ContactsManager.getInstance().getDefaultAvatarBitmap());
			}
		}
	}

	public static final List<Call> getCallsNotInConf(Core lc) {
		List<Call> l=new ArrayList<Call>();
		for(Call c : lc.getCalls()){
			if (!(c.getConference() != null)){
				l.add(c);
			}
		}
		return l;
	}

	public static final List<Call> getCallsInConf(Core lc) {
		List<Call> l=new ArrayList<Call>();
		for(Call c : lc.getCalls()){
			if ((c.getConference() != null)){
				l.add(c);
			}
		}
		return l;
	}

	public static final List<Call> getCalls(Core lc) {
		// return a modifiable list
		return new ArrayList<Call>(Arrays.asList(lc.getCalls()));
	}

	public static final boolean hasExistingResumeableCall(Core lc) {
		for (Call c : getCalls(lc)) {
			if (c.getState() == State.Paused) {
				return true;
			}
		}
		return false;
	}

	public static final List<Call> getCallsInState(Core lc, Collection<State> states) {
		List<Call> foundCalls = new ArrayList<Call>();
		for (Call call : getCalls(lc)) {
			if (states.contains(call.getState())) {
				foundCalls.add(call);
			}
		}
		return foundCalls;
	}
	public static final List<Call> getRunningOrPausedCalls(Core lc) {
		return getCallsInState(lc, Arrays.asList(
				State.Paused,
				State.PausedByRemote,
				State.StreamsRunning));
	}

	public static final int countConferenceCalls(Core lc) {
		int count = lc.getConferenceSize();
		if ((lc.getConference() != null)) count--;
		return count;
	}

	public static int countVirtualCalls(Core lc) {
		return lc.getCallsNb() - countConferenceCalls(lc);
	}
	public static int countNonConferenceCalls(Core lc) {
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

	public static boolean isCallRunning(Call call)
	{
		if (call == null) {
			return false;
		}

		Call.State state = call.getState();

		return state == Call.State.Connected ||
				state == Call.State.Updating ||
				state == Call.State.UpdatedByRemote ||
				state == Call.State.StreamsRunning ||
				state == Call.State.Resuming;
	}

	public static boolean isCallEstablished(Call call) {
		if (call == null) {
			return false;
		}

		Call.State state = call.getState();

		return isCallRunning(call) ||
				state == Call.State.Paused ||
				state == Call.State.PausedByRemote ||
				state == Call.State.Pausing;
	}

	public static boolean isHighBandwidthConnection(Context context){
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        return (info != null && info.isConnected() && isConnectionFast(info.getType(),info.getSubtype()));
    }

	private static boolean isConnectionFast(int type, int subType){
		if (type == ConnectivityManager.TYPE_MOBILE) {
            switch (subType) {
            case TelephonyManager.NETWORK_TYPE_EDGE:
            case TelephonyManager.NETWORK_TYPE_GPRS:
            case TelephonyManager.NETWORK_TYPE_IDEN:
            	return false;
            }
		}
        //in doubt, assume connection is good.
        return true;
    }

	public static void clearLogs() {
		try {
			Runtime.getRuntime().exec(new String[] { "logcat", "-c" });
		} catch (IOException e) {
			Log.e(e);
		}
	}

    public static boolean zipLogs(StringBuilder sb, String toZipFile){
        boolean success = false;
        try {
            FileOutputStream zip = new FileOutputStream(toZipFile);

            ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(zip));
            ZipEntry entry = new ZipEntry("logs.txt");
            out.putNextEntry(entry);

            out.write(sb.toString().getBytes());

            out.close();
            success = true;

        } catch (Exception e){
            Log.e("Exception when trying to zip the logs: " + e.getMessage());
        }

        return success;
    }

	public static void collectLogs(Context context, String email) {
        BufferedReader br = null;
        Process p = null;
        StringBuilder sb = new StringBuilder();

    	try {
			p = Runtime.getRuntime().exec(new String[] { "logcat", "-d", "|", "grep", "`adb shell ps | grep " + context.getPackageName() + " | cut -c10-15`" });
	    	br = new BufferedReader(new InputStreamReader(p.getInputStream()), 2048);

            String line;
	    	while ((line = br.readLine()) != null) {
	    		sb.append(line);
	    		sb.append("\r\n");
	    	}
            String zipFilePath = context.getExternalFilesDir(null).getAbsolutePath() + "/logs.zip";
            Log.i("Saving logs to " + zipFilePath);

            if( zipLogs(sb, zipFilePath) ) {
            	final String appName = (context != null) ? context.getString(R.string.app_name) : "Linphone(?)";

                Uri zipURI = Uri.parse("file://" + zipFilePath);
                Intent i = new Intent(Intent.ACTION_SEND);
                i.putExtra(Intent.EXTRA_EMAIL, new String[]{email});
                i.putExtra(Intent.EXTRA_SUBJECT, appName + " Logs");
                i.putExtra(Intent.EXTRA_TEXT, appName + " logs");
                i.setType("application/zip");
                i.putExtra(Intent.EXTRA_STREAM, zipURI);
                try {
                    context.startActivity(Intent.createChooser(i, "Send mail..."));
                } catch (android.content.ActivityNotFoundException ex) {

                }
            }

		} catch (IOException e) {
			Log.e(e);
		}
	}

	public static String getNameFromFilePath(String filePath) {
		String name = filePath;
		int i = filePath.lastIndexOf('/');
		if (i > 0) {
			name = filePath.substring(i+1);
		}
		return name;
	}

	public static String getExtensionFromFileName(String fileName) {
		String extension = null;
		int i = fileName.lastIndexOf('.');
		if (i > 0) {
		    extension = fileName.substring(i+1);
		}
		return extension;
	}

	public static Boolean isExtensionImage(String path){
		String extension = LinphoneUtils.getExtensionFromFileName(path);
		if(extension != null)
			extension = extension.toLowerCase();
		return (extension != null && extension.matches(".*(png|jpg|jpeg|bmp|gif).*"));
	}

	public static void recursiveFileRemoval(File root) {
		if (!root.delete()) {
			if (root.isDirectory()) {
				File[] files = root.listFiles();
		        if (files != null) {
		            for (File f : files) {
		            	recursiveFileRemoval(f);
		            }
		        }
			}
		}
	}

	public static String getDisplayableUsernameFromAddress(String sipAddress) {
		String username = sipAddress;
		Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc == null) return username;

		if (username.startsWith("sip:")) {
			username = username.substring(4);
		}

		if (username.contains("@")) {
			String domain = username.split("@")[1];
			ProxyConfig lpc = lc.getDefaultProxyConfig();
			if (lpc != null) {
				if (domain.equals(lpc.getDomain())) {
					return username.split("@")[0];
				}
			} else {
				if (domain.equals(LinphoneManager.getInstance().getContext().getString(R.string.default_domain))) {
					return username.split("@")[0];
				}
			}
		}
		return username;
	}

	public static String getFullAddressFromUsername(String username) {
		String sipAddress = username;
		Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc == null || username == null) return sipAddress;

		if (!sipAddress.startsWith("sip:")) {
			sipAddress = "sip:" + sipAddress;
		}

		if (!sipAddress.contains("@")) {
			ProxyConfig lpc = lc.getDefaultProxyConfig();
			if (lpc != null) {
				sipAddress = sipAddress + "@" + lpc.getDomain();
			} else {
				sipAddress = sipAddress + "@" + LinphoneManager.getInstance().getContext().getString(R.string.default_domain);
			}
		}
		return sipAddress;
	}

	public static void storeImage(Context context, ChatMessage msg) {
		if (msg == null || msg.getFileTransferInformation() == null || msg.getAppdata() == null) return;
		File file = new File(Environment.getExternalStorageDirectory(), msg.getAppdata());
		Bitmap bm = BitmapFactory.decodeFile(file.getPath());
		if (bm == null) return;

		ContentValues values = new ContentValues();
        values.put(Images.Media.TITLE, file.getName());
        String extension = msg.getFileTransferInformation().getSubtype();
        values.put(Images.Media.MIME_TYPE, "image/" + extension);
        ContentResolver cr = context.getContentResolver();
        Uri path = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        OutputStream stream;
		try {
			stream = cr.openOutputStream(path);
			if (extension != null && extension.toLowerCase(Locale.getDefault()).equals("png")) {
				bm.compress(Bitmap.CompressFormat.PNG, 100, stream);
			} else {
				bm.compress(Bitmap.CompressFormat.JPEG, 100, stream);
			}

			stream.close();
			file.delete();
	        bm.recycle();

	        msg.setAppdata(path.toString());
		} catch (FileNotFoundException e) {
			Log.e(e);
		} catch (IOException e) {
			Log.e(e);
		}
	}

	private static Context getContext() {
		if (context == null && LinphoneManager.isInstanciated())
			context = LinphoneManager.getInstance().getContext();
		return context;
	}

	public static void displayError(boolean isOk, TextView error, String errorText) {
		if (isOk) {
			error.setVisibility(View.INVISIBLE);
			error.setText("");
		} else {
			error.setVisibility(View.VISIBLE);
			error.setText(errorText);
		}
	}

	public static String errorForPhoneNumberStatus(int status) {
		Context ctxt = getContext();
		if (ctxt != null) {
			if (AccountCreator.PhoneNumberStatus.InvalidCountryCode.toInt()
					== (status & AccountCreator.PhoneNumberStatus.InvalidCountryCode.toInt()))
				return ctxt.getString(R.string.country_code_invalid);
			if (AccountCreator.PhoneNumberStatus.TooShort.toInt()
					== (status & AccountCreator.PhoneNumberStatus.TooShort.toInt()))
				return ctxt.getString(R.string.phone_number_too_short);
			if (AccountCreator.PhoneNumberStatus.TooLong.toInt()
					== (status & AccountCreator.PhoneNumberStatus.TooLong.toInt()))
				return ctxt.getString(R.string.phone_number_too_long);
			if (AccountCreator.PhoneNumberStatus.Invalid.toInt()
					== (status & AccountCreator.PhoneNumberStatus.Invalid.toInt()))
				return ctxt.getString(R.string.phone_number_invalid);
		}
		return null;
	}

	public static String errorForEmailStatus(AccountCreator.EmailStatus status) {
		Context ctxt = getContext();
		if (ctxt != null) {
			if (status.equals(AccountCreator.EmailStatus.InvalidCharacters)
					|| status.equals(AccountCreator.EmailStatus.Malformed))
				return ctxt.getString(R.string.invalid_email);
		}
		return null;
	}

	public static String errorForUsernameStatus(AccountCreator.UsernameStatus status) {
		Context ctxt = getContext();
		if (ctxt != null) {
			if (status.equals(AccountCreator.UsernameStatus.InvalidCharacters))
				return ctxt.getString(R.string.invalid_username);
			if (status.equals(AccountCreator.UsernameStatus.TooShort))
				return ctxt.getString(R.string.username_too_short);
			if (status.equals(AccountCreator.UsernameStatus.TooLong))
				return ctxt.getString(R.string.username_too_long);
			if (status.equals(AccountCreator.UsernameStatus.Invalid))
				return ctxt.getString(R.string.username_invalid_size);
			if (status.equals(AccountCreator.UsernameStatus.InvalidCharacters))
				return ctxt.getString(R.string.invalid_display_name);
		}
		return null;
	}

	public static String errorForPasswordStatus(AccountCreator.PasswordStatus status) {
		Context ctxt = getContext();
		if (ctxt != null) {
			if (status.equals(AccountCreator.PasswordStatus.TooShort))
				return ctxt.getString(R.string.password_too_short);
			if (status.equals(AccountCreator.PasswordStatus.TooLong))
				return ctxt.getString(R.string.password_too_long);
		}
		return null;
	}

	public static String errorForStatus(AccountCreator.Status status) {
		Context ctxt = getContext();
		if (ctxt != null) {
			if (status.equals(AccountCreator.Status.RequestFailed))
				return ctxt.getString(R.string.request_failed);
			if (status.equals(AccountCreator.Status.ServerError))
				return ctxt.getString(R.string.wizard_failed);
			if (status.equals(AccountCreator.Status.AccountExist)
					|| status.equals(AccountCreator.Status.AccountExistWithAlias))
				return ctxt.getString(R.string.account_already_exist);
			if (status.equals(AccountCreator.Status.AliasIsAccount)
					|| status.equals(AccountCreator.Status.AliasExist))
				return ctxt.getString(R.string.assistant_phone_number_unavailable);
			if (status.equals(AccountCreator.Status.AccountNotExist))
				return ctxt.getString(R.string.assistant_error_bad_credentials);
			if (status.equals(AccountCreator.Status.AliasNotExist))
				return ctxt.getString(R.string.phone_number_not_exist);
			if (status.equals(AccountCreator.Status.AliasNotExist)
					|| status.equals(AccountCreator.Status.AccountNotActivated)
					|| status.equals(AccountCreator.Status.AccountAlreadyActivated)
					|| status.equals(AccountCreator.Status.AccountActivated)
					|| status.equals(AccountCreator.Status.AccountNotCreated)
					|| status.equals(AccountCreator.Status.RequestOk))
				return "";
		}
		return null;
	}

	public static String getCountryCode(EditText dialCode) {
		if(dialCode != null) {
			String code = dialCode.getText().toString();
			if(code != null && code.startsWith("+")) {
				code = code.substring(1);
			}
			return code;
		}
		return null;
	}

	public static void setCountry(DialPlan c, EditText dialCode, Button selectCountry, int countryCode) {
		if( c != null && dialCode != null && selectCountry != null) {
			dialCode.setText(c.getCountryCallingCode());
			selectCountry.setText(c.getCountry());
		} else {
			if(countryCode != -1){
				dialCode.setText("+" + countryCode);
			} else {
				dialCode.setText("+");
			}
		}
	}

	public static void displayErrorAlert(String msg, Context ctxt) {
		if (ctxt != null) {
			AlertDialog.Builder builder = new AlertDialog.Builder(ctxt);
			builder.setMessage(msg)
					.setCancelable(false)
					.setNeutralButton(ctxt.getString(R.string.ok), null)
					.show();
		}
	}


	/************************************************************************************************
	 *							Picasa/Photos management workaround									*
	 ************************************************************************************************/

	public static String getFilePath(final Context context, final Uri uri) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && DocumentsContract.isDocumentUri(context, uri)) {
			// ExternalStorageProvider
			if ("com.android.externalstorage.documents".equals(uri.getAuthority())) {
				final String docId = DocumentsContract.getDocumentId(uri);
				final String[] split = docId.split(":");
				final String type = split[0];

				if ("primary".equalsIgnoreCase(type)) {
					return Environment.getExternalStorageDirectory() + "/" + split[1];
				}

				// TODO handle non-primary volumes
			}
			// DownloadsProvider
			else if ("com.android.providers.downloads.documents".equals(uri.getAuthority())) {

				final String id = DocumentsContract.getDocumentId(uri);
				final Uri contentUri = ContentUris.withAppendedId(
						Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

				return getDataColumn(context, contentUri, null, null);
			}
			// MediaProvider
			else if ("com.android.providers.media.documents".equals(uri.getAuthority())) {
				final String docId = DocumentsContract.getDocumentId(uri);
				final String[] split = docId.split(":");
				final String type = split[0];

				Uri contentUri = null;
				if ("image".equals(type)) {
					contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
				} else if ("video".equals(type)) {
					contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
				} else if ("audio".equals(type)) {
					contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
				}

				final String selection = "_id=?";
				final String[] selectionArgs = new String[] {
						split[1]
				};

				return getDataColumn(context, contentUri, selection, selectionArgs);
			}
		} else if ("content".equalsIgnoreCase(uri.getScheme())) { // Content
			// Google photo uri example
			// content://com.google.android.apps.photos.contentprovider/0/1/mediakey%3A%2FAF1QipMObgoK_wDY66gu0QkMAi/ORIGINAL/NONE/114919
			String type = getTypeFromUri(uri, context);
			String result = getDataColumn(context, uri, null, null); //
			if (TextUtils.isEmpty(result))
				if (uri.getAuthority().contains("com.google.android") || uri.getAuthority().contains("com.android")) {
					try {
						File localFile = createFile(context, null, type);
						FileInputStream remoteFile = getSourceStream(context, uri);
						if(copyToFile(remoteFile, localFile))
							result = localFile.getAbsolutePath();
						remoteFile.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			return result;
		} else if ("file".equalsIgnoreCase(uri.getScheme())) { // File
			return uri.getPath();
		}
		return null;
	}


	private static String getTypeFromUri(Uri uri, Context context){
		ContentResolver cR = context.getContentResolver();
		MimeTypeMap mime = MimeTypeMap.getSingleton();
		String type = mime.getExtensionFromMimeType(cR.getType(uri));
		return type;
	}

	/**
	 * Copy data from a source stream to destFile.
	 * Return true if succeed, return false if failed.
	 */
	private static boolean copyToFile(InputStream inputStream, File destFile) {
		if (inputStream == null || destFile == null) return false;
		try {
			OutputStream out = new FileOutputStream(destFile);
			try {
				byte[] buffer = new byte[4096];
				int bytesRead;
				while ((bytesRead = inputStream.read(buffer)) >= 0) {
					out.write(buffer, 0, bytesRead);
				}
			} finally {
				out.close();
			}
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	public static String getStartDate() {
		try {
			return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date());
		} catch (RuntimeException e) {
			return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
		}
	}

	public static File createFile(Context context, String imageFileName, String type) throws IOException {
		if (TextUtils.isEmpty(imageFileName))
			imageFileName = getStartDate()+"."+type; // make random filename if you want.

		final File root;
		imageFileName = imageFileName;
		root = context.getExternalCacheDir();

		if (root != null && !root.exists())
			root.mkdirs();
		return new File(root, imageFileName);
	}


	public static FileInputStream getSourceStream(Context context, Uri u) throws FileNotFoundException {
		FileInputStream out = null;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			ParcelFileDescriptor parcelFileDescriptor =
					context.getContentResolver().openFileDescriptor(u, "r");
			FileDescriptor fileDescriptor = null;
			if (parcelFileDescriptor != null) {
				fileDescriptor = parcelFileDescriptor.getFileDescriptor();
				out = new FileInputStream(fileDescriptor);
			}
		} else {
			out = (FileInputStream) context.getContentResolver().openInputStream(u);
		}
		return out;
	}

	/**
	 * Get the value of the data column for this Uri. This is useful for
	 * MediaStore Uris, and other file-based ContentProviders.
	 *
	 * @param context       The context.
	 * @param uri           The Uri to query.
	 * @param selection     (Optional) Filter used in the query.
	 * @param selectionArgs (Optional) Selection arguments used in the query.
	 * @return The value of the _data column, which is typically a file path.
	 */
	static String getDataColumn(Context context, Uri uri, String selection,
								String[] selectionArgs) {

		Cursor cursor = null;
		final String column = "_data";
		final String[] projection = {
				column
		};

		try {
			cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
					null);
			if (cursor != null && cursor.moveToFirst()) {
				final int column_index = cursor.getColumnIndexOrThrow(column);
				return cursor.getString(column_index);
			}
		} finally {
			if (cursor != null)
				cursor.close();
		}

		return null;
	}

	public static String getRealPathFromURI(Context context, Uri contentUri) {
		String[] proj = {MediaStore.Images.Media.DATA};
		CursorLoader loader = new CursorLoader(context, contentUri, proj, null, null, null);
		Cursor cursor = loader.loadInBackground();
		if (cursor != null && cursor.moveToFirst()) {
			int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
			String result = cursor.getString(column_index);
			cursor.close();
			return result;
		}
		return null;
	}

    public static String processContactUri(Context context, Uri contactUri){
		ContentResolver cr = context.getContentResolver();
        InputStream stream = null;
		if(cr !=null) {
			try {
				stream = cr.openInputStream(contactUri);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			if(stream != null) {
				StringBuffer fileContent = new StringBuffer("");
				int ch;
				try {
					while ((ch = stream.read()) != -1)
						fileContent.append((char) ch);
				} catch (IOException e) {
					e.printStackTrace();
				}
				String data = new String(fileContent);
				return data;
			}
			return null;
		}
		return null;
    }

    public static String getContactNameFromVcard(String vcard){
		if(vcard != null) {
			String contactName = vcard.substring(vcard.indexOf("FN:") + 3);
			contactName = contactName.substring(0, contactName.indexOf("\n") - 1);
			contactName = contactName.replace(";", "");
			contactName = contactName.replace(" ", "");
			return contactName;
		}
		return null;
	}

    public static Uri createCvsFromString(String vcardString){
		String contactName = getContactNameFromVcard(vcardString);
        File vcfFile = new File(Environment.getExternalStorageDirectory(), contactName+".cvs");
        try {
            FileWriter fw = new FileWriter(vcfFile);
            fw.write(vcardString);
            fw.close();
            return Uri.fromFile(vcfFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

	public static Spanned getTextWithHttpLinks(String text) {
		if (text.contains("<")) {
			text = text.replace("<", "&lt;");
		}
		if (text.contains(">")) {
			text = text.replace(">", "&gt;");
		}
		if (text.contains("\n")) {
			text = text.replace("\n", "<br>");
		}
		if (text.contains("http://")) {
			int indexHttp = text.indexOf("http://");
			int indexFinHttp = text.indexOf(" ", indexHttp) == -1 ? text.length() : text.indexOf(" ", indexHttp);
			String link = text.substring(indexHttp, indexFinHttp);
			String linkWithoutScheme = link.replace("http://", "");
			text = text.replaceFirst(Pattern.quote(link), "<a href=\"" + link + "\">" + linkWithoutScheme + "</a>");
		}
		if (text.contains("https://")) {
			int indexHttp = text.indexOf("https://");
			int indexFinHttp = text.indexOf(" ", indexHttp) == -1 ? text.length() : text.indexOf(" ", indexHttp);
			String link = text.substring(indexHttp, indexFinHttp);
			String linkWithoutScheme = link.replace("https://", "");
			text = text.replaceFirst(Pattern.quote(link), "<a href=\"" + link + "\">" + linkWithoutScheme + "</a>");
		}

		return Compatibility.fromHtml(text);
	}

	public static Uri getCVSPathFromLookupUri(String content) {
		String contactId = LinphoneUtils.getNameFromFilePath(content);
		FriendList[] friendList = LinphoneManager.getLc().getFriendsLists();
		for (FriendList list : friendList) {
			for (Friend friend : list.getFriends()) {
				if (friend.getRefKey().toString().equals(contactId)) {
					String contactVcard = friend.getVcard().asVcard4String();
					Uri path = LinphoneUtils.createCvsFromString(contactVcard);
					return path;
				}
			}
		}
		return null;
	}
}

