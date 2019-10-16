/*
 * Copyright (c) 2010-2019 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.utils;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.telephony.TelephonyManager;
import android.text.Html;
import android.text.Spanned;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Pattern;
import org.linphone.LinphoneContext;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.Address;
import org.linphone.core.Call;
import org.linphone.core.CallLog;
import org.linphone.core.Core;
import org.linphone.core.Factory;
import org.linphone.core.LogCollectionState;
import org.linphone.core.ProxyConfig;
import org.linphone.core.tools.Log;
import org.linphone.settings.LinphonePreferences;

/** Helpers. */
public final class LinphoneUtils {
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
                if (LinphoneContext.isReady()) {
                    Factory.instance()
                            .getLoggingService()
                            .addListener(LinphoneContext.instance().getJavaLoggingService());
                }
            } else {
                if (LinphoneContext.isReady()) {
                    Factory.instance()
                            .getLoggingService()
                            .removeListener(LinphoneContext.instance().getJavaLoggingService());
                }
            }
        }
    }

    public static void dispatchOnUIThread(Runnable r) {
        sHandler.post(r);
    }

    public static void dispatchOnUIThreadAfter(Runnable r, long after) {
        sHandler.postDelayed(r, after);
    }

    public static void removeFromUIThreadDispatcher(Runnable r) {
        sHandler.removeCallbacks(r);
    }

    private static boolean isSipAddress(String numberOrAddress) {
        Factory.instance().createAddress(numberOrAddress);
        return true;
    }

    public static boolean isNumberAddress(String numberOrAddress) {
        ProxyConfig proxy = LinphoneManager.getCore().createProxyConfig();
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

    public static void reloadVideoDevices() {
        Core core = LinphoneManager.getCore();
        if (core == null) return;

        Log.i("[Utils] Reloading camera devices");
        core.reloadVideoDevices();

        LinphoneManager.getInstance().resetCameraFromPreferences();
    }

    public static String getDisplayableUsernameFromAddress(String sipAddress) {
        String username = sipAddress;
        Core core = LinphoneManager.getCore();
        if (core == null) return username;

        if (username.startsWith("sip:")) {
            username = username.substring(4);
        }

        if (username.contains("@")) {
            String domain = username.split("@")[1];
            ProxyConfig lpc = core.getDefaultProxyConfig();
            if (lpc != null) {
                if (domain.equals(lpc.getDomain())) {
                    return username.split("@")[0];
                }
            } else {
                if (domain.equals(
                        LinphoneContext.instance()
                                .getApplicationContext()
                                .getString(R.string.default_domain))) {
                    return username.split("@")[0];
                }
            }
        }
        return username;
    }

    public static String getFullAddressFromUsername(String username) {
        String sipAddress = username;
        Core core = LinphoneManager.getCore();
        if (core == null || username == null) return sipAddress;

        if (!sipAddress.startsWith("sip:")) {
            sipAddress = "sip:" + sipAddress;
        }

        if (!sipAddress.contains("@")) {
            ProxyConfig lpc = core.getDefaultProxyConfig();
            if (lpc != null) {
                sipAddress = sipAddress + "@" + lpc.getDomain();
            } else {
                sipAddress =
                        sipAddress
                                + "@"
                                + LinphoneContext.instance()
                                        .getApplicationContext()
                                        .getString(R.string.default_domain);
            }
        }
        return sipAddress;
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

    public static void showTrustDeniedDialog(Context context) {
        final Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
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
                        CallLog[] logs = LinphoneManager.getCore().getCallLogs();
                        CallLog lastLog = logs[0];
                        Address addressToCall =
                                lastLog.getDir() == Call.Dir.Incoming
                                        ? lastLog.getFromAddress()
                                        : lastLog.getToAddress();
                        LinphoneManager.getCallManager()
                                .newOutgoingCall(addressToCall.asString(), null);
                        dialog.dismiss();
                    }
                });
        dialog.show();
    }

    public static Dialog getDialog(Context context, String text) {
        final Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Drawable d = new ColorDrawable(ContextCompat.getColor(context, R.color.dark_grey_color));
        d.setAlpha(200);
        dialog.setContentView(R.layout.dialog);
        dialog.getWindow()
                .setLayout(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT);
        dialog.getWindow().setBackgroundDrawable(d);

        TextView customText = dialog.findViewById(R.id.dialog_message);
        customText.setText(text);
        return dialog;
    }
}
