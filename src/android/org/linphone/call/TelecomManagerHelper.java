package org.linphone.call;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.linphone.LinphoneManager;
import org.linphone.LinphoneService;
import org.linphone.LinphoneUtils;
import org.linphone.R;
import org.linphone.activities.LinphoneActivity;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.Call;
import org.linphone.core.CallParams;
import org.linphone.core.Conference;
import org.linphone.core.ConferenceParams;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.Reason;
import org.linphone.mediastream.Log;

import java.lang.invoke.MethodHandles;
import java.util.List;

import static org.linphone.call.LinphoneConnectionService.CS_TO_EXT_ACTION;
import static org.linphone.call.LinphoneConnectionService.EXT_TO_CS_END_CALL;
import static org.linphone.call.LinphoneConnectionService.EXT_TO_CS_HOLD_CALL;

public class TelecomManagerHelper {
    private Call mCall = null;
    private String mCallId = null;
    private TelecomManager telecomManager;
    private boolean alreadyAcceptedOrDeniedCall;
    private PhoneAccountHandle phoneAccountHandle;
    private final int INCALL = 1;
    private final int OUTGOING = 2;
    private final int END = 3;
    private final int CURRENT = 4;
    private final int HELD = 5;
    private final boolean PAUSE = false;
    private final boolean RESUME = true;
    private CoreListenerStub mListener;
    public static String TAG = "TelecomManagerHelper";
    private Conference mConference = null;

    //Initiates the telecomManager and dependencies which are needed to handle calls.
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public TelecomManagerHelper() {
        telecomManager = (TelecomManager) LinphoneManager.getInstance().getContext().getSystemService(Context.TELECOM_SERVICE);
        if (LinphoneManager.getInstance().getLinPhoneAccount() == null) {
            LinphoneManager.getInstance().setLinPhoneAccount();
        }
        phoneAccountHandle = LinphoneManager.getInstance().getLinPhoneAccount().getAccountHandler();
    }


    @RequiresApi(api = Build.VERSION_CODES.M)
    public void startIncall() {
        alreadyAcceptedOrDeniedCall = false;

        lookupCall(INCALL);


        if (mCall == null) {
            //The incoming call no longer exists.
            Log.d("Couldn't find incoming call");
            return;
        }
        setListenerIncall(mCall);

        String strContact=null;
        Address address = mCall.getRemoteAddress();
        LinphoneContact contact = ContactsManager.getInstance().findContactFromAddress(address);
        if (contact != null) {
            strContact = contact.getFullName();
        }
        String strAddress = address.asStringUriOnly();

        Bundle extras = new Bundle();
        final Bundle b = new Bundle();


        extras.putString(LinphoneConnectionService.EXT_TO_CS_CALL_ID, mCall.getCallLog().getCallId());
        extras.putString(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, strAddress);

        if (strContact != null) {
            b.putString(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS, strContact);
            extras.putBundle(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS, b);
        }


        if (LinphoneManager.getLc().getCalls().length == 2) {
            Call nextCall = mCall;
            //At this point, mCall gets the current paused call if it is
            lookupCall(HELD);
            if (nextCall != mCall) {
                //If mCall differs from nextCall, 1st call is held
                mCallId=mCall.getCallLog().getCallId();

                //Unhold current call to allow ConnectionService to handle the 2nd call and stay beyond LinphoneActivity
                sendToCS(EXT_TO_CS_HOLD_CALL);
                pauseOrResumeCall(mCall, RESUME);
            }

        }
        telecomManager.addNewIncomingCall(phoneAccountHandle, extras);
        registerCallScreenReceiver();

    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void startOutgoingCall() {


        lookupCall(OUTGOING);

        if (mCall == null) {
            //The incoming call no longer exists.
            Log.d("Couldn't find incoming call");
            return;
        }
        setListenerOutgoing(mCall);

        Bundle extras = new Bundle();
        extras.putInt(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, VideoProfile.STATE_BIDIRECTIONAL);
//        extras.putInt(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, VideoProfile.STATE_AUDIO_ONLY);
        //                    b.putString(TelecomManager.EXTRA_CALL_BACK_NUMBER, "lule@sip.linphone.org");
        //                    b.putString(TelecomManager.GATEWAY_ORIGINAL_ADDRESS, "lule@sip.linphone.org");
        //                    b.putInt(TelecomManager.PRESENTATION_ALLOWED, getCallId());
        if (phoneAccountHandle != null) {
            extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle);
        }
        if (ActivityCompat.checkSelfPermission(LinphoneManager.getInstance().getContext(), Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }

        Address address = mCall.getRemoteAddress();
        String strAddress = address.asStringUriOnly();


        telecomManager.placeCall(Uri.parse(strAddress), extras);

        registerCallScreenReceiver();
    }



//    @RequiresApi(api = Build.VERSION_CODES.M)
//    public void stopCall (){
//        lookupCall(END);
//
//        if (mCall == null) {
//            //The call no longer exists.
//            Log.d("Couldn't find call");
//            return;
//        }
//        sendToCS(LinphoneConnectionService.EXT_TO_CS_END_CALL);
//        if (LinphoneManager.getLc().getCalls().length == 0) {
//            unRegisterCallScreenReceiver();
//        }
//        LinphoneManager.getLc().terminateCall(mCall);
//
//    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void stopCallById (String callId){
        mCallId = callId;
        if (mCall == null) {
            //The call no longer exists.
            Log.d("Couldn't find call");
            return;
        }
        sendToCS(LinphoneConnectionService.EXT_TO_CS_END_CALL);
        if (LinphoneManager.getLc().getCalls().length == 0) {
            unRegisterCallScreenReceiver();
            LinphoneActivity.instance().resetClassicMenuLayoutAndGoBackToCallIfStillRunning();
        }
        LinphoneManager.getLc().terminateCall(mCall);

    }



