package org.linphone.utils;

/*
IntentUtils.java
Copyright (C) 2018  Belledonne Communications, Grenoble, France

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

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import java.util.ArrayList;
import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.contacts.ContactsManager;
import org.linphone.core.tools.Log;

public class IntentUtils {
    private static final String ACTION_CALL_LINPHONE = "org.linphone.intent.action.CallLaunched";

    public static void handleIntent(Context context, Intent intent) {
        if (intent == null) return;

        Intent newIntent = new Intent(context, LinphoneActivity.class);
        String stringFileShared;
        String stringUriFileShared;
        Uri fileUri;
        String addressToCall;

        String action = intent.getAction();
        String type = intent.getType();
        newIntent.setData(intent.getData());

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if (("text/plain").equals(type) && intent.getStringExtra(Intent.EXTRA_TEXT) != null) {
                stringFileShared = intent.getStringExtra(Intent.EXTRA_TEXT);
                newIntent.putExtra("msgShared", stringFileShared);
                Log.i("[Intent Utils] ACTION_SEND with text/plain data: " + stringFileShared);
            } else {
                fileUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                stringUriFileShared = FileUtils.getFilePath(context, fileUri);
                newIntent.putExtra("fileShared", stringUriFileShared);
                Log.i("[Intent Utils] ACTION_SEND with file: " + stringUriFileShared);
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
            if (type.startsWith("image/")) {
                ArrayList<Uri> imageUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                String filePaths = "";
                for (Uri uri : imageUris) {
                    filePaths += FileUtils.getFilePath(context, uri);
                    filePaths += ":";
                }
                newIntent.putExtra("fileShared", filePaths);
                Log.i("[Intent Utils] ACTION_SEND_MULTIPLE with files: " + filePaths);
            }
        } else if (ACTION_CALL_LINPHONE.equals(action)
                && (intent.getStringExtra("NumberToCall") != null)) {
            String numberToCall = intent.getStringExtra("NumberToCall");
            Log.i("[Intent Utils] ACTION_CALL_LINPHONE with number: " + numberToCall);
            LinphoneManager.getInstance().newOutgoingCall(numberToCall, null);
        } else if (Intent.ACTION_CALL.equals(action)) {
            if (intent.getData() != null) {
                addressToCall = intent.getData().toString();
                addressToCall = addressToCall.replace("%40", "@");
                addressToCall = addressToCall.replace("%3A", ":");
                if (addressToCall.startsWith("sip:")) {
                    addressToCall = addressToCall.substring("sip:".length());
                } else if (addressToCall.startsWith("tel:")) {
                    addressToCall = addressToCall.substring("tel:".length());
                }
                Log.i("[Intent Utils] ACTION_CALL with number: " + addressToCall);
                newIntent.putExtra("SipUriOrNumber", addressToCall);
            }
        } else if (Intent.ACTION_VIEW.equals(action)) {
            addressToCall =
                    ContactsManager.getInstance()
                            .getAddressOrNumberForAndroidContact(
                                    context.getContentResolver(), intent.getData());
            newIntent.putExtra("SipUriOrNumber", addressToCall);
            Log.i("[Intent Utils] ACTION_VIEW with number: " + addressToCall);
        } else {
            Log.i("[Intent Utils] Unknown action [" + action + "], skipping");
            return;
        }

        context.startActivity(newIntent);
    }
}
