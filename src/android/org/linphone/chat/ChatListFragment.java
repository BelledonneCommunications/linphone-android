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
	private ListView chatList;
	private TextView noChatHistory;
	private ImageView edit, selectAll, deselectAll, delete, newDiscussion, cancel, backInCall;
	private LinearLayout editList, topbar;
	private boolean isEditMode = false;
	private CoreListenerStub mListener;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		mInflater = inflater;

		View view = inflater.inflate(R.layout.chatlist, container, false);
		chatList = view.findViewById(R.id.chatList);
		chatList.setAdapter(new ChatRoomsAdapter(getActivity(), this, mInflater));
		chatList.setOnItemClickListener(this);

		noChatHistory = view.findViewById(R.id.noChatHistory);

		editList = view.findViewById(R.id.edit_list);
		topbar = view.findViewById(R.id.top_bar);

		cancel = view.findViewById(R.id.cancel);
		cancel.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				//TODO
			}
		});

		edit = view.findViewById(R.id.edit);
		edit.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				//TODO
			}
		});

		newDiscussion = view.findViewById(R.id.new_discussion);
		newDiscussion.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				LinphoneActivity.instance().goToChatCreator(null, false);
			}
		});

		selectAll = view.findViewById(R.id.select_all);
		selectAll.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				//TODO
			}
		});

		deselectAll = view.findViewById(R.id.deselect_all);
		deselectAll.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				//TODO
			}
		});

		backInCall = view.findViewById(R.id.back_in_call);
		backInCall.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				LinphoneActivity.instance().resetClassicMenuLayoutAndGoBackToCallIfStillRunning();
				return;
			}
		});

		delete = view.findViewById(R.id.delete);
		delete.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				//TODO
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

		/*ChatFragment.createIfNotExist();
		ChatFragment.addChatListener(this);*/
		return view;
	}

	private void refreshChatRoomsList() {
		((ChatRoomsAdapter)chatList.getAdapter()).refresh();
	}

	/*private void selectAllList(boolean isSelectAll){
		int size = chatList.getAdapter().getCount();
		for(int i=0; i<size; i++) {
			chatList.setItemChecked(i,isSelectAll);
		}
	}*/

	/*private void removeChatsConversation() {
		int size = chatList.getAdapter().getCount();
		for (int i = 0; i < size; i++) {
			if (chatList.isItemChecked(i)) {
				String sipUri = chatList.getAdapter().getItem(i).toString();
				if (sipUri != null) {
					ChatRoom chatroom = LinphoneManager.getLc().getChatRoomFromUri(sipUri);
					if (chatroom != null) {
						chatroom.deleteHistory();
					}
				}
			}
		}
		quitEditMode();
		LinphoneActivity.instance().updateMissedChatCount();
	}*/

	/*public void quitEditMode(){
		isEditMode = false;
		editList.setVisibility(View.GONE);
		topbar.setVisibility(View.VISIBLE);
		refresh();
		if(getResources().getBoolean(R.bool.isTablet)){
			displayFirstChat();
		}
	}*/

	/*public int getNbItemsChecked(){
		int size = chatList.getAdapter().getCount();
		int nb = 0;
		for(int i=0; i<size; i++) {
			if(chatList.isItemChecked(i)) {
				nb ++;
			}
		}
		return nb;
	}*/

	/*public void enabledDeleteButton(Boolean enabled){
		if(enabled){
			delete.setEnabled(true);
		} else {
			if (getNbItemsChecked() == 0){
				delete.setEnabled(false);
			}
		}
	}*/

	/*private void hideAndDisplayMessageIfNoChat() {
		if (mConversations.size() == 0) {
			noChatHistory.setVisibility(View.VISIBLE);
			chatList.setVisibility(View.GONE);
			edit.setEnabled(false);
		} else {
			noChatHistory.setVisibility(View.GONE);
			chatList.setVisibility(View.VISIBLE);
			chatList.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
			chatList.setAdapter(new ChatRoomsAdapter(getActivity(), this, mInflater));
			edit.setEnabled(true);
		}
	}*/

	/*public void refresh() {
		refreshChatRoomsList();
		hideAndDisplayMessageIfNoChat();
	}*/

	/*public void displayFirstChat(){
		if (mConversations != null && mConversations.size() > 0) {
			LinphoneActivity.instance().displayChat(mConversations.get(0), null, null);
		} else {
			LinphoneActivity.instance().displayEmptyFragment();
		}
	}*/

	@Override
	public void onResume() {
		super.onResume();
		ContactsManager.addContactsListener(this);

		if (LinphoneManager.getLc().getCallsNb() > 0) {
			backInCall.setVisibility(View.VISIBLE);
		} else {
			backInCall.setVisibility(View.INVISIBLE);
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

		ChatRoomsAdapter adapter = (ChatRoomsAdapter)chatList.getAdapter();
		if (adapter != null) {
			adapter.notifyDataSetInvalidated();
		}
	}

	@Override
	public void onItemClick(AdapterView<?> adapter, View view, int position, long id) {
		String sipUri = chatList.getAdapter().getItem(position).toString();

		if (LinphoneActivity.isInstanciated() && !isEditMode) {
			LinphoneActivity.instance().goToChat(sipUri);
		}
	}

	/*@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		menu.add(0, v.getId(), 0, getString(R.string.delete));
	}*/

	/*@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		if (info == null || info.targetView == null) {
			return false;
		}
		String sipUri = chatList.getAdapter().getItem(info.position).toString();

		LinphoneActivity.instance().removeFromChatList(sipUri);
		refreshChatRoomsList();
        if (getResources().getBoolean(R.bool.isTablet)) {
			quitEditMode();
        }
		hideAndDisplayMessageIfNoChat();
		return true;
	}*/

	/*@Override
	public void onClick(View v) {
		int id = v.getId();


		if (id == R.id.select_all) {
			deselectAll.setVisibility(View.VISIBLE);
			selectAll.setVisibility(View.GONE);
			enabledDeleteButton(true);
			selectAllList(true);
			return;
		}
		if (id == R.id.deselect_all) {
			deselectAll.setVisibility(View.GONE);
			selectAll.setVisibility(View.VISIBLE);
			enabledDeleteButton(false);
			selectAllList(false);
			return;
		}

		if (id == R.id.cancel) {
			quitEditMode();
			return;
		}

		if (id == R.id.delete) {
			final Dialog dialog = LinphoneActivity.instance().displayDialog(getString(R.string.delete_text));
			Button delete = dialog.findViewById(R.id.delete_button);
			Button cancel = dialog.findViewById(R.id.cancel);

			delete.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
					removeChatsConversation();
					dialog.dismiss();
					quitEditMode();
				}
			});

			cancel.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
					dialog.dismiss();
					quitEditMode();
				}
			});
			dialog.show();
			return;
		}
		else if (id == R.id.edit) {
			topbar.setVisibility(View.GONE);
			editList.setVisibility(View.VISIBLE);
			isEditMode = true;
			hideAndDisplayMessageIfNoChat();
			enabledDeleteButton(false);
		}
	}*/

	/*@Override
	public void onChatUpdated() {
		refreshChatRoomsList();
	}*/
}


