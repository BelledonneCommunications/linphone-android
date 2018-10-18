package org.linphone;

/*
LinphoneUtils.java
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
import android.content.Context;
import android.content.Intent;
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
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.telephony.TelephonyManager;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import org.linphone.compatibility.Compatibility;
import org.linphone.contacts.ContactsManager;
import org.linphone.core.AccountCreator;
import org.linphone.core.Address;
import org.linphone.core.Call;
import org.linphone.core.Call.State;
import org.linphone.core.ChatMessage;
import org.linphone.core.Content;
import org.linphone.core.Core;
import org.linphone.core.Factory;
import org.linphone.core.Friend;
import org.linphone.core.FriendList;
import org.linphone.core.LogCollectionState;
import org.linphone.core.LogLevel;
import org.linphone.core.LoggingService;
import org.linphone.core.LoggingServiceListener;
import org.linphone.core.ProxyConfig;
import org.linphone.mediastream.Log;
import org.linphone.mediastream.video.capture.hwconf.Hacks;
import org.linphone.ui.LinphoneMediaScanner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
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

    public static void initLoggingService(boolean isDebugEnabled, String appName) {
        if (!LinphonePreferences.instance().useJavaLogger()) {
            Factory.instance().enableLogCollection(LogCollectionState.Enabled);
            Factory.instance().setDebugMode(isDebugEnabled, appName);
        } else {
            Factory.instance().setDebugMode(isDebugEnabled, appName);
            Factory.instance().enableLogCollection(LogCollectionState.EnabledWithoutPreviousLogHandler);
            Factory.instance().getLoggingService().setListener(new LoggingServiceListener() {
                @Override
                public void onLogMessageWritten(LoggingService logService, String domain, LogLevel lev, String message) {
                    switch (lev) {
                        case Debug:
                            android.util.Log.d(domain, message);
                            break;
                        case Message:
                            android.util.Log.i(domain, message);
                            break;
                        case Warning:
                            android.util.Log.w(domain, message);
                            break;
                        case Error:
                            android.util.Log.e(domain, message);
                            break;
                        case Fatal:
                        default:
                            android.util.Log.wtf(domain, message);
                            break;
                    }
                }
            });
        }
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

    public static String getAddressDisplayName(String uri) {
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
                && (Hacks.needSoftvolume()) || Build.VERSION.SDK_INT >= 15)) {
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
            try {
                is.close();
            } catch (IOException x) {
            }
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
                bm = MediaStore.Images.Media.getBitmap(c.getContentResolver(), tUri);
            } catch (IOException e) {
            }
            if (bm != null) {
                view.setImageBitmap(bm);
            } else {
                view.setImageBitmap(ContactsManager.getInstance().getDefaultAvatarBitmap());
            }
        }
    }

    public static final List<Call> getCalls(Core lc) {
        // return a modifiable list
        return new ArrayList<>(Arrays.asList(lc.getCalls()));
    }

    public static final List<Call> getCallsInState(Core lc, Collection<State> states) {
        List<Call> foundCalls = new ArrayList<>();
        for (Call call : getCalls(lc)) {
            if (states.contains(call.getState())) {
                foundCalls.add(call);
            }
        }
        return foundCalls;
    }

    public static void setVisibility(View v, int id, boolean visible) {
        v.findViewById(id).setVisibility(visible ? VISIBLE : GONE);
    }

    public static void setVisibility(View v, boolean visible) {
        v.setVisibility(visible ? VISIBLE : GONE);
    }

    public static boolean isCallRunning(Call call) {
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

    public static boolean isHighBandwidthConnection(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        return (info != null && info.isConnected() && isConnectionFast(info.getType(), info.getSubtype()));
    }

    private static boolean isConnectionFast(int type, int subType) {
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

    public static String getNameFromFilePath(String filePath) {
        String name = filePath;
        int i = filePath.lastIndexOf('/');
        if (i > 0) {
            name = filePath.substring(i + 1);
        }
        return name;
    }

    public static String getExtensionFromFileName(String fileName) {
        String extension = null;
        int i = fileName.lastIndexOf('.');
        if (i > 0) {
            extension = fileName.substring(i + 1);
        }
        return extension;
    }

    public static Boolean isExtensionImage(String path) {
        String extension = LinphoneUtils.getExtensionFromFileName(path);
        if (extension != null)
            extension = extension.toLowerCase();
        return (extension != null && extension.matches("(png|jpg|jpeg|bmp|gif)"));
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
        if (dialCode != null) {
            String code = dialCode.getText().toString();
            if (code != null && code.startsWith("+")) {
                code = code.substring(1);
            }
            return code;
        }
        return null;
    }

    public static void displayErrorAlert(String msg, Context ctxt) {
        if (ctxt != null && msg != null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(ctxt);
            builder.setMessage(msg)
                    .setCancelable(false)
                    .setNeutralButton(ctxt.getString(R.string.ok), null)
                    .show();
        }
    }


    public static String getFilePath(final Context context, final Uri uri) {
        if (uri == null) return null;

        String result = null;
        String name = getNameFromUri(uri, context);

        try {
            File localFile = createFile(context, name);
            InputStream remoteFile = context.getContentResolver().openInputStream(uri);

            if (copyToFile(remoteFile, localFile)) {
                result = localFile.getAbsolutePath();
            }

            remoteFile.close();
        } catch (IOException e) {
            Log.e("Enable to get sharing file", e);
        }

        return result;
    }

    private static String getNameFromUri(Uri uri, Context context) {
        String name = null;
        if (uri.getScheme().equals("content")) {
            Cursor returnCursor = context.getContentResolver().query(uri, null, null, null, null);
            if (returnCursor != null) {
                returnCursor.moveToFirst();
                int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                name = returnCursor.getString(nameIndex);
                returnCursor.close();
            }
        } else if (uri.getScheme().equals("file")) {
            name = uri.getLastPathSegment();
        }
        return name;
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

    public static File createFile(Context context, String fileName) throws IOException {
        if (TextUtils.isEmpty(fileName))
            fileName = getStartDate();

        if (!fileName.contains(".")) {
            fileName = fileName + ".unknown";
        }

        final File root;
        root = context.getExternalCacheDir();

        if (root != null && !root.exists())
            root.mkdirs();
        return new File(root, fileName);
    }

    public static String getRealPathFromURI(Context context, Uri contentUri) {
        String[] proj = {MediaStore.Images.Media.DATA};
        Cursor cursor = context.getContentResolver().query(contentUri, proj, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            String result = cursor.getString(column_index);
            cursor.close();
            return result;
        }
        return null;
    }

    public static String getContactNameFromVcard(String vcard) {
        if (vcard != null) {
            String contactName = vcard.substring(vcard.indexOf("FN:") + 3);
            contactName = contactName.substring(0, contactName.indexOf("\n") - 1);
            contactName = contactName.replace(";", "");
            contactName = contactName.replace(" ", "");
            return contactName;
        }
        return null;
    }

    public static Uri createCvsFromString(String vcardString) {
        String contactName = getContactNameFromVcard(vcardString);
        File vcfFile = new File(Environment.getExternalStorageDirectory(), contactName + ".cvs");
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
        if (text == null) return null;

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

    public static String getStorageDirectory(Context mContext) {
        String storageDir = Environment.getExternalStorageDirectory() + "/" + mContext.getString(mContext.getResources().getIdentifier("app_name", "string", mContext.getPackageName()));
        File file = new File(storageDir);
        if (!file.isDirectory() || !file.exists()) {
            Log.w("Directory " + file + " doesn't seem to exists yet, let's create it");
            file.mkdirs();
            LinphoneManager.getInstance().getMediaScanner().scanFile(file);
        }
        return storageDir;
    }

    public static void hideKeyboard(Activity activity) {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        View view = activity.getCurrentFocus();
        if (view == null) {
            view = new View(activity);
        }
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    public static void scanFile(ChatMessage message) {
        String appData = message.getAppdata();
        if (appData == null) {
            for (Content c : message.getContents()) {
                if (c.isFile()) {
                    appData = c.getFilePath();
                }
            }
        }
        LinphoneManager.getInstance().getMediaScanner().scanFile(new File(appData));
    }
}

