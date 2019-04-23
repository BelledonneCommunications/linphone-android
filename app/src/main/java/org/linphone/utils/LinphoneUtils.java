package org.linphone.utils;

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
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.telephony.TelephonyManager;
import android.text.Html;
import android.text.Spanned;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.linphone.LinphoneManager;
import org.linphone.LinphoneService;
import org.linphone.R;
import org.linphone.core.AccountCreator;
import org.linphone.core.Address;
import org.linphone.core.Call;
import org.linphone.core.Call.State;
import org.linphone.core.CallLog;
import org.linphone.core.ChatRoom;
import org.linphone.core.ChatRoomCapabilities;
import org.linphone.core.Core;
import org.linphone.core.Factory;
import org.linphone.core.LogCollectionState;
import org.linphone.core.ProxyConfig;
import org.linphone.core.tools.Log;
import org.linphone.settings.LinphonePreferences;

/** Helpers. */
public final class LinphoneUtils {
    private static Context sContext = null;
    private static final Handler sHandler = new Handler(Looper.getMainLooper());

    private LinphoneUtils() {}

    public static void configureLoggingService(boolean isDebugEnabled, String appName) {
        if (!LinphonePreferences.instance().useJavaLogger()) {
            Factory.instance().enableLogCollection(LogCollectionState.Enabled);
            Factory.instance().setDebugMode(isDebugEnabled, appName);
        } else {
            Factory.instance().setDebugMode(isDebugEnabled, appName);
            Factory.instance()
                    .enableLogCollection(LogCollectionState.EnabledWithoutPreviousLogHandler);
            if (isDebugEnabled) {
                if (LinphoneService.isReady()) {
                    Factory.instance()
                            .getLoggingService()
                            .addListener(LinphoneService.instance().getJavaLoggingService());
                }
            } else {
                if (LinphoneService.isReady()) {
                    Factory.instance()
                            .getLoggingService()
                            .removeListener(LinphoneService.instance().getJavaLoggingService());
                }
            }
        }
    }

    public static void dispatchOnUIThread(Runnable r) {
        sHandler.post(r);
    }

    // private static final String sipAddressRegExp =
    // "^(sip:)?(\\+)?[a-z0-9]+([_\\.-][a-z0-9]+)*@([a-z0-9]+([\\.-][a-z0-9]+)*)+\\.[a-z]{2,}(:[0-9]{2,5})?$";
    // private static final String strictSipAddressRegExp =
    // "^sip:(\\+)?[a-z0-9]+([_\\.-][a-z0-9]+)*@([a-z0-9]+([\\.-][a-z0-9]+)*)+\\.[a-z]{2,}$";

    private static boolean isSipAddress(String numberOrAddress) {
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

    public static String getDisplayableAddress(Address addr) {
        return "sip:" + addr.getUsername() + "@" + addr.getDomain();
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
        if (address.contains("sip:")) address = address.replace("sip:", "");

        if (address.contains("@")) address = address.split("@")[0];

        return address;
    }

    public static boolean onKeyBackGoHome(Activity activity, int keyCode, KeyEvent event) {
        if (!(keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0)) {
            return false; // continue
        }

        activity.startActivity(
                new Intent().setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME));
        return true;
    }

    public static String timestampToHumanDate(Context context, long timestamp, int format) {
        return timestampToHumanDate(context, timestamp, context.getString(format));
    }

    public static String timestampToHumanDate(Context context, long timestamp, String format) {
        try {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(timestamp * 1000); // Core returns timestamps in seconds...

            SimpleDateFormat dateFormat;
            if (isToday(cal)) {
                dateFormat =
                        new SimpleDateFormat(
                                context.getResources().getString(R.string.today_date_format),
                                Locale.getDefault());
            } else {
                dateFormat = new SimpleDateFormat(format, Locale.getDefault());
            }

            return dateFormat.format(cal.getTime());
        } catch (NumberFormatException nfe) {
            return String.valueOf(timestamp);
        }
    }

    private static boolean isToday(Calendar cal) {
        return isSameDay(cal, Calendar.getInstance());
    }

