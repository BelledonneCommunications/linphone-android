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
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.List;

import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneChatMessage;
import org.linphone.core.LinphoneChatRoom;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.mediastream.Log;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.app.Fragment;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * @author Sylvain Berfini
 */
public class ChatListFragment extends Fragment implements OnClickListener, OnItemClickListener {
	private LayoutInflater mInflater;
	private List<String> mConversations, mDrafts;
	private ListView chatList;
	private TextView noChatHistory;
	private ImageView edit, selectAll, deselectAll, delete, newDiscussion, contactPicture, cancel, backInCall;
	private LinearLayout editList, topbar;
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

		editList = (LinearLayout) view.findViewById(R.id.edit_list);
		topbar = (LinearLayout) view.findViewById(R.id.top_bar);

		cancel = (ImageView) view.findViewById(R.id.cancel);
		cancel.setOnClickListener(this);

		edit = (ImageView) view.findViewById(R.id.edit);
		edit.setOnClickListener(this);
		
		newDiscussion = (ImageView) view.findViewById(R.id.new_discussion);
		newDiscussion.setOnClickListener(this);
		
		selectAll = (ImageView) view.findViewById(R.id.select_all);
		selectAll.setOnClickListener(this);

		deselectAll = (ImageView) view.findViewById(R.id.deselect_all);
		deselectAll.setOnClickListener(this);

		backInCall = (ImageView) view.findViewById(R.id.back_in_call);
		backInCall.setOnClickListener(this);

