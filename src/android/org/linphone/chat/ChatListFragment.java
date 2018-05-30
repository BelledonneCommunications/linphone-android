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
import android.graphics.Canvas;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

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
import org.linphone.ui.SwipeController;
import org.linphone.ui.SwipeControllerActions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.linphone.fragments.FragmentsAvailable.CHAT_LIST;

/*
* Sources: Linphone + https://enoent.fr/blog/2015/01/18/recyclerview-basics/
* */

public class ChatListFragment extends Fragment implements ContactsUpdatedListener, ChatRoomsAdapter.ChatRoomViewHolder.ClickListener {

	private ActionModeCallback actionModeCallback = new ActionModeCallback();
	private ActionMode actionMode;
	private LinearLayout mEditTopBar, mTopBar;
	private ImageView mEditButton, mSelectAllButton, mDeselectAllButton,mDeleteButton, mCancelButton;
	private RecyclerView mChatRoomsList;
	private TextView mNoChatHistory;
	private ImageView mNewDiscussionButton, mBackToCallButton;
	private ChatRoomsAdapter mChatRoomsAdapter;
	private CoreListenerStub mListener;
	private RelativeLayout mWaitLayout;
	private int mChatRoomDeletionPendingCount;
	private ChatRoomListenerStub mChatRoomListener;
	private Context mContext;
	public List<ChatRoom> mRooms;

	@Override
	public View onCreateView(final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		//We get back all ChatRooms from the LinphoneManager and store them
		mRooms = new ArrayList<>(Arrays.asList(LinphoneManager.getLc().getChatRooms()));

		this.mContext = getActivity().getApplicationContext();
		View view = inflater.inflate(R.layout.chatlist, container, false);

		//Views definition
		mChatRoomsList = view.findViewById(R.id.chatList);
		mWaitLayout = view.findViewById(R.id.waitScreen);
		mEditTopBar = view.findViewById(R.id.edit_list);
		mTopBar = view.findViewById(R.id.top_bar);
		mSelectAllButton = view.findViewById(R.id.select_all);
		mDeselectAllButton = view.findViewById(R.id.deselect_all);
		mDeleteButton= view.findViewById(R.id.delete);
		mEditButton = view.findViewById(R.id.edit);
		mCancelButton = view.findViewById(R.id.cancel);
		mNewDiscussionButton = view.findViewById(R.id.new_discussion);
		mBackToCallButton = view.findViewById(R.id.back_in_call);

		//Creation and affectation of adapter to the RecyclerView
		mChatRoomsAdapter = new ChatRoomsAdapter(mContext, R.layout.chatlist_cell, mRooms,this);
		mChatRoomsList.setAdapter(mChatRoomsAdapter);

		//Initialize the LayoutManager
		RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(mContext);
		mChatRoomsList.setLayoutManager(layoutManager);




		mWaitLayout.setVisibility(View.GONE);


		//Actions allowed by swipe buttons

		final SwipeController swipeController = new SwipeController(new SwipeControllerActions() {
			@Override
			public void onLeftClicked(int position) {
				super.onLeftClicked(position);
			}

			@Override
			public void onRightClicked(int position) {
				mChatRoomsAdapter.removeItem(position);
			}
		});

		//Initialize swipe detection

		ItemTouchHelper itemTouchhelper = new ItemTouchHelper(swipeController);
		itemTouchhelper.attachToRecyclerView(mChatRoomsList);

		//Add swipe buttons
		mChatRoomsList.addItemDecoration(new RecyclerView.ItemDecoration() {
			@Override
			public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
				swipeController.onDraw(c);
			}
		});


		// Buttons onClickListeners definitions

		mEditButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				//Start selection mode
				actionMode = getActivity().startActionMode(actionModeCallback);
			}
		});

		mNewDiscussionButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				LinphoneActivity.instance().goToChatCreator(null, null, null, false);
			}
		});

		mBackToCallButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				LinphoneActivity.instance().resetClassicMenuLayoutAndGoBackToCallIfStillRunning();
			}
		});


		//Update ChatRoomsList on change
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
			//Start selection mode
			actionMode = getActivity().startActionMode(actionModeCallback);

		}
		toggleSelection(position);
		return true;
	}


	/*Switch selection state of an item and handle
	* selection buttons visibility
	*/
	private void toggleSelection(int position) {

		mChatRoomsAdapter.toggleSelection(position);
		int count = mChatRoomsAdapter.getSelectedItemCount();
		if (count < mChatRoomsAdapter.getItemCount()) {
			mDeselectAllButton.setVisibility(View.GONE);
			mSelectAllButton.setVisibility(View.VISIBLE);

		}else{
			mSelectAllButton.setVisibility(View.GONE);
			mDeselectAllButton.setVisibility(View.VISIBLE);
		}
		mChatRoomsAdapter.notifyItemChanged(position);
		actionMode.invalidate();
	}



	//Selection mode (ActionMode)

	private class ActionModeCallback implements ActionMode.Callback {
		@SuppressWarnings("unused")
		private final String TAG = ActionModeCallback.class.getSimpleName();

		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			actionMode=mode;
			mTopBar.setVisibility(View.GONE);
			mEditTopBar.setVisibility(View.VISIBLE);

			//Transmits ActionMode current state to the adapter
			for (Integer i = 0; i <= mChatRoomsAdapter.getItemCount(); i++) {
				mChatRoomsAdapter.setEditionMode(mode);
			}

			/*
			* Inflate custom menu, example for future plans
			*mode.getMenuInflater().inflate (R.menu.edit_list_menu, menu);
			*/


			mCancelButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					actionMode.finish();
				}
			});


			/*Way to disable the sliding menu (left)
			* mSideMenu=(DrawerLayout) getActivity().findViewById(R.id.side_menu);
			* mSideMenu.setDrawerLockMode(1);
			* */


			//Add all non-selected items to the selection
			mSelectAllButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					for (Integer i = 0; i < mChatRoomsAdapter.getItemCount(); i++) {
						if (!mChatRoomsAdapter.isSelected(i)) {
							toggleSelection(i);
						}
					}
				}
			});

			//Remove all selected items from the selection
			mDeselectAllButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					for (Integer i = 0; i < mChatRoomsAdapter.getItemCount(); i++) {
						if (mChatRoomsAdapter.isSelected(i)) {
							toggleSelection(i);
						}
					}
				}
			});

			mDeleteButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					mChatRoomsAdapter.removeItems(mChatRoomsAdapter.getSelectedItems());
					actionMode.finish();
				}
			});
			return true;
		}

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			return false;
		}

		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			switch (item.getItemId()) {

				/*
				Meant to execute actions as Contextual Action Bar item is clicked,
				unused in our case as the CAB isn't used.
				No need for clickListeners.
				Example below for future evolution.

				case R.id.delete:
					return true
				*/
				default:
					return false;
			}
		}

		@Override
		public void onDestroyActionMode(ActionMode mode) {

			mChatRoomsAdapter.clearSelection();
			mTopBar.setVisibility(View.VISIBLE);
			mEditTopBar.setVisibility(View.GONE);
			actionMode = null;
			mChatRoomsAdapter.setEditionMode(actionMode);
		}
	}

	//ActionMode ending

	//Existing functions before RecyclerView conversion

	private void refreshChatRoomsList() {
		mChatRoomsAdapter.refresh();
		//mNoChatHistory.setVisibility(mChatRoomsAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
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
			mBackToCallButton.setVisibility(View.GONE);
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
	public void onContactsUpdated() {
		if (!LinphoneActivity.isInstanciated() || LinphoneActivity.instance().getCurrentFragment() != CHAT_LIST)
			return;

		ChatRoomsAdapter adapter = (ChatRoomsAdapter) mChatRoomsList.getAdapter();
		if (adapter != null) {
			adapter.notifyDataSetChanged();
		}
	}



}

