/*
ChatListFragment.java
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

package org.linphone.chat;

import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.linphone.contacts.ContactsManager;
import org.linphone.core.ChatRoomListenerStub;
import org.linphone.core.EventLog;
import org.linphone.mediastream.Log;
import org.linphone.ui.ListSelectionHelper;
import org.linphone.contacts.ContactsUpdatedListener;
import org.linphone.fragments.FragmentsAvailable;
import org.linphone.activities.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatRoom;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;

import java.io.File;

import static org.linphone.fragments.FragmentsAvailable.CHAT_LIST;

public class ChatListFragment extends Fragment implements ContactsUpdatedListener, ListSelectionHelper.DeleteListener {
	private LayoutInflater mInflater;
	private ListView mChatRoomsList;
	private TextView mNoChatHistory;
	private ImageView mNewDiscussionButton, mBackToCallButton;
	private ChatRoomsAdapter mChatRoomsAdapter;
	private CoreListenerStub mListener;
	private ListSelectionHelper mSelectionHelper;
	private RelativeLayout mWaitLayout;
	private int mChatRoomDeletionPendingCount;
	private ChatRoomListenerStub mChatRoomListener;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		mInflater = inflater;

		View view = inflater.inflate(R.layout.chatlist, container, false);
		mSelectionHelper = new ListSelectionHelper(view, this);
		mChatRoomsAdapter = new ChatRoomsAdapter(getActivity(), mSelectionHelper, mInflater);
		mSelectionHelper.setAdapter(mChatRoomsAdapter);
		mSelectionHelper.setDialogMessage(R.string.chat_room_delete_dialog);

		mWaitLayout = view.findViewById(R.id.waitScreen);
		mWaitLayout.setVisibility(View.GONE);

		mChatRoomsList = view.findViewById(R.id.chatList);
		mChatRoomsList.setAdapter(mChatRoomsAdapter);

		mNoChatHistory = view.findViewById(R.id.noChatHistory);
		mNoChatHistory.setVisibility(View.GONE);

		mNewDiscussionButton = view.findViewById(R.id.new_discussion);
		mNewDiscussionButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				LinphoneActivity.instance().goToChatCreator(null, null, null, false, null);
			}
		});

		mBackToCallButton = view.findViewById(R.id.back_in_call);
		mBackToCallButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				LinphoneActivity.instance().resetClassicMenuLayoutAndGoBackToCallIfStillRunning();
			}
		});

		mListener = new CoreListenerStub() {
			@Override
			public void onMessageReceived(Core lc, ChatRoom cr, ChatMessage message) {
				refreshChatRoomsList();
			}

			@Override
			public void onChatRoomStateChanged(Core lc, ChatRoom cr, ChatRoom.State state) {
				if (state == ChatRoom.State.Created) {
					refreshChatRoomsList();
				}
			}
		};

		mChatRoomListener = new ChatRoomListenerStub() {
			@Override
			public void onStateChanged(ChatRoom room, ChatRoom.State state) {
				super.onStateChanged(room, state);
				if (state == ChatRoom.State.Deleted || state == ChatRoom.State.TerminationFailed) {
					mChatRoomDeletionPendingCount -= 1;

					if (state == ChatRoom.State.TerminationFailed) {
						//TODO error message
					}

					if (mChatRoomDeletionPendingCount == 0) {
						mWaitLayout.setVisibility(View.GONE);
						refreshChatRoomsList();
					}
				}
			}
		};

		return view;
	}

	private void refreshChatRoomsList() {
		mChatRoomsAdapter.refresh();
		mNoChatHistory.setVisibility(mChatRoomsAdapter.getCount() == 0 ? View.VISIBLE : View.GONE);
	}

	public void displayFirstChat() {
		ChatRoomsAdapter adapter = (ChatRoomsAdapter)mChatRoomsList.getAdapter();
		if (adapter != null && adapter.getCount() > 0) {
			ChatRoom room = (ChatRoom) adapter.getItem(0);
			LinphoneActivity.instance().goToChat(room.getPeerAddress().asStringUriOnly(), null);
		} else {
			LinphoneActivity.instance().displayEmptyFragment();
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		ContactsManager.addContactsListener(this);

		if (LinphoneManager.getLc().getCallsNb() > 0) {
			mBackToCallButton.setVisibility(View.VISIBLE);
		} else {
			mBackToCallButton.setVisibility(View.INVISIBLE);
		}

		if (LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().selectMenu(FragmentsAvailable.CHAT_LIST);
			LinphoneActivity.instance().hideTabBar(false);
		}

		Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			lc.addListener(mListener);
		}

		refreshChatRoomsList();
	}

	@Override
	public void onPause() {
		Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			lc.removeListener(mListener);
		}
		ContactsManager.removeContactsListener(this);
		mChatRoomsAdapter.clear();
		super.onPause();
	}

	@Override
	public void onDeleteSelection(Object[] objectsToDelete) {
		Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		mChatRoomDeletionPendingCount = objectsToDelete.length;
		for (Object obj : objectsToDelete) {
			ChatRoom room = (ChatRoom)obj;

			for (EventLog eventLog : room.getHistoryEvents(0)) {
				if (eventLog.getType() == EventLog.Type.ConferenceChatMessage) {
					ChatMessage message = eventLog.getChatMessage();
					if (message.getContents() != null && message.getContents().length > 0 && !message.isOutgoing()) {
						File file = new File(message.getContents()[0].getFilePath());
						if (file.exists()) {
							file.delete(); // Delete downloaded file from incoming message that will be deleted
						}
					}
				}
			}

			room.addListener(mChatRoomListener);
			lc.deleteChatRoom(room);
		}
		if (mChatRoomDeletionPendingCount > 0) {
			mWaitLayout.setVisibility(View.VISIBLE);
		}
		LinphoneActivity.instance().refreshMissedChatCountDisplay();
	}

	@Override
	public void onContactsUpdated() {
		if (!LinphoneActivity.isInstanciated() || LinphoneActivity.instance().getCurrentFragment() != CHAT_LIST)
			return;

		ChatRoomsAdapter adapter = (ChatRoomsAdapter) mChatRoomsList.getAdapter();
		if (adapter != null) {
			adapter.notifyDataSetInvalidated();
		}
	}
}


