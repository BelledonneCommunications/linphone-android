package org.linphone.call.telecom;

/*
LinphoneTelecomAccount.java
Copyright (C) 2019 Belledonne Communications, Grenoble, France

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

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import java.util.Arrays;
import java.util.List;
import org.linphone.LinphoneManager;
import org.linphone.LinphoneService;
import org.linphone.R;
import org.linphone.core.tools.Log;

/*This class is about creation and registration of PhoneAccount, a necessary component
for TelecomManager and ConnectionService to work properly.*/

@TargetApi(Build.VERSION_CODES.M)
public class LinphoneTelecomAccount {
    private TelecomManager mTelecomManager;
    private PhoneAccountHandle mAccountHandle;
    private PhoneAccount mAccount;
    private Context mContext;

    public LinphoneTelecomAccount(Context context) {
        mContext = context;
        mTelecomManager = (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);

        // Check if a PhoneAccount has been created and registered, then get it back to use.
        mAccount = null;
        refreshPhoneAccount();

        // Create a Linphone PhoneAccount if no one exists
        if (mAccount == null) {
            Log.w("[Telecom Manager] No existing account found, let's create it");
            mAccountHandle =
                    new PhoneAccountHandle(
                            new ComponentName(mContext, LinphoneConnectionService.class),
                            mContext.getPackageName());

            String uriAdress = LinphoneManager.getCore().getIdentity();

            mAccount =
                    PhoneAccount.builder(mAccountHandle, "Linphone")
                            .setAddress(Uri.fromParts(PhoneAccount.SCHEME_SIP, uriAdress, null))
                            .setIcon(Icon.createWithResource(mContext, R.drawable.linphone_logo))
                            .setSubscriptionAddress(null)
                            .setCapabilities(
                                    PhoneAccount.CAPABILITY_CALL_PROVIDER
                                            | PhoneAccount.CAPABILITY_VIDEO_CALLING
                                            | PhoneAccount.CAPABILITY_CONNECTION_MANAGER)
                            .setHighlightColor(Color.GREEN)
                            .setShortDescription(
                                    "Enable to allow Linphone integration and set it as default Phone Account in the next panel.")
                            .setSupportedUriSchemes(
                                    Arrays.asList(PhoneAccount.SCHEME_SIP, "tel, sip"))
                            .build();

            register();
        } else {
            mAccountHandle = mAccount.getAccountHandle();
            Log.i(
                    "[Telecom Manager] Found existing phone account, it is "
                            + (mAccount.isEnabled() ? "enabled" : "disabled"));
        }
    }

    public void refreshPhoneAccount() {
        // FIXME: check PHONE_READ_STATE permission
        List<PhoneAccountHandle> phoneAccountHandleList =
                mTelecomManager.getCallCapablePhoneAccounts();
        ComponentName linphoneConnectionService =
                new ComponentName(LinphoneService.instance(), LinphoneConnectionService.class);
        for (PhoneAccountHandle phoneAccountHandle : phoneAccountHandleList) {
            PhoneAccount phoneAccount = mTelecomManager.getPhoneAccount(phoneAccountHandle);
            if (phoneAccountHandle.getComponentName().equals(linphoneConnectionService)) {
                mAccount = phoneAccount;
                break;
            }
        }
    }

    public void register() {
        if (mTelecomManager != null && mAccount != null) {
            mTelecomManager.registerPhoneAccount(mAccount);
        }
    }

    public void unregister() {
        if (mTelecomManager != null && mAccountHandle != null) {
            mTelecomManager.unregisterPhoneAccount(mAccountHandle);
        }
    }

    public PhoneAccount getPhoneAccount() {
        return mAccount;
    }

    public PhoneAccountHandle getHandle() {
        return mAccountHandle;
    }
}
