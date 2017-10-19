package org.linphone.tutorials;

/*
TutorialCardDavSync.java
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
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.linphone.R;
import org.linphone.UIThreadDispatcher;
import org.linphone.core.Address;
import org.linphone.core.AuthInfo;
import org.linphone.core.Call;
import org.linphone.core.Call.State;
import org.linphone.core.CallStats;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatRoom;
import org.linphone.core.Content;
import org.linphone.core.Core;
import org.linphone.core.Core.AuthMethod;
import org.linphone.core.Core.ConfiguringState;
import org.linphone.core.Core.EcCalibratorStatus;
import org.linphone.core.Core.GlobalState;
import org.linphone.core.Core.LogCollectionUploadState;
import org.linphone.core.Core.RegistrationState;
import org.linphone.core.CoreException;
import org.linphone.core.CoreListener;
import org.linphone.core.Event;
import org.linphone.core.Factory;
import org.linphone.core.Friend;
import org.linphone.core.FriendList;
import org.linphone.core.FriendList.FriendListListener;
import org.linphone.core.InfoMessage;
import org.linphone.core.ProxyConfig;
import org.linphone.core.PublishState;
import org.linphone.core.SubscriptionState;
import org.linphone.mediastream.Log;

import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;

public class TutorialCardDavSync extends Activity implements OnClickListener, CoreListener, FriendListListener {
	private EditText username, password, ha1, server;
	private Button synchronize;
	private TextView logs;

	private Timer timer;

	private Core lc;
	private FriendList lfl;

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

		Factory.instance().setDebugMode(true, "CardDAV sync tutorial");
		try {
			lc = Factory.instance().createCore(this, this);
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

			lfl = lc.createFriendList();
			lc.addFriendList(lfl);

			Friend lf = lc.createFriendWithAddress("sip:ghislain@sip.linphone.org");
			lf.setName("Ghislain");
			lfl.addLocalFriend(lf); // This is a local friend, it won't be sent to the CardDAV server and will be removed at the next synchronization
		} catch (CoreException e) {
			Log.e(e);
		}
	}

	@Override
	protected void onDestroy() {
		try {
			lc.removeFriendList(lfl);
		} catch (CoreException e) {
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
		AuthInfo authInfo = Factory.instance().createAuthInfo(username.getText().toString(), null, password.getText().toString(), ha1.getText().toString(), "SabreDAV", serverDomain);
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
	public void onContactCreated(FriendList list,
			Friend lf) {
		// TODO Auto-generated method stub
		String msg = "Friend created " + lf.getAddress();
		myLog(msg);

		Friend[] friends = list.getFriendsLists();
		String msg2 = "There are " + friends.length + (friends.length > 1 ? " friends" : " friend") + " in the list";
		myLog(msg2);
	}

	@Override
	public void onContactUpdated(FriendList list,
			Friend newFriend, Friend oldFriend) {
		// TODO Auto-generated method stub
		String msg = "Friend updated " + newFriend.getAddress();
		myLog(msg);

		Friend[] friends = list.getFriendsLists();
		String msg2 = "There are " + friends.length + (friends.length > 1 ? " friends" : " friend") + " in the list";
		myLog(msg2);
	}

	@Override
	public void onContactDeleted(FriendList list,
			Friend lf) {
		// TODO Auto-generated method stub
		String msg = "Friend removed " + lf.getAddress();
		myLog(msg);

		Friend[] friends = list.getFriendsLists();
		String msg2 = "There are " + friends.length + (friends.length > 1 ? " friends" : " friend") + " in the list";
		myLog(msg2);
	}

	@Override
	public void onSyncStatusChanged(FriendList list, FriendList.State status, String message) {
		// TODO Auto-generated method stub
		String msg = "Sync status changed: " + status.toString() + " (" + message + ")";
		myLog(msg);
		if (status != FriendList.State.SyncStarted) {
			synchronize.setEnabled(true);
		}
	}

	@Override
	public void onFriendListCreated(Core lc, FriendList list) {
		// TODO Auto-generated method stub
		String msg = "Friend List added";
		myLog(msg);

		FriendList[] lists = lc.getFriendsLists();
		String msg2 = "There are " + lists.length + (lists.length > 1 ? " lists" : " list") + " in the core";
		myLog(msg2);
	}

	@Override
	public void onFriendListRemoved(Core lc, FriendList list) {
		// TODO Auto-generated method stub
		String msg = "Friend List removed";
		myLog(msg);

		FriendList[] lists = lc.getFriendsLists();
		String msg2 = "There are " + lists.length + (lists.length > 1 ? " lists" : " list") + " in the core";
		myLog(msg2);
	}

	@Override
	public void onNetworkReachable(Core lc, boolean enable) {

	}

	@Override
	public void onCallStatsUpdated(Core lc, Call call,
			CallStats stats) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onNewSubscriptionRequested(Core lc, Friend lf,
			String url) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onNotifyPresenceReceived(Core lc, Friend lf) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onDtmfReceived(Core lc, Call call, int dtmf) {
		// TODO Auto-generated method stub

	}

	@Override
	public void removed(Core lc, Call call,
			Address from, byte[] event) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onTransferStateChanged(Core lc, Call call,
			State new_call_state) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onInfoReceived(Core lc, Call call,
			InfoMessage info) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onSubscriptionStateChanged(Core lc, Event ev,
			SubscriptionState state) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onPublishStateChanged(Core lc, Event ev,
			PublishState state) {
		// TODO Auto-generated method stub

	}

	@Override
	public void removed( lc) {
		// TODO Auto-generated method stub

	}

	@Override
	public void removed(Core lc, String message) {
		// TODO Auto-generated method stub

	}

	@Override
	public void removed(Core lc, String message) {
		// TODO Auto-generated method stub

	}

	@Override
	public void removed(Core lc, String message) {
		// TODO Auto-generated method stub

	}

	@Override
	public void removed(Core lc,
			ChatMessage message, Content content, int progress) {
		// TODO Auto-generated method stub

	}

	@Override
	public void removed(Core lc, ChatMessage message,
			Content content, byte[] buffer, int size) {
		// TODO Auto-generated method stub

	}

	@Override
	public int removed(Core lc, ChatMessage message,
			Content content, ByteBuffer buffer, int size) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void onGlobalStateChanged(Core lc, GlobalState state, String message) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onRegistrationStateChanged(Core lc, ProxyConfig cfg,
			RegistrationState state, String smessage) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onConfiguringStatus(Core lc,
			ConfiguringState state, String message) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onMessageReceived(Core lc, ChatRoom cr,
			ChatMessage message) {
		// TODO Auto-generated method stub

	}

	@Override
	public void removed(Core lc, ChatRoom cr, ChatMessage message) {

	}

	@Override
	public void onCallStateChanged(Core lc, Call call, State state,
			String message) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onCallEncryptionChanged(Core lc, Call call,
			boolean encrypted, String authenticationToken) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onNotifyReceived(Core lc, Event ev,
			String eventName, Content content) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onIsComposingReceived(Core lc, ChatRoom cr) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onEcCalibrationResult(Core lc, EcCalibratorStatus status,
			int delay_ms, Object data) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onLogCollectionUploadProgressIndication(Core lc, int offset, int total) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onLogCollectionUploadStateChanged(Core lc,
			LogCollectionUploadState state, String info) {
		// TODO Auto-generated method stub

	}

	@Override
	public void removed(Core lc, String realm,
			String username, String domain) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onAuthenticationRequested(Core lc,
			AuthInfo authInfo, AuthMethod method) {
		// TODO Auto-generated method stub

	}
}
