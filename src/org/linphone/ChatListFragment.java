package org.linphone;
/*
ChatListFragment.java
Copyright (C) 2012  Belledonne Communications, Grenoble, France

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
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
*/
import java.util.List;

import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCoreFactory;

import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

/**
 * @author Sylvain Berfini
 */
public class ChatListFragment extends Fragment implements OnClickListener, OnItemClickListener {
	private Handler mHandler = new Handler();
	
	private LayoutInflater mInflater;
	private List<String> mConversations, mDrafts;
	private ListView chatList;
	private TextView edit, ok, newDiscussion, noChatHistory;
	private ImageView clearFastChat;
	private EditText fastNewChat;
	private boolean isEditMode = false;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		mInflater = inflater;

		View view = inflater.inflate(R.layout.chatlist, container, false);
		chatList = (ListView) view.findViewById(R.id.chatList);
		chatList.setOnItemClickListener(this);
		registerForContextMenu(chatList);
		
		noChatHistory = (TextView) view.findViewById(R.id.noChatHistory);
		
		edit = (TextView) view.findViewById(R.id.edit);
		edit.setOnClickListener(this);
		
		newDiscussion = (TextView) view.findViewById(R.id.newDiscussion);
		newDiscussion.setOnClickListener(this);
		
		ok = (TextView) view.findViewById(R.id.ok);
		ok.setOnClickListener(this);
		
		clearFastChat = (ImageView) view.findViewById(R.id.clearFastChatField);
		clearFastChat.setOnClickListener(this);
		
		fastNewChat = (EditText) view.findViewById(R.id.newFastChat);
		
