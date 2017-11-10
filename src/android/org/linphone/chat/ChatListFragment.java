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

import android.app.Dialog;
import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.linphone.contacts.ContactsManager;
import org.linphone.receivers.ContactsUpdatedListener;
import org.linphone.fragments.FragmentsAvailable;
import org.linphone.activities.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatRoom;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;

import static org.linphone.fragments.FragmentsAvailable.CHAT_LIST;

public class ChatListFragment extends Fragment implements OnItemClickListener, ContactsUpdatedListener {
	private LayoutInflater mInflater;
	private ListView mChatRoomsList;
	private TextView mNoChatHistory;
	private ImageView mEditButton, mSelectAllButton, mDeselectAllButton, mDeleteSelectionButton, mNewDiscussionButton, mCancelButton, mBackToCallButton;
	private LinearLayout mEditTopBar, mTopBar;
	private boolean isEditMode = false;
	private ChatRoomsAdapter mChatRoomsAdapter;
	private CoreListenerStub mListener;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		mInflater = inflater;

		View view = inflater.inflate(R.layout.chatlist, container, false);
		mChatRoomsList = view.findViewById(R.id.chatList);
		mChatRoomsList.setAdapter(new ChatRoomsAdapter(getActivity(), this, mInflater));
		mChatRoomsList.setOnItemClickListener(this);

		mNoChatHistory = view.findViewById(R.id.noChatHistory);

		mEditTopBar = view.findViewById(R.id.edit_list);
		mTopBar = view.findViewById(R.id.top_bar);

		mCancelButton = view.findViewById(R.id.cancel);
		mCancelButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				quitEditionMode();
			}
		});

		mEditButton = view.findViewById(R.id.edit);
		mEditButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mChatRoomsAdapter.enableEdition(true);
				mTopBar.setVisibility(View.GONE);
				mEditTopBar.setVisibility(View.VISIBLE);
			}
		});

		mNewDiscussionButton = view.findViewById(R.id.new_discussion);
		mNewDiscussionButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				LinphoneActivity.instance().goToChatCreator(null, null, null, false);
			}
		});

		mSelectAllButton = view.findViewById(R.id.select_all);
		mSelectAllButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mChatRoomsAdapter.selectAll();
			}
		});

		mDeselectAllButton = view.findViewById(R.id.deselect_all);
		mDeselectAllButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mChatRoomsAdapter.deselectAll();
			}
		});

		mBackToCallButton = view.findViewById(R.id.back_in_call);
		mBackToCallButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				LinphoneActivity.instance().resetClassicMenuLayoutAndGoBackToCallIfStillRunning();
			}
		});

		mDeleteSelectionButton = view.findViewById(R.id.delete);
		mDeleteSelectionButton.setEnabled(false);
		mDeleteSelectionButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				final Dialog dialog = LinphoneActivity.instance().displayDialog(getString(R.string.delete_text));
				Button delete = dialog.findViewById(R.id.delete_button);
				Button cancel = dialog.findViewById(R.id.cancel);

				delete.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						deleteSelectedRooms();
						dialog.dismiss();
						quitEditionMode();
					}
				});

				cancel.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						dialog.dismiss();
						quitEditionMode();
					}
				});
				dialog.show();
			}
		});

		mListener = new CoreListenerStub() {
			@Override
			public void onMessageReceived(Core lc, ChatRoom cr, ChatMessage message) {
				refreshChatRoomsList();
			}

			@Override
			public void onChatRoomInstantiated(Core lc, ChatRoom cr) {
				refreshChatRoomsList();
			}
		};

		mChatRoomsAdapter = new ChatRoomsAdapter(getActivity(), this, mInflater);
		mChatRoomsList.setAdapter(mChatRoomsAdapter);

		return view;
	}

	private void refreshChatRoomsList() {
		mChatRoomsAdapter.refresh();
	}

	private void deleteSelectedRooms() {
		ChatRoom rooms[] = mChatRoomsAdapter.getSelectedItems();
		//TODO
	}

	public void updateSelectionButtons(boolean isSelectionEmpty, boolean isSelectionFull) {
		if (isSelectionEmpty) {
			mDeleteSelectionButton.setEnabled(false);
		} else {
			mDeleteSelectionButton.setEnabled(true);
		}

		if (isSelectionFull) {
			mSelectAllButton.setVisibility(View.GONE);
			mDeselectAllButton.setVisibility(View.VISIBLE);
		} else {
			mSelectAllButton.setVisibility(View.VISIBLE);
			mDeselectAllButton.setVisibility(View.GONE);
		}
	}

	private void quitEditionMode() {
		mChatRoomsAdapter.enableEdition(false);
		mTopBar.setVisibility(View.VISIBLE);
		mEditTopBar.setVisibility(View.GONE);
		mDeleteSelectionButton.setEnabled(false);
		mSelectAllButton.setVisibility(View.VISIBLE);
		mDeselectAllButton.setVisibility(View.GONE);
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


