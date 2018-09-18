package org.linphone.call;

import java.util.Collections;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.RequiresApi;
import android.support.v4.content.LocalBroadcastManager;
import android.telecom.CallAudioState;
import android.telecom.Conference;
import android.telecom.Conferenceable;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.RemoteConference;
import android.telecom.TelecomManager;
import android.util.Log;

import org.linphone.LinphoneManager;
import org.linphone.activities.LinphoneActivity;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.Call;
import org.linphone.core.ConferenceParams;
import org.linphone.core.Core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;

import static android.telecom.Connection.STATE_ACTIVE;
import static android.telecom.Connection.STATE_DIALING;
import static android.telecom.Connection.STATE_HOLDING;
import static android.telecom.Connection.STATE_RINGING;

@RequiresApi(api = Build.VERSION_CODES.M)
public class LinphoneConnectionService extends ConnectionService {



    private static final String TAG = "LinphoneConnectionService";

    //Broadcast (or extras for EXT_TO_CS_CALL_ID) values used to communicate from other components to this ConnectionService implementation
    public static final String EXT_TO_CS_BROADCAST = "EXT_TO_CS_BROADCAST";
    public static final String EXT_TO_CS_ACTION = "EXT_TO_CS_ACTION";
    public static final String EXT_TO_CS_CALL_ID = "EXT_TO_CS_CALL_ID";
    public static final String EXT_TO_CS_HOLD_STATE = "EXT_TO_CS_HOLD_STATE";

    public static final int EXT_TO_CS_END_CALL = 1;
    public static final int EXT_TO_CS_HOLD_CALL = 2;
    public static final int EXT_TO_CS_ESTABLISHED = 3;

    //Broadcast (or extras for CS_TO_EXT_CALL_ID) values used to communicate from this ConnectionService implementation to other components
    public static final String CS_TO_EXT_BROADCAST = "CS_TO_EXT_BROADCAST";
    public static final String CS_TO_EXT_CALL_ID = "CS_TO_EXT_CALL_ID";
    public static final String CS_TO_EXT_ACTION = "CS_TO_EXT_ACTION";
    public static final String CS_TO_EXT_IS_CONFERENCE = "CS_TO_EXT_IS_CONFERENCE"; //true if conference

    public static final int CS_TO_EXT_REJECT = 1; //reject by me
    public static final int CS_TO_EXT_DISCONNECT = 2; //remote disconnect
    public static final int CS_TO_EXT_ANSWER = 3; //answer call
    public static final int CS_TO_EXT_ABORT = 4; //abort call
    public static final int CS_TO_EXT_HOLD = 5; //hold call
    public static final int CS_TO_EXT_UNHOLD = 6; //unhold call
    public static final int CS_TO_EXT_ADD_TO_CONF = 7; //add call to conference
    public static final int CS_TO_EXT_REMOVE_FROM_CONF = 8; //remove call from conference

    /**
     * Intent extra used to pass along the video state for a new test sipAudioCall.
     */
    public static final String EXTRA_HANDLE = "extra_handle";




    public LinphoneConnectionService() {
    }

