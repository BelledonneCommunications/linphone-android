package org.linphone.call;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.telecom.TelecomManager;

import org.linphone.LinphoneManager;
import org.linphone.LinphoneUtils;
import org.linphone.core.Call;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.mediastream.Log;

import java.util.List;

public class TelecomManagerHelper {

    private Call mCall;
    private TelecomManager telecomManager;
    private CoreListenerStub mListener;
    private boolean alreadyAcceptedOrDeniedCall;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public TelecomManagerHelper () {
            telecomManager = (TelecomManager) LinphoneManager.getInstance().getContext().getSystemService(Context.TELECOM_SERVICE);
    }

    public void startIncall (){
        alreadyAcceptedOrDeniedCall = false;
        mCall = null;
        lookupCurrentCall();

//        mListener = new CoreListenerStub(){
//            @Override
//            public void onCallStateChanged(Core lc, Call call, Call.State state, String message) {
//                if (call == mCall && Call.State.End == state) {
//                    finish();
//                }
//                if (state == Call.State.StreamsRunning) {
//                    Log.e("CallIncommingActivity - onCreate -  State.StreamsRunning - speaker = "+ LinphoneManager.getInstance().isSpeakerEnabled());
//                    // The following should not be needed except some devices need it (e.g. Galaxy S).
//                    LinphoneManager.getInstance().enableSpeaker(LinphoneManager.getInstance().isSpeakerEnabled());
//                }
//            }
//        };
        if (mCall == null) {
            //The incoming call no longer exists.
            Log.d("Couldn't find incoming call");
            return;
        }




    }

    private void lookupCurrentCall() {
        if (LinphoneManager.getLcIfManagerNotDestroyedOrNull() != null) {
            List<Call> calls = LinphoneUtils.getCalls(LinphoneManager.getLc());
            for (Call call : calls) {
                if (Call.State.IncomingReceived == call.getState()) {
                    mCall = call;
                    break;
                }
            }
        }
    }

}
