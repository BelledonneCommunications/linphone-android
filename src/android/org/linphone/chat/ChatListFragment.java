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

//import android.support.v7.view.ActionMode;

import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Layout;
import android.view.ActionMode;
import android.support.v7.app.ActionBar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
//import android.widget.Toolbar;
import android.support.v7.widget.Toolbar;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.activities.LinphoneActivity;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.ContactsUpdatedListener;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatRoom;
import org.linphone.core.ChatRoomListenerStub;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.fragments.FragmentsAvailable;
import org.linphone.mediastream.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.linphone.fragments.FragmentsAvailable.CHAT_LIST;

public class ChatListFragment extends Fragment implements ContactsUpdatedListener, ListSelectionHelper.DeleteListener {
//public class ChatListFragment extends Fragment {
	private ActionModeCallback actionModeCallback = new ActionModeCallback();
	private ActionMode actionMode;

	private LinearLayout mTabBar;
	private DrawerLayout mSideMenu;
	private ImageView mEditButton;
	private MenuItem mSelectAllButton, mDeselectAllButton, mDeleteButton;
	private RecyclerView mChatRoomsList;
	private TextView mNoChatHistory;
	private ImageView mNewDiscussionButton, mBackToCallButton;
	private ChatRoomsAdapter mChatRoomsAdapter;
	private CoreListenerStub mListener;
//	private ListSelectionHelper mSelectionHelper;
	private RelativeLayout mWaitLayout;
	private int mChatRoomDeletionPendingCount;
	private ChatRoomListenerStub mChatRoomListener;
	private Context mContext;
	private List<ChatRoom> mRooms;

	@Override
	public View onCreateView(final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
//		mInflater = inflater;

		super.onCreate(savedInstanceState);

		setHasOptionsMenu(true);
		mRooms = new ArrayList<>(Arrays.asList(LinphoneManager.getLc().getChatRooms()));
		this.mContext = getActivity().getApplicationContext();
		View view = inflater.inflate(R.layout.chatlist, container, false);
		RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(mContext);

		Toolbar toolbar = this.getActivity().findViewById(R.id.context_bar);

//		mChatRoomsAdapter = new ChatRoomsAdapter(mContext, R.layout.chatlist_cell, mRooms);
		mChatRoomsAdapter = new ChatRoomsAdapter(mContext, R.layout.chatlist_cell, mRooms,this);






//		mSelectionHelper = new ListSelectionHelper(view, this);
//		mChatRoomsAdapter = new ChatRoomsAdapter(this, mSelectionHelper, mRooms);
//		mSelectionHelper.setAdapter(mChatRoomsAdapter);
//		mSelectionHelper.setDialogMessage(R.string.chat_room_delete_dialog);





		mWaitLayout = view.findViewById(R.id.waitScreen);
		mWaitLayout.setVisibility(View.GONE);

		mChatRoomsList = view.findViewById(R.id.chatList);
		mChatRoomsList.setAdapter(mChatRoomsAdapter);
		mChatRoomsList.setLayoutManager(layoutManager);
//		mNoChatHistory = view.findViewById(R.id.noChatHistory);

//		mNoChatHistory.setVisibility(View.GONE);
		//mEditTopBar = view.findViewById(R.id.edit_list_menu);



//		mTopBar = view.findViewById(R.id.top_bar);
//		Toolbar toolbar = this.getActivity().findViewById(R.id.context_bar);




//		mStatusBar = view.findViewById(R.id.status);

		mEditButton = view.findViewById(R.id.edit);
		mEditButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				actionMode = getActivity().startActionMode(actionModeCallback);



//				getActivity().setActionBar();
				//				mChatRoomsAdapter.setEditionMode(actionMode);
//				mTopBar.setVisibility(View.GONE);
//				mEditTopBar.setVisibility(View.VISIBLE);
			}
		});