    @SuppressLint("LongLogTag")
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate!");
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(
                mSipEventReceiver, new IntentFilter(EXT_TO_CS_BROADCAST));
        Log.d(TAG, "mSipEventReceiver: registered!");
    }


    private BroadcastReceiver mSipEventReceiver = new BroadcastReceiver() {
        @SuppressLint("LongLogTag")
        @Override
        public void onReceive(Context context, Intent intent) {

            int action = intent.getIntExtra(EXT_TO_CS_ACTION, -1);
            String callId = intent.getStringExtra(EXT_TO_CS_CALL_ID);

            Log.d(TAG, "mSipEventReceiver: callId:"+callId);
            MyConnection connection = null;
            for (MyConnection con : mCalls){
                if (callId.equalsIgnoreCase(con.getCallId())){
                    connection = con;
                    break;
                }
            }

            if (connection == null){
                return;
            }

            switch (action){
                case EXT_TO_CS_END_CALL:
                    connection.setDisconnected(new DisconnectCause(DisconnectCause.REJECTED));
                    destroyCall(connection);
                    connection.destroy();
                    break;

                case EXT_TO_CS_HOLD_CALL:
                    boolean holdState = intent.getBooleanExtra(EXT_TO_CS_HOLD_STATE, false);
                    if (holdState) {
                        connection.setOnHold();
                    } else {
                        setAsActive(connection);
                    }
                    break;

                case EXT_TO_CS_ESTABLISHED:
                    if(connection.getState()!= STATE_HOLDING){
                        setAsActive(connection);
                    }
                    break;

            }

        }
    };



    final class MyConnection extends Connection {
        private final boolean mIsIncoming;
        private boolean mIsActive = false;
        private String mCallId;

        MyConnection(boolean isIncoming) {
            mIsIncoming = isIncoming;
            // Assume all calls are video capable.
            int capabilities = getConnectionCapabilities();
            capabilities |= CAPABILITY_MUTE;
            capabilities |= CAPABILITY_SUPPORT_HOLD;
            capabilities |= CAPABILITY_HOLD;
            capabilities |= CAPABILITY_CAN_UPGRADE_TO_VIDEO;
            setConnectionCapabilities(capabilities);

        }

        public String getCallId() {
            return mCallId;
        }

        public void setCallId(String mCallId) {
            this.mCallId = mCallId;
        }

        public void setLocalActive(boolean isActive){
            mIsActive = isActive;
        }

        public boolean isLocalActive(){
            return mIsActive;
        }

        /** ${inheritDoc} */
        @Override
        public void onAbort() {
            destroyCall(this);
            destroy();

            sendLocalBroadcast(CS_TO_EXT_ABORT);
        }

        /** ${inheritDoc} */
        @Override
        public void onAnswer(int videoState) {
            if (mCalls.size() > 1) {
                holdInActiveCalls(this);
            }
            setAsActive(this);
            sendLocalBroadcast(CS_TO_EXT_ANSWER);
        }

        /** ${inheritDoc} */
        @Override
        public void onPlayDtmfTone(char c) {
            log("DTMF played: "+c);
            if (c == '1') {
                setDialing();
            }
        }

        /** ${inheritDoc} */
        @Override
        public void onStopDtmfTone() {
            log("DTMF stopped!");
        }

        /** ${inheritDoc} */
        @Override
        public void onDisconnect() {
            sendLocalBroadcast(CS_TO_EXT_DISCONNECT);
        }

        /** ${inheritDoc} */
        @Override
        public void onHold() {
            if(mCalls.size() > 1){
                performSwitchCall(this);
            }else{
                setOnHold();
                sendLocalBroadcast(CS_TO_EXT_HOLD);
            }
        }

        /** ${inheritDoc} */
        @Override
        public void onReject() {
            setDisconnected(new DisconnectCause(DisconnectCause.REJECTED));
            destroyCall(this);
            destroy();
            sendLocalBroadcast(CS_TO_EXT_REJECT);
            super.onReject();
        }

        /** ${inheritDoc} */
        @Override
        public void onUnhold() {
            setActive();
            sendLocalBroadcast(CS_TO_EXT_UNHOLD);
        }

        @Override
        public void onStateChanged(int state) {
            updateCallCapabilities();
            updateConferenceable();
        }

        public void sendLocalBroadcast(int action){
            Intent intent = new Intent(CS_TO_EXT_BROADCAST);
            intent.putExtra(CS_TO_EXT_ACTION, action);
            intent.putExtra(CS_TO_EXT_CALL_ID, getCallId());
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
        }



    }





    //Conference part

    private final class CallConference extends Conference {

        public CallConference(MyConnection a, MyConnection b) {
            super(mPhoneAccountHandle);
            log("conference: CallConference("+a.getAddress()+", "+b.getAddress()+")");
            updateConnectionCapabilities();
            setActive();

            addConnection(a);
            addConnection(b);
            setLocalActive(true);
            a.sendLocalBroadcast(CS_TO_EXT_ADD_TO_CONF);
            b.sendLocalBroadcast(CS_TO_EXT_ADD_TO_CONF);
            Bundle extra = new Bundle();
            extra.putBoolean("android.telecom.extra.DISABLE_ADD_CALL", true);

        }

        public void setLocalActive(boolean isActive){
            mIsActive = isActive;
            for (Connection con : this.getConnections()){
                MyConnection call = (MyConnection)con;
                call.setLocalActive(false);
            }
        }
        public boolean isLocalActive(){
            return mIsActive;
        }
        @Override
        public void onDisconnect() {
            setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
            for (Connection c : getConnections()) {
                MyConnection call = (MyConnection) c;
                call.onDisconnect();
            }
            mCallConference = null;
        }

        @Override
        public void onSeparate(Connection connection) {

            for (Connection c : getConnections()) {
                MyConnection call = (MyConnection) c;


                if (call.equals(connection)) {

                    //create the connection active
                    mCallConference.removeConnection(call);
                    call.sendLocalBroadcast(CS_TO_EXT_REMOVE_FROM_CONF);


                    //Particular case, when Conference will be deleted, the other call must be hold
                    call.setOnHold();
                    call.sendLocalBroadcast(CS_TO_EXT_HOLD);

                }
            }

            //Set conference on Hold if 2 or more people remains
            //destroy conference if not

            if (mCallConference != null && mCallConference.getConnections().size() <2){
                List<Connection> listConnection = mCallConference.getConnections();
                for(Connection con : listConnection){
                    if(con ==  listConnection.get(0) ){
                        mCallConference.removeConnection(con);
                        MyConnection call = (MyConnection)con;
                        ((MyConnection)con).sendLocalBroadcast(CS_TO_EXT_REMOVE_FROM_CONF);
                        setAsActive(call);

                    }else{
                        mCallConference.removeConnection(con);
                        MyConnection call = (MyConnection)con;
                        ((MyConnection)con).sendLocalBroadcast(CS_TO_EXT_REMOVE_FROM_CONF);
                        call.setOnHold();
                        call.sendLocalBroadcast(CS_TO_EXT_HOLD);
                    }

                }
                mCallConference.destroy();
                mCallConference = null;

            }else{
                mCallConference.setActive();
                mCallConference.setLocalActive(true);
            }

            updateConnectionCapabilities();
            updateCallCapabilities();
            updateConferenceable();
        }

        @Override
        public void onHold() {

            performSwitchCall(((MyConnection)this.getConnections().get(0)));
        }

        @Override
        public void onUnhold() {
            sendLocalBroadcast(CS_TO_EXT_UNHOLD);
            setLocalActive(true);
            setActive();
        }

        @Override
        public void onMerge(Connection connection) {
            log("conference: onMerge("+connection.getAddress()+")");
        }

        @Override
        public void onMerge() {
            log("conference: onMerge()");
        }

        @Override
        public void onSwap() {
            log("conference: onSwap()");
        }

        @Override
        public void onConnectionAdded(Connection connection) {
            log("conference: onConnectionAdded("+connection.getAddress()+")");
        }

        @Override
        public void onCallAudioStateChanged(CallAudioState state) {
            updateConnectionCapabilities();
        }

        private void sendLocalBroadcast(int action){
            Intent intent = new Intent(CS_TO_EXT_BROADCAST);
            intent.putExtra(CS_TO_EXT_ACTION, action);
            intent.putExtra(CS_TO_EXT_IS_CONFERENCE, true);
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
        }

        //utils functions
        protected final void updateConnectionCapabilities() {
            int newCapabilities = buildConnectionCapabilities();
            newCapabilities = applyConferenceTerminationCapabilities(newCapabilities);
            if (getConnectionCapabilities() != newCapabilities) {
                setConnectionCapabilities(newCapabilities);
            }
        }

        /**
         * Builds call capabilities common to all TelephonyConnections. Namely, apply IMS-based
         * capabilities.
         */
        protected int buildConnectionCapabilities() {
            int callCapabilities = 0;
            callCapabilities |= Connection.CAPABILITY_MUTE;
            callCapabilities |= Connection.CAPABILITY_SUPPORT_HOLD;
            log("conference: getState("+getState()+")");
            if (getState() == Connection.STATE_ACTIVE || getState() == STATE_HOLDING) {
                callCapabilities |= Connection.CAPABILITY_HOLD;

            }

            return callCapabilities;
        }

        private int applyConferenceTerminationCapabilities(int capabilities) {
            int currentCapabilities = capabilities;
            currentCapabilities |= Connection.CAPABILITY_MANAGE_CONFERENCE;
            return currentCapabilities;
        }
    }


    //End conference part








    @SuppressLint("LongLogTag")
    static void log(String msg) {
        Log.w(TAG, msg);
    }

    @TargetApi(Build.VERSION_CODES.O)
    @RequiresApi(api = Build.VERSION_CODES.N_MR1)
    @Override
    public Connection onCreateOutgoingConnection(
            PhoneAccountHandle connectionManagerAccount,
            final ConnectionRequest request) {

        PhoneAccountHandle accountHandle = request.getAccountHandle();
        ComponentName componentName = new ComponentName(getApplicationContext(), this.getClass());

        Bundle extras = request.getExtras();
        String gatewayPackage = extras.getString(TelecomManager.GATEWAY_PROVIDER_PACKAGE);
        Uri originalHandle = extras.getParcelable(TelecomManager.GATEWAY_ORIGINAL_ADDRESS);
        if (accountHandle != null && componentName.equals(accountHandle.getComponentName())) {

            // Get the stashed intent extra that determines if this is a video sipAudioCall or audio sipAudioCall.

            Uri providedHandle = request.getAddress();

            String callerName = null;

            final MyConnection connection = new MyConnection(false);




            Address address = LinphoneManager.getLc().getCurrentCall().getRemoteAddress();
            LinphoneContact contact = ContactsManager.getInstance().findContactFromAddress(address);
            if (contact != null) {
                callerName = contact.getFullName();
            }
            if (callerName != null) {
                connection.setCallerDisplayName(callerName, TelecomManager.PRESENTATION_ALLOWED);
            }
//            setAddress(connection, providedHandle);
            setAddress(connection, providedHandle);
            String callId = LinphoneManager.getLc().getCurrentCall().getCallLog().getCallId();
            connection.setCallId(callId);
            connection.setAudioModeIsVoip(true);

            //            connection.setAudioRoute(CallAudioState.ROUTE_EARPIECE);
            connection.setDialing();

            addCall(connection);


            return connection;
        } else {
            return Connection.createFailedConnection(new DisconnectCause(DisconnectCause.ERROR,
                    "Invalid inputs: " + accountHandle + " " + componentName));
        }
    }


    @Override
    public void onCreateOutgoingConnectionFailed(
            PhoneAccountHandle connectionManagerAccount,
            final ConnectionRequest request) {

        PhoneAccountHandle accountHandle = request.getAccountHandle();
        ComponentName componentName = new ComponentName(getApplicationContext(), this.getClass());
//        Log.d(TAG, "onCreateOutgoingConnection => phoneAccountHandle: "+connectionManagerAccount+" | request: "+request);

        Bundle extras = request.getExtras();
        String gatewayPackage = extras.getString(TelecomManager.GATEWAY_PROVIDER_PACKAGE);
        Uri originalHandle = extras.getParcelable(TelecomManager.GATEWAY_ORIGINAL_ADDRESS);
        log("gateway package [" + gatewayPackage + "], original handle [" +
                originalHandle + "]");

//        Log.d(TAG, "onCreateOutgoingConnection initiationType: "+extras.getInt(Messages.TAG_CALL_INITIATION_TYPE, -1));

        if (accountHandle != null && componentName.equals(accountHandle.getComponentName())) {
            final MyConnection connection = new MyConnection(false);
            // Get the stashed intent extra that determines if this is a video sipAudioCall or audio sipAudioCall.
            Uri providedHandle = request.getAddress();
            Bundle b = extras.getBundle(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS);

            log("set address: "+request.getAddress());
            setAddress(connection, providedHandle);
            connection.setAudioModeIsVoip(true);
            connection.setRinging();
            addCall(connection);
        }
    }



    @RequiresApi(api = Build.VERSION_CODES.N_MR1)
    @Override
    public Connection onCreateIncomingConnection(
            PhoneAccountHandle connectionManagerAccount,
            final ConnectionRequest request) {
        PhoneAccountHandle accountHandle = request.getAccountHandle();
        ComponentName componentName = new ComponentName(getApplicationContext(), this.getClass());

//        log("onCreateIncomingConnection conManager: "+connectionManagerAccount.getId()+" | componentName: "+connectionManagerAccount.getComponentName());
//        log("onCreateIncomingConnection request: "+request.getAccountHandle().getId()+" | componentName: "+request.getAccountHandle().getComponentName());


        if (accountHandle != null && componentName.equals(accountHandle.getComponentName())) {
            mPhoneAccountHandle = accountHandle;
            final MyConnection connection = new MyConnection(true);
            // Get the stashed intent extra that determines if this is a video sipAudioCall or audio sipAudioCall.
            Bundle extras = request.getExtras();
            String callId = extras.getString(EXT_TO_CS_CALL_ID);
            connection.setCallId(callId);

            Uri providedHandle = Uri.parse(extras.getString(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS));

            Bundle b = extras.getBundle(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS);
            if (b != null) {
                String callerName = b.getString(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS);
                connection.setCallerDisplayName(callerName, TelecomManager.PRESENTATION_ALLOWED);
            }

            log("request.getAddress: "+request.getAddress());
            log("request.setCallId: "+callId);
            setAddress(connection, providedHandle);
            connection.setAudioModeIsVoip(true);
            connection.setRinging();
            addCall(connection);

            return connection;
        } else {
            return Connection.createFailedConnection(new DisconnectCause(DisconnectCause.ERROR,
                    "Invalid inputs: " + accountHandle + " " + componentName));
        }
    }

    @Override
    public void onConference(Connection a, Connection b) {
        if (mCallConference ==null){
            mCallConference = new CallConference((MyConnection) a, (MyConnection) b);
            addConference(mCallConference);

        }else{
            MyConnection call = (MyConnection)a;
            mCallConference.addConnection(call);
            mCallConference.updateConnectionCapabilities();

            call.sendLocalBroadcast(CS_TO_EXT_ADD_TO_CONF);
            mCallConference.setActive();

        }

    }

    @Override
    public void onRemoteConferenceAdded(RemoteConference remoteConference) {

    }

    private PhoneAccountHandle mPhoneAccountHandle = null;
    private final List<MyConnection> mCalls = new ArrayList<>();
    private ListIterator<MyConnection> mCallsIterator = null;
    private CallConference mCallConference = null;
    private final Handler mHandler = new Handler();
    private boolean mIsActive;
    private MyConnection mActiveCall = null;
    private MyConnection mLastHeldCall = null;
    @Override
    public boolean onUnbind(Intent intent) {
        log("onUnbind");
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(
                mSipEventReceiver);
        return super.onUnbind(intent);
    }

    private void addCall(MyConnection connection) {
//        Bundle extras = new Bundle();
//        extras.putBoolean("android.telecom.extra.DISABLE_ADD_CALL", true);
//        connection.setExtras(extras);

        mCalls.add(connection);
//        mCallsIterator=mCalls.listIterator();
        updateCallCapabilities();
        updateConferenceable();
    }

    private void destroyCall(MyConnection connection) {
        mCalls.remove(connection);
        updateCallCapabilities();
        updateConferenceable();
    }

    private void updateConferenceable() {
        List<Connection> freeConnections = new ArrayList<>();
        freeConnections.addAll(mCalls);
        for (int i = 0; i < freeConnections.size(); i++) {
            if (freeConnections.get(i).getConference() != null) {
                freeConnections.remove(i);
            }
        }
        for (int i = 0; i < freeConnections.size(); i++) {
            Connection c = freeConnections.remove(i);
            c.setConferenceableConnections(freeConnections);
            freeConnections.add(i, c);
        }
    }


    private void updateCallCapabilities(){
        for (MyConnection connection : mCalls){
            connection.setConnectionCapabilities(getCallCapabilities(connection, mCalls.size()));
        }
    }

    private int getCallCapabilities(Connection connection, int totalCall){
        int callCapabilities = 0;
        callCapabilities |= Connection.CAPABILITY_MUTE;
        callCapabilities |= Connection.CAPABILITY_SUPPORT_HOLD;

        //hold capability for only single call
        if (totalCall == 1){
            if (connection.getState() == Connection.STATE_ACTIVE || connection.getState() == STATE_HOLDING) {
                log("getCallCapabilities(HOLD)");
                callCapabilities |= Connection.CAPABILITY_HOLD;
            }
        }

        if (totalCall > 1){
            callCapabilities |= Connection.CAPABILITY_MERGE_CONFERENCE;
            callCapabilities |= Connection.CAPABILITY_SEPARATE_FROM_CONFERENCE;
            callCapabilities |= Connection.CAPABILITY_SWAP_CONFERENCE;
            callCapabilities |= Connection.CAPABILITY_MANAGE_CONFERENCE;
        }

        return callCapabilities;
    }

    private void holdInActiveCalls(MyConnection activeCall){
        for (MyConnection con : mCalls){
            if (!Objects.equals(con, activeCall)){
                if(con.getConference() == null){
                    con.setOnHold();
                    con.setLocalActive(false);
                    con.sendLocalBroadcast(CS_TO_EXT_HOLD);
                }else if (((CallConference) con.getConference()).isLocalActive()){
                    mCallConference = (CallConference) con.getConference();
                    mCallConference.setLocalActive(false);
                    mCallConference.setOnHold();
                    mCallConference.sendLocalBroadcast(CS_TO_EXT_HOLD);
                }
            }
        }
    }

    private void setAsActive(MyConnection connection){
//        MyConnection futureActive=null;
//
//        if(mCalls.size() >= 2 && connection.getState() == STATE_RINGING){
//            futureActive = getFutureActive(connection);
//        }else if (mCalls.size() >= 2){
//            futureActive = mLastHeldCall;
//        }

        for (MyConnection con : mCalls){
            //Set mLastHeldCall
//            if(getActive() != null && con == getActive() && mCalls.size() >= 2){
//                mLastHeldCall=con;
//            }


            if (Objects.equals(con, connection) && con.getConference() == null){
                con.setLocalActive(true);
                con.setActive();
            } else if (Objects.equals(con, connection) && con.getConference() != null){
                ((CallConference)con.getConference()).setLocalActive(true);
                con.getConference().setActive();
            }/* else if (!Objects.equals(con, connection) && con.getConference() == null){
                con.setLocalActive(false);
                con.setOnHold();
            } else if (!Objects.equals(con, connection) && con.getConference() != null){
                ((CallConference)con.getConference()).setLocalActive(false);
                con.getConference().setOnHold();

            }*/
        }
//        if(mCalls.size() >= 2 && futureActive != null) {
//            futureActive.setLocalActive(false);
//            futureActive.setOnHold();
//        }

    }

