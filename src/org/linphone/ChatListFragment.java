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
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

/**
 * @author Sylvain Berfini
 */
public class ChatListFragment extends Fragment implements OnClickListener, OnItemClickListener {
	private LayoutInflater mInflater;
	private List<String> mConversations;
	private ListView chatList;
	private ImageView edit, ok, newDiscussion;
	private boolean isEditMode = false;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		mInflater = inflater;

		View view = inflater.inflate(R.layout.chatlist, container, false);
		chatList = (ListView) view.findViewById(R.id.chatList);
		chatList.setOnItemClickListener(this);
		registerForContextMenu(chatList);
		
		edit = (ImageView) view.findViewById(R.id.edit);
		edit.setOnClickListener(this);
		newDiscussion = (ImageView) view.findViewById(R.id.newDiscussion);
		newDiscussion.setOnClickListener(this);
		ok = (ImageView) view.findViewById(R.id.ok);
		ok.setOnClickListener(this);
		
		return view;
	}

	@Override
	public void onResume() {
		super.onResume();
		
		if (LinphoneActivity.isInstanciated())
			LinphoneActivity.instance().selectMenu(FragmentsAvailable.CHATLIST);
		
		mConversations = LinphoneActivity.instance().getChatList();
		chatList.setAdapter(new ChatListAdapter());
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
		chatList.setAdapter(new ChatListAdapter());
		return true;
	}
	
	@Override
	public void onClick(View v) {
		int id = v.getId();
		
		if (id == R.id.ok) {
			edit.setVisibility(View.VISIBLE);
			ok.setVisibility(View.GONE);
			isEditMode = false;
			chatList.setAdapter(new ChatListAdapter());
		}
		else if (id == R.id.edit) {
			edit.setVisibility(View.GONE);
			ok.setVisibility(View.VISIBLE);
			isEditMode = true;
			chatList.setAdapter(new ChatListAdapter());
		}
		else if (id == R.id.newDiscussion) {
			LinphoneActivity.instance().displayContacts();
		}
	}

	@Override
	public void onItemClick(AdapterView<?> adapter, View view, int position, long id) {
		String sipUri = (String) view.getTag();
		
		if (LinphoneActivity.isInstanciated() && !isEditMode) {
			LinphoneActivity.instance().displayChat(sipUri);
		} else if (LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().removeFromChatList(sipUri);
			mConversations = LinphoneActivity.instance().getChatList();
			chatList.setAdapter(new ChatListAdapter());
			LinphoneActivity.instance().updateMissedChatCount();
		}
	}
	
	class ChatListAdapter extends BaseAdapter {
		ChatListAdapter() {
		}
		
		public int getCount() {
			return mConversations.size();
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
			String contact = mConversations.get(position);
			view.setTag(contact);
			
			LinphoneAddress address = LinphoneCoreFactory.instance().createLinphoneAddress(contact);
			LinphoneUtils.findUriPictureOfContactAndSetDisplayName(address, view.getContext().getContentResolver());
			
			TextView sipUri = (TextView) view.findViewById(R.id.sipUri);
			
			if (getResources().getBoolean(R.bool.only_display_username_if_unknown) && LinphoneUtils.isSipAddress(address.getDisplayName())) {
				address.setDisplayName(LinphoneUtils.getUsernameFromAddress(address.getDisplayName()));
			} else if (getResources().getBoolean(R.bool.only_display_username_if_unknown) && LinphoneUtils.isSipAddress(contact)) {
				contact = LinphoneUtils.getUsernameFromAddress(contact);
			} 
			
			sipUri.setText(address.getDisplayName() == null ? contact : address.getDisplayName());
			
			ImageView delete, detail;
			delete = (ImageView) view.findViewById(R.id.delete);
			detail = (ImageView) view.findViewById(R.id.detail);
			
			if (isEditMode) {
				delete.setVisibility(View.VISIBLE);
				detail.setVisibility(View.GONE);
			} else {
				delete.setVisibility(View.GONE);
				detail.setVisibility(View.VISIBLE);
			}
			
			return view;
		}
	}
}


