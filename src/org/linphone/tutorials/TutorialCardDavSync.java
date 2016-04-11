package org.linphone.tutorials;

import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;

import org.linphone.R;
import org.linphone.UIThreadDispatcher;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneAuthInfo;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneCallStats;
import org.linphone.core.LinphoneChatMessage;
import org.linphone.core.LinphoneChatRoom;
import org.linphone.core.LinphoneContent;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCore.EcCalibratorStatus;
import org.linphone.core.LinphoneCore.GlobalState;
import org.linphone.core.LinphoneCore.LogCollectionUploadState;
import org.linphone.core.LinphoneCore.RegistrationState;
import org.linphone.core.LinphoneCore.RemoteProvisioningState;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneCoreListener;
import org.linphone.core.LinphoneEvent;
import org.linphone.core.LinphoneFriend;
import org.linphone.core.LinphoneFriendList;
import org.linphone.core.LinphoneFriendList.LinphoneFriendListListener;
import org.linphone.core.LinphoneInfoMessage;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.PublishState;
import org.linphone.core.SubscriptionState;
import org.linphone.mediastream.Log;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class TutorialCardDavSync extends Activity implements OnClickListener, LinphoneCoreListener, LinphoneFriendListListener {
	private EditText username, password, ha1, server;
	private Button synchronize;
	private TextView logs;

	private Timer timer;
	
	private LinphoneCore lc;
	private LinphoneFriendList lfl;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.tuto_carddav);
		
		username = (EditText) findViewById(R.id.carddav_username);
		password = (EditText) findViewById(R.id.carddav_pwd);
		ha1 = (EditText) findViewById(R.id.carddav_ha1);
		server = (EditText) findViewById(R.id.carddav_server);
		logs = (TextView) findViewById(R.id.carddav_events);
		
		synchronize = (Button) findViewById(R.id.carddav_synchronize);
		synchronize.setOnClickListener(this);

		LinphoneCoreFactory.instance().setDebugMode(true, "CardDAV sync tutorial");
		try {
			lc = LinphoneCoreFactory.instance().createLinphoneCore(this, this);
			TimerTask lTask = new TimerTask() {
				@Override
				public void run() {
					UIThreadDispatcher.dispatch(new Runnable() {
						@Override
						public void run() {
							if (lc != null) {
								lc.iterate();
							}
						}
					});
				}
			};
			timer = new Timer("Linphone scheduler");
			timer.schedule(lTask, 0, 20);
			
			lfl = lc.createLinphoneFriendList();
			lc.addFriendList(lfl);
			
			LinphoneFriend lf = LinphoneCoreFactory.instance().createLinphoneFriend("sip:ghislain@sip.linphone.org");
			lf.setName("Ghislain");
			lfl.addLocalFriend(lf); // This is a local friend, it won't be sent to the CardDAV server and will be removed at the next synchronization
		} catch (LinphoneCoreException e) {
			Log.e(e);
		}
	}
	
	@Override
	protected void onDestroy() {
		try {
			lc.removeFriendList(lfl);
		} catch (LinphoneCoreException e) {
			Log.e(e);
		}
		timer.cancel();
		lc.destroy();
		super.onDestroy();
	}

	@Override
	public void onClick(View v) {
		String serverUrl = server.getText().toString();
		String serverDomain = serverUrl.replace("http://", "").replace("https://", "").split("/")[0]; // We just want the domain name
		LinphoneAuthInfo authInfo = LinphoneCoreFactory.instance().createAuthInfo(username.getText().toString(), null, password.getText().toString(), ha1.getText().toString(), "SabreDAV", serverDomain);
		lc.addAuthInfo(authInfo);
		
		lfl.setUri(serverUrl);
		lfl.setListener(this);
		synchronize.setEnabled(false);
		lfl.synchronizeFriendsFromServer();
	}
	
	private void myLog(String msg) {
		Log.d(msg);
		logs.setText(logs.getText().toString() + "\r\n" + msg);
	}

	@Override
	public void onLinphoneFriendCreated(LinphoneFriendList list,
			LinphoneFriend lf) {
		// TODO Auto-generated method stub
		String msg = "Friend created " + lf.getAddress();
		myLog(msg);
		
		LinphoneFriend[] friends = list.getFriendList();
		String msg2 = "There are " + friends.length + (friends.length > 1 ? " friends" : " friend") + " in the list";
		myLog(msg2);
	}

	@Override
	public void onLinphoneFriendUpdated(LinphoneFriendList list,
			LinphoneFriend newFriend, LinphoneFriend oldFriend) {
		// TODO Auto-generated method stub
		String msg = "Friend updated " + newFriend.getAddress();
		myLog(msg);
		
		LinphoneFriend[] friends = list.getFriendList();
		String msg2 = "There are " + friends.length + (friends.length > 1 ? " friends" : " friend") + " in the list";
		myLog(msg2);
	}

	@Override
	public void onLinphoneFriendDeleted(LinphoneFriendList list,
			LinphoneFriend lf) {
		// TODO Auto-generated method stub
		String msg = "Friend removed " + lf.getAddress();
		myLog(msg);
		
		LinphoneFriend[] friends = list.getFriendList();
		String msg2 = "There are " + friends.length + (friends.length > 1 ? " friends" : " friend") + " in the list";
		myLog(msg2);
	}

	@Override
	public void onLinphoneFriendSyncStatusChanged(LinphoneFriendList list,
			org.linphone.core.LinphoneFriendList.State status, String message) {
		// TODO Auto-generated method stub
		String msg = "Sync status changed: " + status.toString() + " (" + message + ")";
		myLog(msg);
		if (status != LinphoneFriendList.State.SyncStarted) {
			synchronize.setEnabled(true);
		}
	}

	@Override
	public void friendListCreated(LinphoneCore lc, LinphoneFriendList list) {
		// TODO Auto-generated method stub
		String msg = "Friend List added";
		myLog(msg);
		
		LinphoneFriendList[] lists = lc.getFriendLists();
		String msg2 = "There are " + lists.length + (lists.length > 1 ? " lists" : " list") + " in the core";
		myLog(msg2);
	}

	@Override
	public void friendListRemoved(LinphoneCore lc, LinphoneFriendList list) {
		// TODO Auto-generated method stub
		String msg = "Friend List removed";
		myLog(msg);
		
		LinphoneFriendList[] lists = lc.getFriendLists();
		String msg2 = "There are " + lists.length + (lists.length > 1 ? " lists" : " list") + " in the core";
		myLog(msg2);
	}

	@Override
	public void authInfoRequested(LinphoneCore lc, String realm,
			String username, String Domain) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void callStatsUpdated(LinphoneCore lc, LinphoneCall call,
			LinphoneCallStats stats) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void newSubscriptionRequest(LinphoneCore lc, LinphoneFriend lf,
			String url) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void notifyPresenceReceived(LinphoneCore lc, LinphoneFriend lf) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void dtmfReceived(LinphoneCore lc, LinphoneCall call, int dtmf) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void notifyReceived(LinphoneCore lc, LinphoneCall call,
			LinphoneAddress from, byte[] event) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void transferState(LinphoneCore lc, LinphoneCall call,
			State new_call_state) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void infoReceived(LinphoneCore lc, LinphoneCall call,
			LinphoneInfoMessage info) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void subscriptionStateChanged(LinphoneCore lc, LinphoneEvent ev,
			SubscriptionState state) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void publishStateChanged(LinphoneCore lc, LinphoneEvent ev,
			PublishState state) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void show(LinphoneCore lc) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void displayStatus(LinphoneCore lc, String message) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void displayMessage(LinphoneCore lc, String message) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void displayWarning(LinphoneCore lc, String message) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void fileTransferProgressIndication(LinphoneCore lc,
			LinphoneChatMessage message, LinphoneContent content, int progress) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void fileTransferRecv(LinphoneCore lc, LinphoneChatMessage message,
			LinphoneContent content, byte[] buffer, int size) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public int fileTransferSend(LinphoneCore lc, LinphoneChatMessage message,
			LinphoneContent content, ByteBuffer buffer, int size) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void globalState(LinphoneCore lc, GlobalState state, String message) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void registrationState(LinphoneCore lc, LinphoneProxyConfig cfg,
			RegistrationState state, String smessage) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void configuringStatus(LinphoneCore lc,
			RemoteProvisioningState state, String message) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void messageReceived(LinphoneCore lc, LinphoneChatRoom cr,
			LinphoneChatMessage message) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void callState(LinphoneCore lc, LinphoneCall call, State state,
			String message) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void callEncryptionChanged(LinphoneCore lc, LinphoneCall call,
			boolean encrypted, String authenticationToken) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void notifyReceived(LinphoneCore lc, LinphoneEvent ev,
			String eventName, LinphoneContent content) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void isComposingReceived(LinphoneCore lc, LinphoneChatRoom cr) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void ecCalibrationStatus(LinphoneCore lc, EcCalibratorStatus status,
			int delay_ms, Object data) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void uploadProgressIndication(LinphoneCore lc, int offset, int total) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void uploadStateChanged(LinphoneCore lc,
			LogCollectionUploadState state, String info) {
		// TODO Auto-generated method stub
		
	}
}