//    private MyConnection getFutureActive(MyConnection activeConnection){
//
//        MyConnection futureActive;
//        mCallsIterator=mCalls.listIterator();
//        while (mCallsIterator.hasNext() && mCallsIterator.nextIndex()<=getPosition(activeConnection)){
//            mCallsIterator.next();
//        }
//
//
//        if (mCallsIterator.hasNext()){
//            futureActive=mCallsIterator.next();
//            if(mCallsIterator.hasNext()){
//                mLastHeldCall=mCallsIterator.next();
//            }else{
//                mLastHeldCall=mCalls.get(0);
//            }
//        }else{
//            futureActive=mCalls.get(0);
//            mLastHeldCall=mCalls.get(1);
//        }
//        return futureActive;
//    }

    private MyConnection getInActive(){
        for (MyConnection con : mCalls){
            if ((!con.isLocalActive()) && con.getConference()==null){
                return con;
            }
        }
        for (MyConnection con : mCalls){
            if ((con.getConference() !=null) && !((CallConference)con.getConference()).isLocalActive() ){
                return con;
            }
        }


        throw new NullPointerException("No inactive call found!");
    }


    @SuppressLint("LongLogTag")
    private void performSwitchCall(MyConnection activeConnection){
        Log.d(TAG, "performSwitchCall :: activeConnection: "+activeConnection.getAddress());


        MyConnection futureActive=getInActive();


        //unhold in active call

        if(futureActive.getConference() == null &&  futureActive.getState()== STATE_RINGING ){
            futureActive.sendLocalBroadcast(CS_TO_EXT_ANSWER);
        }else if (futureActive.getConference() == null) {
            futureActive.sendLocalBroadcast(CS_TO_EXT_UNHOLD);
        }else{
            mCallConference.sendLocalBroadcast(CS_TO_EXT_UNHOLD);
        }

        holdInActiveCalls(futureActive);
        setAsActive(futureActive);

        updateCallCapabilities();
        updateConferenceable();

    }

    @SuppressLint("LongLogTag")
    private void setAddress(Connection connection, Uri address) {
        connection.setAddress(address, TelecomManager.PRESENTATION_ALLOWED);
        Log.d(TAG, "setAddress: "+address);
//        if ("5551234".equals(address.getSchemeSpecificPart())) {
//            connection.setCallerDisplayName("Hello World", TelecomManager.PRESENTATION_ALLOWED);
//        }
    }






}