    private static boolean isSameDay(Calendar cal1, Calendar cal2) {
        if (cal1 == null || cal2 == null) {
            return false;
        }

        return (cal1.get(Calendar.ERA) == cal2.get(Calendar.ERA)
                && cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
                && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR));
    }

    public static boolean onKeyVolumeAdjust(int keyCode) {
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

    public static List<Call> getCallsInState(Core lc, Collection<State> states) {
        List<Call> foundCalls = new ArrayList<>();
        for (Call call : lc.getCalls()) {
            if (states.contains(call.getState())) {
                foundCalls.add(call);
            }
        }
        return foundCalls;
    }

    private static boolean isCallRunning(Call call) {
        if (call == null) {
            return false;
        }

        Call.State state = call.getState();

        return state == Call.State.Connected
                || state == Call.State.Updating
                || state == Call.State.UpdatedByRemote
                || state == Call.State.StreamsRunning
                || state == Call.State.Resuming;
    }

    public static boolean isCallEstablished(Call call) {
        if (call == null) {
            return false;
        }

        Call.State state = call.getState();

        return isCallRunning(call)
                || state == Call.State.Paused
                || state == Call.State.PausedByRemote
                || state == Call.State.Pausing;
    }

    public static boolean isHighBandwidthConnection(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        return (info != null
                && info.isConnected()
                && isConnectionFast(info.getType(), info.getSubtype()));
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
        // in doubt, assume connection is good.
        return true;
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
                if (domain.equals(
                        LinphoneManager.getInstance()
                                .getContext()
                                .getString(R.string.default_domain))) {
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
                sipAddress =
                        sipAddress
                                + "@"
                                + LinphoneManager.getInstance()
                                        .getContext()
                                        .getString(R.string.default_domain);
            }
        }
        return sipAddress;
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
        Log.e("Status error " + status.name());
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
                    || status.equals(AccountCreator.Status.RequestOk)) return "";
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
            int indexFinHttp =
                    text.indexOf(" ", indexHttp) == -1
                            ? text.length()
                            : text.indexOf(" ", indexHttp);
            String link = text.substring(indexHttp, indexFinHttp);
            String linkWithoutScheme = link.replace("http://", "");
            text =
                    text.replaceFirst(
                            Pattern.quote(link),
                            "<a href=\"" + link + "\">" + linkWithoutScheme + "</a>");
        }
        if (text.contains("https://")) {
            int indexHttp = text.indexOf("https://");
            int indexFinHttp =
                    text.indexOf(" ", indexHttp) == -1
                            ? text.length()
                            : text.indexOf(" ", indexHttp);
            String link = text.substring(indexHttp, indexFinHttp);
            String linkWithoutScheme = link.replace("https://", "");
            text =
                    text.replaceFirst(
                            Pattern.quote(link),
                            "<a href=\"" + link + "\">" + linkWithoutScheme + "</a>");
        }

        return Html.fromHtml(text);
    }

    public static void hideKeyboard(Activity activity) {
        InputMethodManager imm =
                (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        View view = activity.getCurrentFocus();
        if (view == null) {
            view = new View(activity);
        }
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private static Context getContext() {
        if (sContext == null && LinphoneManager.isInstanciated())
            sContext = LinphoneManager.getInstance().getContext();
        return sContext;
    }

    public static ArrayList<ChatRoom> removeEmptyOneToOneChatRooms(ChatRoom[] rooms) {
        ArrayList<ChatRoom> newRooms = new ArrayList<>();
        for (ChatRoom room : rooms) {
            if (room.hasCapability(ChatRoomCapabilities.OneToOne.toInt())
                    && room.getHistorySize() == 0) {
                // Hide 1-1 chat rooms without messages
            } else {
                newRooms.add(room);
            }
        }
        return newRooms;
    }

    public static void showTrustDeniedDialog(Context context) {
        final Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Drawable d = new ColorDrawable(ContextCompat.getColor(context, R.color.dark_grey_color));
        d.setAlpha(200);
        dialog.setContentView(R.layout.dialog);
        dialog.getWindow()
                .setLayout(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT);
        dialog.getWindow().setBackgroundDrawable(d);

        TextView title = dialog.findViewById(R.id.dialog_title);
        title.setVisibility(View.GONE);

        TextView message = dialog.findViewById(R.id.dialog_message);
        message.setVisibility(View.VISIBLE);
        message.setText(context.getString(R.string.trust_denied));

        ImageView icon = dialog.findViewById(R.id.dialog_icon);
        icon.setVisibility(View.VISIBLE);
        icon.setImageResource(R.drawable.security_alert_indicator);

        Button delete = dialog.findViewById(R.id.dialog_delete_button);
        delete.setVisibility(View.GONE);
        Button cancel = dialog.findViewById(R.id.dialog_cancel_button);
        cancel.setVisibility(View.VISIBLE);
        Button call = dialog.findViewById(R.id.dialog_ok_button);
        call.setVisibility(View.VISIBLE);
        call.setText(R.string.call);

        cancel.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dialog.dismiss();
                    }
                });

        call.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        CallLog[] logs =
                                LinphoneManager.getLcIfManagerNotDestroyedOrNull().getCallLogs();
                        CallLog lastLog = logs[0];
                        Address addressToCall =
                                lastLog.getDir() == Call.Dir.Incoming
                                        ? lastLog.getFromAddress()
                                        : lastLog.getToAddress();
                        LinphoneManager.getInstance()
                                .newOutgoingCall(addressToCall.asString(), null);
                        dialog.dismiss();
                    }
                });
        dialog.show();
    }
}