		delete = (ImageView) view.findViewById(R.id.delete);
		delete.setOnClickListener(this);
		return view;
	}

	private void selectAllList(boolean isSelectAll){
		int size = chatList.getAdapter().getCount();
		for(int i=0; i<size; i++) {
			chatList.setItemChecked(i,isSelectAll);
		}
	}

	private void removeChatsConversation(){
		int size = chatList.getAdapter().getCount();
		for(int i=0; i<size; i++) {
			if(chatList.isItemChecked(i)){
				View item = chatList.getAdapter().getView(i, null, null);
				if(item != null) {
					LinphoneChatRoom chatroom = LinphoneManager.getLc().getOrCreateChatRoom(item.getTag().toString());
					if (chatroom != null)
						chatroom.deleteHistory();
				}
			}
		}
		LinphoneActivity.instance().updateMissedChatCount();
	}

	public void quitEditMode(){
		isEditMode = false;
		editList.setVisibility(View.GONE);
		topbar.setVisibility(View.VISIBLE);
		refresh();
		if(getResources().getBoolean(R.bool.isTablet)){
			displayFirstChat();
		}
	}

	public int getNbItemsChecked(){
		int size = chatList.getAdapter().getCount();
		int nb = 0;
		for(int i=0; i<size; i++) {
			if(chatList.isItemChecked(i)) {
				nb ++;
			}
		}
		return nb;
	}

	public void enabledDeleteButton(Boolean enabled){
		if(enabled){
			delete.setEnabled(true);
		} else {
			if (getNbItemsChecked() == 0){
				delete.setEnabled(false);
			}
		}
	}
	
	private void hideAndDisplayMessageIfNoChat() {
		if (mConversations.size() == 0 && mDrafts.size() == 0) {
			noChatHistory.setVisibility(View.VISIBLE);
			chatList.setVisibility(View.GONE);
			edit.setEnabled(false);
		} else {
			noChatHistory.setVisibility(View.GONE);
			chatList.setVisibility(View.VISIBLE);
			chatList.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
			chatList.setAdapter(new ChatListAdapter());
			edit.setEnabled(true);
		}
	}
	
	public void refresh() {
		mConversations = LinphoneActivity.instance().getChatList();
		mDrafts = LinphoneActivity.instance().getDraftChatList();
		mConversations.removeAll(mDrafts);
		hideAndDisplayMessageIfNoChat();
	}

	public void displayFirstChat(){
		if(mConversations.size() > 0) {
			LinphoneActivity.instance().displayChat(mConversations.get(0));
		} else {
			LinphoneActivity.instance().displayEmptyFragment();
		}
	}

	@Override
	public void onResume() {
		super.onResume();

		if (LinphoneManager.getLc().getCallsNb() > 0) {
			backInCall.setVisibility(View.VISIBLE);
		} else {
			backInCall.setVisibility(View.INVISIBLE);
		}
		
		if (LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().selectMenu(FragmentsAvailable.CHAT_LIST);
			LinphoneActivity.instance().updateChatListFragment(this);
			LinphoneActivity.instance().hideTabBar(false);
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

		if (id == R.id.back_in_call) {
			LinphoneActivity.instance().resetClassicMenuLayoutAndGoBackToCallIfStillRunning();
			return;
		}

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
			Button delete = (Button) dialog.findViewById(R.id.delete_button);
			Button cancel = (Button) dialog.findViewById(R.id.cancel);

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
		else if (id == R.id.new_discussion) {
			LinphoneActivity.instance().displayChat(null);
			/*String sipUri = fastNewChat.getText().toString();
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

			}*/
		}
	}

	@Override
	public void onItemClick(AdapterView<?> adapter, View view, int position, long id) {
		String sipUri = (String) view.getTag();

		if (LinphoneActivity.isInstanciated() && !isEditMode) {
			LinphoneActivity.instance().displayChat(sipUri);
		}
	}
	
	private String saveImageAsFile(int id, Bitmap bm) {
		try {
			String path = Environment.getExternalStorageDirectory().toString();
			if (!path.endsWith("/"))
				path += "/";
			path += "Pictures/";
			File directory = new File(path);
			directory.mkdirs();
			
			String filename = getString(R.string.picture_name_format).replace("%s", String.valueOf(id));
			File file = new File(path, filename);
			
			OutputStream fOut = null;
			fOut = new FileOutputStream(file);

			bm.compress(Bitmap.CompressFormat.JPEG, 100, fOut);
			fOut.flush();
			fOut.close();
			
			return path + filename;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	class ChatListAdapter extends BaseAdapter {

		ChatListAdapter() {}
		
		public int getCount() {
			return mConversations.size();
		}

		public Object getItem(int position) {
			return position;
		}

		public long getItemId(int position) {
			return position;
		}

		public View getView(final int position, View convertView, ViewGroup parent) {
			View view = null;
			
			if (convertView != null) {
				view = convertView;
			} else {
				view = mInflater.inflate(R.layout.chatlist_cell, parent, false);
			}

			String sipUri = mConversations.get(position);
			view.setTag(sipUri);
			
			LinphoneAddress address;
			try {
				address = LinphoneCoreFactory.instance().createLinphoneAddress(sipUri);
			} catch (LinphoneCoreException e) {
				Log.e("Chat view cannot parse address",e);
				return view;
			}

			Contact contact = ContactsManager.getInstance().findContactWithAddress(getActivity().getContentResolver(), address);
			String message = "";
			Long time;

			TextView lastMessageView = (TextView) view.findViewById(R.id.lastMessage);
			TextView date = (TextView) view.findViewById(R.id.date);
			TextView displayName = (TextView) view.findViewById(R.id.sipUri);
			TextView unreadMessages = (TextView) view.findViewById(R.id.unreadMessages);
			CheckBox select = (CheckBox) view.findViewById(R.id.delete_chatroom);
			ImageView contactPicture = (ImageView) view.findViewById(R.id.contact_picture);

			LinphoneChatRoom chatRoom = LinphoneManager.getLc().getChatRoom(address);
			int unreadMessagesCount = chatRoom.getUnreadMessagesCount();
			LinphoneChatMessage[] history = chatRoom.getHistory(1);
			LinphoneChatMessage msg = history[0];

			if(msg.getFileTransferInformation() != null || msg.getExternalBodyUrl() != null || msg.getAppData() != null ){
				lastMessageView.setBackgroundResource(R.drawable.chat_file_message);
				time = msg.getTime();
				date.setText(LinphoneUtils.timestampToHumanDate(getActivity(),time,getString(R.string.messages_list_date_format)));
				lastMessageView.setText("");
			} else if (msg.getText() != null && msg.getText().length() > 0 ){
				message = msg.getText();
				lastMessageView.setBackgroundResource(0);
				time = msg.getTime();
				date.setText(LinphoneUtils.timestampToHumanDate(getActivity(),time,getString(R.string.messages_list_date_format)));
				lastMessageView.setText(message);
			}

			displayName.setSelected(true); // For animation
			displayName.setText(contact == null ? LinphoneUtils.getAddressDisplayName(address) : contact.getName());


			if(contact != null){
				LinphoneUtils.setImagePictureFromUri(view.getContext(), contactPicture, contact.getPhotoUri(), contact.getThumbnailUri());
			} else {
				contactPicture.setImageResource(R.drawable.avatar);
			}

			if (unreadMessagesCount > 0) {
				unreadMessages.setVisibility(View.VISIBLE);
				unreadMessages.setText(String.valueOf(unreadMessagesCount));
				if(unreadMessagesCount > 99){
					unreadMessages.setTextSize(12);
				}
				displayName.setTypeface(null, Typeface.BOLD);
			} else {
				unreadMessages.setVisibility(View.GONE);
				displayName.setTypeface(null, Typeface.NORMAL);
			}

			if (isEditMode) {
				unreadMessages.setVisibility(View.GONE);
				select.setVisibility(View.VISIBLE);
				select.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
					@Override
					public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
						chatList.setItemChecked(position, b);
						if (getNbItemsChecked() == getCount()) {
							deselectAll.setVisibility(View.VISIBLE);
							selectAll.setVisibility(View.GONE);
							enabledDeleteButton(true);
						} else {
							if (getNbItemsChecked() == 0) {
								deselectAll.setVisibility(View.GONE);
								selectAll.setVisibility(View.VISIBLE);
								enabledDeleteButton(false);
							} else {
								deselectAll.setVisibility(View.GONE);
								selectAll.setVisibility(View.VISIBLE);
								enabledDeleteButton(true);
							}
						}
					}
				});
				if(chatList.isItemChecked(position)) {
					select.setChecked(true);
				} else {
					select.setChecked(false);
				}
			} else {
				if (unreadMessagesCount > 0) {
					unreadMessages.setVisibility(View.VISIBLE);
				}
			}
			return view;
		}
	}
}


