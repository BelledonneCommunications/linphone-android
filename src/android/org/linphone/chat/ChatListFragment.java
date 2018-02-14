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
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.linphone.contacts.ContactsManager;
import org.linphone.core.ChatRoomListenerStub;
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

import static org.linphone.fragments.FragmentsAvailable.CHAT_LIST;

public class ChatListFragment extends Fragment implements OnItemClickListener, ContactsUpdatedListener, ListSelectionHelper.DeleteListener {
	private LayoutInflater mInflater;
	private ListView mChatRoomsList;
	private TextView mNoChatHistory;
	private ImageView mNewDiscussionButton, mBackToCallButton;
	private boolean isEditMode = false;
	private ChatRoomsAdapter mChatRoomsAdapter;
	private CoreListenerStub mListener;
	private ListSelectionHelper mSelectionHelper;
	private RelativeLayout mWaitLayout;
	private int mChatRoomDeletionPendingCount;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		mInflater = inflater;

		View view = inflater.inflate(R.layout.chatlist, container, false);
		mSelectionHelper = new ListSelectionHelper(view, this);
		mChatRoomsAdapter = new ChatRoomsAdapter(getActivity(), mSelectionHelper, mInflater);
		mSelectionHelper.setAdapter(mChatRoomsAdapter);

		mWaitLayout = view.findViewById(R.id.waitScreen);
		mWaitLayout.setVisibility(View.GONE);

		mChatRoomsList = view.findViewById(R.id.chatList);
		mChatRoomsList.setAdapter(mChatRoomsAdapter);
		mChatRoomsList.setOnItemClickListener(this);

		mNoChatHistory = view.findViewById(R.id.noChatHistory);

		mNewDiscussionButton = view.findViewById(R.id.new_discussion);
		mNewDiscussionButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				LinphoneActivity.instance().goToChatCreator(null, null, null, false);
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

		return view;
	}

	private void refreshChatRoomsList() {
		mChatRoomsAdapter.refresh();
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
		super.onPause();
	}

	@Override
	public void onDeleteSelection(Object[] objectsToDelete) {
		Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		mChatRoomDeletionPendingCount = objectsToDelete.length;
		for (Object obj : objectsToDelete) {
			ChatRoom room = (ChatRoom)obj;
			room.setListener(new ChatRoomListenerStub() {
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
			});
			lc.deleteChatRoom(room);
		}
		if (mChatRoomDeletionPendingCount > 0) {
			mWaitLayout.setVisibility(View.VISIBLE);
		}
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

	@Override
	public void onItemClick(AdapterView<?> adapter, View view, int position, long id) {
		ChatRoom chatRoom = (ChatRoom) mChatRoomsList.getAdapter().getItem(position);

		if (LinphoneActivity.isInstanciated() && !isEditMode) {
			LinphoneActivity.instance().goToChat(chatRoom.getPeerAddress().asString());
		}
	}
}