    private void setListenerIncall (Call call){
        mListener = new CoreListenerStub() {
            @RequiresApi(api = Build.VERSION_CODES.M)
            @Override
            public void onCallStateChanged(Core lc, Call call, Call.State state, String message) {
                if (call == mCall && Call.State.End == state) {
//                    stopCall();
                    stopCallById(call.getCallLog().getCallId());

                }
                if (state == Call.State.StreamsRunning) {
                    Log.e("CallIncommingActivity - onCreate -  State.StreamsRunning - speaker = " + LinphoneManager.getInstance().isSpeakerEnabled());
                    // The following should not be needed except some devices need it (e.g. Galaxy S).
//                    LinphoneManager.getInstance().enableSpeaker(LinphoneManager.getInstance().isSpeakerEnabled());
                }
                if (call == mCall && (Call.State.PausedByRemote == state) ) {
//                    mCall= call;
                    Toast.makeText(LinphoneManager.getInstance().getContext(),"Vous êtes en attente",Toast.LENGTH_LONG).show();
                }
            }
        };

        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.addListener(mListener);
        }
    }

    private void setListenerOutgoing (Call call){
        mListener = new CoreListenerStub() {
            @RequiresApi(api = Build.VERSION_CODES.M)
            @Override
            public void onCallStateChanged(Core lc, Call call, Call.State state, String message) {
                if (call == mCall && Call.State.Connected == state && call.getDir() == Call.Dir.Outgoing) {
//                if (call == mCall && Call.State.Connected == state) {
                    if (!LinphoneActivity.isInstanciated()) {
                        return;
                    }
                    mCallId=call.getCallLog().getCallId();
//                    LinphoneActivity.instance().startIncallActivity(mCall);
                    sendToCS(LinphoneConnectionService.EXT_TO_CS_ESTABLISHED);
                    return;
                } else if (state == Call.State.Error) {


                    // Convert Core message for internalization
                    if (call.getErrorInfo().getReason() == Reason.Declined) {
                        displayCustomToast(LinphoneManager.getInstance().getContext().getString(R.string.error_call_declined), Toast.LENGTH_SHORT);
                        stopCallById(call.getCallLog().getCallId());
                    } else if (call.getErrorInfo().getReason() == Reason.NotFound) {
                        displayCustomToast(LinphoneManager.getInstance().getContext().getString(R.string.error_user_not_found), Toast.LENGTH_SHORT);
                        stopCallById(call.getCallLog().getCallId());
                    } else if (call.getErrorInfo().getReason() == Reason.NotAcceptable) {
                        displayCustomToast(LinphoneManager.getInstance().getContext().getString(R.string.error_incompatible_media), Toast.LENGTH_SHORT);
                        stopCallById(call.getCallLog().getCallId());
                    } else if (call.getErrorInfo().getReason() == Reason.Busy) {
                        displayCustomToast(LinphoneManager.getInstance().getContext().getString(R.string.error_user_busy), Toast.LENGTH_SHORT);
                        stopCallById(call.getCallLog().getCallId());
                    } else if (message != null) {
                        displayCustomToast((R.string.error_unknown) + " - " + message, Toast.LENGTH_SHORT);
                        stopCallById(call.getCallLog().getCallId());
                    }
                }else if (state == Call.State.End) {
//                    stopCall();
                    stopCallById(call.getCallLog().getCallId());
                    // Convert Core message for internalization
                    if (call.getErrorInfo().getReason() == Reason.Declined) {
                        displayCustomToast(LinphoneManager.getInstance().getContext().getString(R.string.error_call_declined), Toast.LENGTH_SHORT);

                    }
                } else if (call == mCall && (Call.State.PausedByRemote == state) ) {
//                    mCall= call;
                    Toast.makeText(LinphoneManager.getInstance().getContext(),"Vous êtes en attente",Toast.LENGTH_LONG).show();
                }

                if (LinphoneManager.getLc().getCallsNb() == 0) {
//                    finish();
                    return;
                }
            }
        };

        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.addListener(mListener);
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.M)
    private void sendToCS(int action){
        Intent intent = new Intent(LinphoneConnectionService.EXT_TO_CS_BROADCAST);
        intent.putExtra(LinphoneConnectionService.EXT_TO_CS_ACTION, action);
        intent.putExtra(LinphoneConnectionService.EXT_TO_CS_CALL_ID, mCallId);
//        intent.putExtra(LinphoneConnectionService.EXT_TO_CS_CALL_ID, mCall.getCallLog().getCallId());
//        if(action == LinphoneConnectionService.EXT_TO_CS_HOLD_CALL && getCallById(mCallId).getState()== Call.State.Paused){
//            intent.putExtra(LinphoneConnectionService.EXT_TO_CS_HOLD_STATE, true);
//        }
        LocalBroadcastManager.getInstance(LinphoneManager.getInstance().getContext()).sendBroadcast(intent);
    }

    private void lookupCall(int type) {

        if (LinphoneManager.getLcIfManagerNotDestroyedOrNull() != null) {
            List<Call> calls = LinphoneUtils.getCalls(LinphoneManager.getLc());

            for (Call call : calls) {
                switch (type) {
                    case INCALL:
                        if (Call.State.IncomingReceived == call.getState()) {
                            mCall = call;
                            break;
                        }

                    case END:
                        if (call.getState() == Call.State.Released) {
//                        if ((call.getState() == Call.State.End) || (call.getState() == Call.State.Error )|| (call.getState() == Call.State.Released)) {
                            mCall = call;
                            break;
                        }
                    case OUTGOING:
                        if ((Call.State.OutgoingInit == call.getState()) || (Call.State.OutgoingProgress == call.getState())|| (Call.State.OutgoingRinging == call.getState())) {
                            mCall = call;
                            break;
                        }
                    case CURRENT:
                        if (Call.State.StreamsRunning == call.getState()) {
                            mCall = call;
                            break;
                        }
                    case HELD:
                        if (Call.State.Paused == call.getState()) {
                            mCall = call;
                            break;
                        }

                }
            }
        }
    }

    //BroadcastReceiver which role is to receive callback from user's action on incall/current call screen buttons
    //and provide actions
    private BroadcastReceiver mCallScreenEventsReceiver = new BroadcastReceiver() {
        @RequiresApi(api = Build.VERSION_CODES.P)
        @Override
        public void onReceive(Context context, Intent intent) {
            int action = intent.getIntExtra(LinphoneConnectionService.CS_TO_EXT_ACTION, -1);
            String callId = intent.getStringExtra(LinphoneConnectionService.CS_TO_EXT_CALL_ID);
            boolean isConference = intent.getBooleanExtra(LinphoneConnectionService.CS_TO_EXT_IS_CONFERENCE, false);

            if(!isConference){
                mCall = getCallById(callId);
            }
            android.util.Log.d(TAG, "callScreenEventsReceiver: action: "+action+" | callId: "+callId);
            if (mCall == null){
                return;
            }
            switch (action){
                case LinphoneConnectionService.CS_TO_EXT_ANSWER:
                    answer();
                    break;
                case LinphoneConnectionService.CS_TO_EXT_REJECT:
                    stopCallById(callId);
//                    stopCall();
                    break;
                case LinphoneConnectionService.CS_TO_EXT_DISCONNECT:
                    stopCallById(callId);
                    break;
                case LinphoneConnectionService.CS_TO_EXT_ABORT:
                    stopCallById(callId);
                    break;
                case LinphoneConnectionService.CS_TO_EXT_HOLD:
                    if (isConference){
                        pauseOrResumeConference(PAUSE);
                    }else{
                        pauseOrResumeCall(mCall, PAUSE);
                    }
                    break;
                case LinphoneConnectionService.CS_TO_EXT_UNHOLD:
                    if (isConference){
                        pauseOrResumeConference(RESUME);
                    }else{
                        pauseOrResumeCall(mCall, RESUME);
                    }
                    break;
                case LinphoneConnectionService.CS_TO_EXT_ADD_TO_CONF:
                    if (mConference == null){
                        startConference();
                    }
                    LinphoneManager.getLc().addToConference(getCallById(callId));
                    break;
                case LinphoneConnectionService.CS_TO_EXT_REMOVE_FROM_CONF:

                    Call temp = getCallById(callId);
                    LinphoneManager.getLc().removeFromConference(temp);

//                    Call[] calls = LinphoneManager.getLc().getCalls();


////                    LinphoneManager.getLc().pauseAllCalls();
//
//                    pauseOrResumeCall(temp, RESUME);
//                    temp1 = LinphoneManager.getLc().getConferenceSize();
//                    if (LinphoneManager.getLc().getConferenceSize() == 1) {
//                        for (Call x:calls){
//                            if(!(x.getCallLog().getCallId().equals(callId))){
//                                Call.State etat = x.getState();
//                                String test = x.getCallLog().getCallId();
//                                Conference tempconf = x.getConference();
//                                pauseOrResumeCall(x, PAUSE);
//                                pauseOrResumeConference(PAUSE);
//                            }
//                        }
//                        mConference = null;
//                    }
                    pauseOrResumeCall(temp, PAUSE);

                    break;
            }
        }
    };


    public Call getCallById(String callId) {
        if (LinphoneManager.getLcIfManagerNotDestroyedOrNull() != null) {
            List<Call> calls = LinphoneUtils.getCalls(LinphoneManager.getLc());
            for (Call call : calls) {
                if (callId.equals(call.getCallLog().getCallId())) {
                    return call;
                }
            }
        }
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void registerCallScreenReceiver(){
        android.util.Log.d(TAG, "registerCallScreenReceiver...");
        LocalBroadcastManager.getInstance(LinphoneManager.getInstance().getContext()).registerReceiver(
                mCallScreenEventsReceiver, new IntentFilter(LinphoneConnectionService.CS_TO_EXT_BROADCAST));
    }

    private void unRegisterCallScreenReceiver(){
        LocalBroadcastManager.getInstance(LinphoneManager.getInstance().getContext()).unregisterReceiver(mCallScreenEventsReceiver);
        android.util.Log.d(TAG, "unRegisterCallScreenReceiver...");
    }



    private void answer() {
        if (alreadyAcceptedOrDeniedCall) {
            return;
        }
        alreadyAcceptedOrDeniedCall = true;

        CallParams params = LinphoneManager.getLc().createCallParams(mCall);

        boolean isLowBandwidthConnection = !LinphoneUtils.isHighBandwidthConnection(LinphoneService.instance().getApplicationContext());

        if (params != null) {
            params.enableLowBandwidth(isLowBandwidthConnection);
        }else {
            Log.e("Could not create call params for call");
        }

        if (params == null || !LinphoneManager.getInstance().acceptCallWithParams(mCall, params)) {
            // the above method takes care of Samsung Galaxy S
            Toast.makeText(LinphoneManager.getInstance().getContext(), R.string.couldnt_accept_call, Toast.LENGTH_LONG).show();
        } else {
            if (!LinphoneActivity.isInstanciated()) {
                return;
            }
            LinphoneManager.getInstance().routeAudioToReceiver();
            LinphoneManager.getLc().acceptCall(mCall);

        }
    }
    public void displayCustomToast(final String message, final int duration) {
        LayoutInflater inflater = LinphoneActivity.instance().getLayoutInflater();
        View layout = inflater.inflate(R.layout.toast, (ViewGroup) LinphoneActivity.instance().findViewById(R.id.toastRoot));

        TextView toastText = (TextView) layout.findViewById(R.id.toastMessage);
        toastText.setText(message);

        final Toast toast = new Toast(LinphoneManager.getInstance().getContext());
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.setDuration(duration);
        toast.setView(layout);
        toast.show();
    }

    private void pauseOrResumeCall(Call call, Boolean resume) {
        Core lc = LinphoneManager.getLc();

        if (call != null && LinphoneManager.getLc().getCurrentCall() == call && !resume) {
            lc.pauseCall(call);
//            if (isVideoEnabled(LinphoneManager.getLc().getCurrentCall())) {
//                isVideoCallPaused = true;
//            }
//            pause.setImageResource(R.drawable.pause_big_over_selected);
        } else if (call != null) {
            Call.State test = call.getState();
            if ((call.getState() == Call.State.Paused)&& resume) {
//            if ((call.getState() == Call.State.Paused || call.getState() == Call.State.Pausing || call.getState() == Call.State.Updating)&& resume) {
                lc.resumeCall(call);
//                if (isVideoCallPaused) {
//                    isVideoCallPaused = false;
//                }
//                pause.setImageResource(R.drawable.pause_big_default);
            }
        }
    }

    public void startConference(){
        ConferenceParams mConfParams= LinphoneManager.getLc().createConferenceParams();
        mConference= LinphoneManager.getLc().createConferenceWithParams(mConfParams);
    }




    public void pauseOrResumeConference(boolean resume) {
        Core lc = LinphoneManager.getLc();
            if (lc.isInConference() && !resume) {
                lc.leaveConference();
            } else if (resume){
                lc.enterConference();
            }
//        refreshCallList(getResources());
    }

}