//		mCancelButton = view.findViewById(R.id.cancel);
//		mCancelButton.setOnClickListener(new View.OnClickListener() {
//			@Override
//			public void onClick(View v) {
//				actionMode.invalidate();
//			}
//		});
//		mDeselectAllButton = view.findViewById(R.id.deselect_all);
//		mSelectAllButton = view.findViewById(R.id.select_all);



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

	@Override
	public void onItemClicked(int position) {
		if (actionMode != null) {
			toggleSelection(position);
		}else{
			ChatRoom room = (ChatRoom) mChatRoomsAdapter.getItem(position);
			LinphoneActivity.instance().goToChat(room.getPeerAddress().asString());
		}
	}

	@Override
	public boolean onItemLongClicked(int position) {
		if (actionMode == null) {

			actionMode = getActivity().startActionMode(actionModeCallback);

		}

		toggleSelection(position);

		return true;
	}

	private void toggleSelection(int position) {
		mChatRoomsAdapter.toggleSelection(position);
//
		int count = mChatRoomsAdapter.getSelectedItemCount();

		if (count < mChatRoomsAdapter.getItemCount()) {
//			actionMode.finish();
			mDeselectAllButton.setVisible(false);
			mSelectAllButton.setVisible(true);

		} else {

//			actionMode.setTitle(String.valueOf(count));
			mDeselectAllButton.setVisible(true);
			mSelectAllButton.setVisible(false);

		}
		getActivity().invalidateOptionsMenu();
		actionMode.invalidate();

	}

	private class ActionModeCallback implements ActionMode.Callback {
		@SuppressWarnings("unused")
		private final String TAG = ActionModeCallback.class.getSimpleName();

		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			//mTopBar.setVisibility(View.GONE);
//			Toolbar contextBar =
//			(RelativeLayout) view.findViewById(R.id.status);
//			getActivity().getContext();
//			View v = View.inflate(R.layout.status, null);
//			View view = (RelativeLayout)inflater.inflate(R.layout.chatlist, container, false);
//			mStatusBar.setVisibility(View.VISIBLE);
			mode.getMenuInflater().inflate (R.menu.edit_list_menu, menu);
//			Menu contextBar = menu;
			mDeselectAllButton = menu.findItem(R.id.deselect_all);
			mSelectAllButton = menu.findItem(R.id.select_all);
			mDeleteButton = menu.findItem(R.id.select_all);
			mTabBar = (LinearLayout)  getActivity().findViewById(R.id.footer);
			mTabBar.setVisibility(View.INVISIBLE);
			mSideMenu=(DrawerLayout) getActivity().findViewById(R.id.side_menu);

			mSideMenu.setDrawerLockMode(1);
//			SlidingDrawer simpleSlidingDrawer = (SlidingDrawer) getActivity().findViewById(R.id.side_menu); // initiate the SlidingDrawer
//			simpleSlidingDrawer.lock(); // lock the sliderDrawer so that touch event are disabled

//			menu.findItem(R.id.menu_search).getActionView();
//			LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
//			View tv= inflater.inflate(R.layout.context_bar, null);
//			mode.setCustomView(tv);

//			(Toolbar) findViewById(R.id.status);


			//mEditTopBar.setVisibility(View.VISIBLE);

			for (Integer i = 0; i <= mChatRoomsAdapter.getItemCount(); i++) {
				mChatRoomsAdapter.setEditionMode(mode);
			}
			return true;
		}

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
//			int cpt = 0;
//			while (cpt <= mChatRoomsAdapter.getItemCount()) {
//				mChatRoomsAdapter.setEditionMode(mode);
//				cpt++;
//			}
//			final MenuItem alertMenuItem = menu.findItem(R.id.menu_search);

			return false;
		}

		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			switch (item.getItemId()) {

				case R.id.delete:
					// TODO: actually remove items
//					Log.d(TAG, "menu_remove");
					mChatRoomsAdapter.removeItems(mChatRoomsAdapter.getSelectedItems());
					mode.finish();
					return true;

				case R.id.select_all:
				//Add all non-selected items to the selection
						for (Integer i = 0; i < mChatRoomsAdapter.getItemCount(); i++) {
							if (!mChatRoomsAdapter.isSelected(i)) {
								toggleSelection(i);
							}
						}

					return true;

				case R.id.deselect_all:
					for (Integer i = 0; i < mChatRoomsAdapter.getItemCount(); i++) {
						if (mChatRoomsAdapter.isSelected(i)) {
							toggleSelection(i);
						}
					}
					return true;
//				case R.id.cancel:
//					mode.finish();
				default:

					return false;
			}
		}

		@Override
		public void onDestroyActionMode(ActionMode mode) {

			mChatRoomsAdapter.clearSelection();
			mTabBar.setVisibility(View.VISIBLE);
			mSideMenu.setDrawerLockMode(0);

			actionMode = null;
			mChatRoomsAdapter.setEditionMode(actionMode);

		}

	}









	private void refreshChatRoomsList() {
		mChatRoomsAdapter.refresh();
//		mNoChatHistory.setVisibility(mChatRoomsAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
	}

	public void displayFirstChat() {
		ChatRoomsAdapter adapter = (ChatRoomsAdapter)mChatRoomsList.getAdapter();
		if (adapter != null && adapter.getItemCount() > 0) {
			ChatRoom room = (ChatRoom) adapter.getItem(0);
			LinphoneActivity.instance().goToChat(room.getPeerAddress().asStringUriOnly());
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


		mChatRoomsAdapter.clearSelection();
//		mTabBar.setVisibility(View.VISIBLE);

		actionMode = null;
		mChatRoomsAdapter.setEditionMode(actionMode);

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
					if (message.getAppdata() != null && !message.isOutgoing()) {
						File file = new File(message.getAppdata());
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
	}

		@Override
	public void onContactsUpdated() {
		if (!LinphoneActivity.isInstanciated() || LinphoneActivity.instance().getCurrentFragment() != CHAT_LIST)
			return;

		ChatRoomsAdapter adapter = (ChatRoomsAdapter) mChatRoomsList.getAdapter();
		if (adapter != null) {
//			adapter.notifyDataSetInvalidated();
		}
	}



}

