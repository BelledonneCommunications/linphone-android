package org.linphone.call;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v4.content.ContextCompat;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;

import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.activities.LinphoneActivity;

import java.util.Arrays;
import java.util.List;


/*This class is about creation and registration of PhoneAccount, a necessary component
for TelecomManager and ConnectionService to work properly.*/

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class LinPhoneAccount {
    private static TelecomManager telecomManager;
    private PhoneAccountHandle accountHandle;
    private PhoneAccount account;
    private Context mContext;


    @TargetApi(Build.VERSION_CODES.M)
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public LinPhoneAccount(Context context) {
        mContext=context;
        telecomManager = (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);

        if ( ContextCompat.checkSelfPermission( LinphoneActivity.instance().getBaseContext(), Manifest.permission.READ_PHONE_STATE )
                != PackageManager.PERMISSION_GRANTED ) {
            LinphoneActivity.instance().checkAndRequestReadPhoneStatePermission();
        }

        //Check if a PhoneAccount has been created and registered, then get it back to use.

        List<PhoneAccountHandle> phoneAccountHandleList = telecomManager.getCallCapablePhoneAccounts();
        PhoneAccount phoneAccount = null;
        ComponentName linphoneConnectionService = new ComponentName(LinphoneManager.getInstance().getContext(), LinphoneConnectionService.class);
        for ( PhoneAccountHandle phoneAccountHandle : phoneAccountHandleList ) {
            phoneAccount = telecomManager.getPhoneAccount(phoneAccountHandle);
            if (phoneAccountHandle.getComponentName().equals(linphoneConnectionService)) {
                break;
            }
            phoneAccount = null;
        }

        //Create a Linphone PhoneAccount if no one exists

        if (phoneAccount == null) {
            accountHandle = new PhoneAccountHandle(
                    new ComponentName(mContext, LinphoneConnectionService.class),
                    mContext.getPackageName()
            );

            String uriAdress = LinphoneManager.getLc().getIdentity();

            account = PhoneAccount.builder(accountHandle, "Linphone")
                    .setAddress(Uri.fromParts(PhoneAccount.SCHEME_SIP, uriAdress, null))
                    .setIcon(Icon.createWithResource(mContext, R.drawable.linphone_logo))
                    .setSubscriptionAddress(null)
                    .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER |
                            PhoneAccount.CAPABILITY_VIDEO_CALLING |
                            PhoneAccount.CAPABILITY_CONNECTION_MANAGER
                    )
                    .setHighlightColor(Color.GREEN)
                    .setShortDescription("Enable to allow Linphone integration and set it as default Phone Account in the next panel.")
                    .setSupportedUriSchemes(Arrays.asList(PhoneAccount.SCHEME_SIP, "tel, sip"))
                    .build();

            registerPhoneAccount();
        } else {
            account = phoneAccount;
            accountHandle = account.getAccountHandle();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void registerPhoneAccount(){
        if (telecomManager != null && account != null){
            telecomManager.registerPhoneAccount(account);
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    public void unregisterPhoneAccount(){
        if (telecomManager != null && accountHandle != null){
            telecomManager.unregisterPhoneAccount(accountHandle);
        }
    }
    public PhoneAccount getAccount(){
        return account;
    }

    public PhoneAccountHandle getAccountHandler(){
        return accountHandle;
    }

}