		return view;
	}
	
	private void hideAndDisplayMessageIfNoChat() {
		if (mConversations.size() == 0 && mDrafts.size() == 0) {
			noChatHistory.setVisibility(View.VISIBLE);
			chatList.setVisibility(View.GONE);
		} else {
			noChatHistory.setVisibility(View.GONE);
			chatList.setVisibility(View.VISIBLE);
			chatList.setAdapter(new ChatListAdapter());
		}
	}
	
	public void refresh() {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				mConversations = LinphoneActivity.instance().getChatList();
				mDrafts = LinphoneActivity.instance().getDraftChatList();
				mConversations.removeAll(mDrafts);
				hideAndDisplayMessageIfNoChat();
			}
		});
	}

	@Override
	public void onResume() {
		super.onResume();
		
		if (LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().selectMenu(FragmentsAvailable.CHATLIST);
			LinphoneActivity.instance().updateChatListFragment(this);
		}
		
		refresh();
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		menu.add(0, v.getId(), 0, getString(R.string.delete));
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		if (info == null || info.targetView == null) {
			return false;
		}
		String sipUri = (String) info.targetView.getTag();
		
		LinphoneActivity.instance().removeFromChatList(sipUri);
		mConversations = LinphoneActivity.instance().getChatList();
		mDrafts = LinphoneActivity.instance().getDraftChatList();
		mConversations.removeAll(mDrafts);
		hideAndDisplayMessageIfNoChat();
		return true;
	}
	
	@Override
	public void onClick(View v) {
		int id = v.getId();
		
		if (id == R.id.clearFastChatField) {
			fastNewChat.setText("");
		}
		else if (id == R.id.ok) {
			edit.setVisibility(View.VISIBLE);
			ok.setVisibility(View.GONE);
			isEditMode = false;
			hideAndDisplayMessageIfNoChat();
		}
		else if (id == R.id.edit) {
			edit.setVisibility(View.GONE);
			ok.setVisibility(View.VISIBLE);
			isEditMode = true;
			hideAndDisplayMessageIfNoChat();
		}
		else if (id == R.id.newDiscussion) {
			String sipUri = fastNewChat.getText().toString();
			if (sipUri.equals("")) {
				LinphoneActivity.instance().displayContacts(true);
			} else {
				if (!LinphoneUtils.isSipAddress(sipUri)) {
					if (LinphoneManager.getLc().getDefaultProxyConfig() == null) {
						return;
					}
					sipUri = sipUri + "@" + LinphoneManager.getLc().getDefaultProxyConfig().getDomain();
				}
				if (!LinphoneUtils.isStrictSipAddress(sipUri)) {
					sipUri = "sip:" + sipUri;
				}
				LinphoneActivity.instance().displayChat(sipUri);
			}
		}
	}

	@Override
	public void onItemClick(AdapterView<?> adapter, View view, int position, long id) {
		String sipUri = (String) view.getTag();
		
		if (LinphoneActivity.isInstanciated() && !isEditMode) {
			LinphoneActivity.instance().displayChat(sipUri);
		} else if (LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().removeFromChatList(sipUri);
			LinphoneActivity.instance().removeFromDrafts(sipUri);
			
			mConversations = LinphoneActivity.instance().getChatList();
			mDrafts = LinphoneActivity.instance().getDraftChatList();
			mConversations.removeAll(mDrafts);
			hideAndDisplayMessageIfNoChat();
			
			LinphoneActivity.instance().updateMissedChatCount();
		}
	}
	
	class ChatListAdapter extends BaseAdapter {
		ChatListAdapter() {
		}
		
		public int getCount() {
			return mConversations.size() + mDrafts.size();
		}

		public Object getItem(int position) {
			return position;
		}

		public long getItemId(int position) {
			return position;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			View view = null;
			
			if (convertView != null) {
				view = convertView;
			} else {
				view = mInflater.inflate(R.layout.chatlist_cell, parent, false);
				
			}
			String contact;
			boolean isDraft = false;
			if (position >= mDrafts.size()) {
				contact = mConversations.get(position - mDrafts.size());
			} else {
				contact = mDrafts.get(position);
				isDraft = true;
			}
			view.setTag(contact);
			int unreadMessagesCount = LinphoneActivity.instance().getChatStorage().getUnreadMessageCount(contact);
			
			LinphoneAddress address = LinphoneCoreFactory.instance().createLinphoneAddress(contact);
			LinphoneUtils.findUriPictureOfContactAndSetDisplayName(address, view.getContext().getContentResolver());

			List<ChatMessage> messages = LinphoneActivity.instance().getChatMessages(contact);
			if (messages != null && messages.size() > 0) {
				int iterator = messages.size() - 1;
				ChatMessage lastMessage = null;
				
				while (iterator >= 0) {
					lastMessage = messages.get(iterator);
					if (lastMessage.getMessage() == null) {
						iterator--;
					} else {
						iterator = -1;
					}
				}
				
				String message = "";
				message = (lastMessage == null || lastMessage.getMessage() == null) ? "" : lastMessage.getMessage();
				TextView lastMessageView = (TextView) view.findViewById(R.id.lastMessage);
				lastMessageView.setText(message);
			}
			
			TextView sipUri = (TextView) view.findViewById(R.id.sipUri);
			sipUri.setSelected(true); // For animation
			
			if (getResources().getBoolean(R.bool.only_display_username_if_unknown) && address.getDisplayName() != null && LinphoneUtils.isSipAddress(address.getDisplayName())) {
				address.setDisplayName(LinphoneUtils.getUsernameFromAddress(address.getDisplayName()));
			} else if (getResources().getBoolean(R.bool.only_display_username_if_unknown) && LinphoneUtils.isSipAddress(contact)) {
				contact = LinphoneUtils.getUsernameFromAddress(contact);
			} 
			
			sipUri.setText(address.getDisplayName() == null ? contact : address.getDisplayName());
			if (isDraft) {
				view.findViewById(R.id.draft).setVisibility(View.VISIBLE);
			}
			
			
			ImageView delete = (ImageView) view.findViewById(R.id.delete);
			TextView unreadMessages = (TextView) view.findViewById(R.id.unreadMessages);
			
			if (unreadMessagesCount > 0) {
				unreadMessages.setVisibility(View.VISIBLE);
				unreadMessages.setText(String.valueOf(unreadMessagesCount));
			} else {
				unreadMessages.setVisibility(View.GONE);
			}
			
			if (isEditMode) {
				delete.setVisibility(View.VISIBLE);
			} else {
				delete.setVisibility(View.INVISIBLE);
			}
			
			return view;
		}
	}
}


