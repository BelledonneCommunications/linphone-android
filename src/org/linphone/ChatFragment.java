package org.linphone;
/*
ChatFragment.java
Copyright (C) 2015  Belledonne Communications, Grenoble, France

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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;

import android.app.Dialog;
import android.app.Fragment;
import android.graphics.Matrix;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Inflater;


import org.linphone.compatibility.Compatibility;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneBuffer;
import org.linphone.core.LinphoneChatMessage;
import org.linphone.core.LinphoneChatRoom;
import org.linphone.core.LinphoneContent;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneChatMessage.State;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneCoreListenerBase;
import org.linphone.core.LinphoneFriend;
import org.linphone.core.PresenceActivityType;
import org.linphone.mediastream.Log;
import org.linphone.ui.BubbleChat;

import android.media.ExifInterface;
import android.support.v4.content.CursorLoader;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SectionIndexer;
import android.widget.TextView;
import android.widget.Toast;

public class ChatFragment extends Fragment implements OnClickListener, LinphoneChatMessage.LinphoneChatMessageListener {
	private static ChatFragment instance;

	private static final int ADD_PHOTO = 1337;
	private static final int MENU_DELETE_MESSAGE = 0;
	private static final int MENU_PICTURE_SMALL = 2;
	private static final int MENU_PICTURE_MEDIUM = 3;
	private static final int MENU_PICTURE_LARGE = 4;
	private static final int MENU_PICTURE_REAL = 5;
	private static final int MENU_COPY_TEXT = 6;
	private static final int MENU_RESEND_MESSAGE = 7;
	private static final int SIZE_SMALL = 500;
	private static final int SIZE_MEDIUM = 1000;
	private static final int SIZE_LARGE = 1500;
	private static final int SIZE_MAX = 2048;

	private LinphoneChatRoom chatRoom;
	private String sipUri;
	private String displayName;
	private String pictureUri;
	private EditText message;
	private ImageView cancelUpload, edit, selectAll, deselectAll, startCall, delete, sendImage, sendMessage, cancel;
	private TextView contactName, remoteComposing;
	private ImageView back;
	private AutoCompleteTextView searchContactField;
	private RelativeLayout uploadLayout, textLayout, topBar, editList;
	private ListView messagesList;

	private ProgressBar progressBar;
	private boolean isEditMode = false;
	private Contact contact;
	private Uri imageToUploadUri;
	private String filePathToUpload;
	private TextWatcher textWatcher;
	private ViewTreeObserver.OnGlobalLayoutListener keyboardListener;
	private ChatMessageAdapter adapter;
	
	private LinphoneCoreListenerBase mListener;
	private ByteArrayInputStream mUploadingImageStream;
	private LinphoneChatMessage currentMessageInFileTransferUploadState;
	private boolean newChatConversation = false;

	public static boolean isInstanciated() {
		return instance != null;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		instance = this;
		View view = inflater.inflate(R.layout.chat, container, false);

		LinphoneManager.addListener(this);
		// Retain the fragment across configuration changes
		setRetainInstance(true);

		if(getArguments() == null || getArguments().getString("SipUri") == null) {
			newChatConversation = true;
		} else {
			//Retrieve parameter from intent
			sipUri = getArguments().getString("SipUri");
			displayName = getArguments().getString("DisplayName");
			pictureUri = getArguments().getString("PictureUri");
		}

		//Initialize UI
		contactName = (TextView) view.findViewById(R.id.contact_name);
		//contactPicture = (ImageView) view.findViewById(R.id.contactPicture);
		messagesList = (ListView) view.findViewById(R.id.chatMessageList);
		searchContactField = (AutoCompleteTextView) view.findViewById(R.id.searchContactField);

		textLayout = (RelativeLayout) view.findViewById(R.id.messageLayout);
		topBar = (RelativeLayout) view.findViewById(R.id.top_bar);

		sendMessage = (ImageView) view.findViewById(R.id.sendMessage);
		sendMessage.setOnClickListener(this);

		remoteComposing = (TextView) view.findViewById(R.id.remoteComposing);
		remoteComposing.setVisibility(View.GONE);

		uploadLayout = (RelativeLayout) view.findViewById(R.id.uploadLayout);
		uploadLayout.setVisibility(View.GONE);

		editList = (RelativeLayout) view.findViewById(R.id.edit_list);

		cancel = (ImageView) view.findViewById(R.id.cancel);
		cancel.setOnClickListener(this);

		edit = (ImageView) view.findViewById(R.id.edit);
		edit.setOnClickListener(this);

		startCall = (ImageView) view.findViewById(R.id.start_call);
		startCall.setOnClickListener(this);

		selectAll = (ImageView) view.findViewById(R.id.select_all);
		selectAll.setOnClickListener(this);

		deselectAll = (ImageView) view.findViewById(R.id.deselect_all);
		deselectAll.setOnClickListener(this);

		delete = (ImageView) view.findViewById(R.id.delete);
		delete.setOnClickListener(this);

		if (newChatConversation) {
			messagesList.setVisibility(View.GONE);
			searchContactField.setVisibility(View.VISIBLE);
			searchContactField.setAdapter(new SearchContactsListAdapter(ContactsManager.getInstance().getAllContacts(), null, inflater));
			edit.setVisibility(View.INVISIBLE);
			startCall.setVisibility(View.INVISIBLE);
		} else {
			displayChatHeader(displayName, pictureUri);
			LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
			if (lc != null) {
				chatRoom = lc.getOrCreateChatRoom(sipUri);
				chatRoom.markAsRead();
			}
		}

		LinphoneAddress lAddress;
		try {
			lAddress = LinphoneCoreFactory.instance().createLinphoneAddress(sipUri);
			contact = ContactsManager.getInstance().findContactWithAddress(getActivity().getContentResolver(), lAddress);
		} catch (Exception e){

		}

		//Manage multiline
		message = (EditText) view.findViewById(R.id.message);
		if (!getResources().getBoolean(R.bool.allow_chat_multiline)) {
			message.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE);
			message.setMaxLines(1);
		}

		sendImage = (ImageView) view.findViewById(R.id.sendPicture);
		if (!getResources().getBoolean(R.bool.disable_chat_send_file)) {
			sendImage.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					pickImage();
				}
			});
			//registerForContextMenu(sendImage);
		} else {
			sendImage.setEnabled(false);
		}

		back = (ImageView) view.findViewById(R.id.back);
		if (back != null) {
			back.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					getFragmentManager().popBackStackImmediate();
				}
			});
		}

		cancelUpload = (ImageView) view.findViewById(R.id.cancelUpload);
		cancelUpload.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (currentMessageInFileTransferUploadState != null) {
					uploadLayout.setVisibility(View.GONE);
					textLayout.setVisibility(View.VISIBLE);
					progressBar.setProgress(0);
					currentMessageInFileTransferUploadState.cancelFileTransfer();
					currentMessageInFileTransferUploadState = null;
					LinphoneManager.getInstance().setUploadPendingFileMessage(null);
				}
			}
		});
		
		mListener = new LinphoneCoreListenerBase(){
			@Override
			public void messageReceived(LinphoneCore lc, LinphoneChatRoom cr, LinphoneChatMessage message) {
				LinphoneAddress from = cr.getPeerAddress();
				if (from.asStringUriOnly().equals(sipUri)) {
					invalidate();
					messagesList.setSelection(adapter.getCount()-1);
				}
			}
			
			@Override
			public void isComposingReceived(LinphoneCore lc, LinphoneChatRoom room) {
				if (chatRoom != null && room != null && chatRoom.getPeerAddress().asStringUriOnly().equals(room.getPeerAddress().asStringUriOnly())) {
					remoteComposing.setVisibility(chatRoom.isRemoteComposing() ? View.VISIBLE : View.GONE);
				}
			}
		};

		textWatcher = new TextWatcher() {
			public void afterTextChanged(Editable arg0) {}

			public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {}

			public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
				if (message.getText().toString().equals("")) {
					sendMessage.setEnabled(false);
				} else {
					if (chatRoom != null)
						chatRoom.compose();
					sendMessage.setEnabled(true);
				}
			}
		};

		// Force hide keyboard
		getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
		return view;
	}

	public static ChatFragment instance() {
		return instance;
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putString("messageDraft", message.getText().toString());

		super.onSaveInstanceState(outState);
	}

	private void addVirtualKeyboardVisiblityListener() {
		keyboardListener = new ViewTreeObserver.OnGlobalLayoutListener() {
			@Override
			public void onGlobalLayout() {
			Rect visibleArea = new Rect();
			getActivity().getWindow().getDecorView().getWindowVisibleDisplayFrame(visibleArea);

			int heightDiff = getActivity().getWindow().getDecorView().getRootView().getHeight() - (visibleArea.bottom - visibleArea.top);
				if (heightDiff > 200) {
					showKeyboardVisibleMode();
				} else {
					hideKeyboardVisibleMode();
				}
			}
		};
		getActivity().getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(keyboardListener);
	}

	private void removeVirtualKeyboardVisiblityListener() {
		Compatibility.removeGlobalLayoutListener(getActivity().getWindow().getDecorView().getViewTreeObserver(), keyboardListener);
	}

	public void showKeyboardVisibleMode() {
		boolean isOrientationLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
		if (isOrientationLandscape && topBar != null) {
			topBar.setVisibility(View.GONE);
		}
		//contactPicture.setVisibility(View.GONE);
	}

	public void hideKeyboardVisibleMode() {
		boolean isOrientationLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
		//contactPicture.setVisibility(View.VISIBLE);
		if (isOrientationLandscape && topBar != null) {
			topBar.setVisibility(View.VISIBLE);
		}
	}

	class ChatMessageAdapter extends BaseAdapter {
		LinphoneChatMessage[] history;
		Context context;

		public ChatMessageAdapter(Context context, LinphoneChatMessage[] history) {
			this.history = history;
			this.context = context;
		}

		public void refreshHistory() {
			this.history = chatRoom.getHistory();
		}

		@Override
		public int getCount() {
			return history.length;
		}

		@Override
		public LinphoneChatMessage getItem(int position) {
			return history[position];
		}

		@Override
		public long getItemId(int position) {
			return history[position].getStorageId();
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			LinphoneChatMessage message = history[position];
			
			BubbleChat bubble = new BubbleChat(context, message, contact);
			View v = bubble.getView();

			registerForContextMenu(v);
			RelativeLayout rlayout = new RelativeLayout(context);

	 		if(message.isOutgoing()){
				RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
				layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
				layoutParams.setMargins(100, 10, 10, 10);
				v.setLayoutParams(layoutParams);
			} else {
				RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
				layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
				layoutParams.setMargins(10, 10, 100, 10);
				v.setLayoutParams(layoutParams);
			}
			rlayout.addView(v);
			return rlayout;
		}
	}

	public void dispayMessageList() {
		adapter = new ChatMessageAdapter(getActivity(), chatRoom.getHistory());
		messagesList.setAdapter(adapter);
		adapter.notifyDataSetChanged();
	}

	private void displayChatHeader(String displayName, String pictureUri) {
		if(contact != null) {
			contactName.setText(contact.getName());
		} else if (displayName == null && getResources().getBoolean(R.bool.only_display_username_if_unknown) && LinphoneUtils.isSipAddress(sipUri)) {
			contactName.setText(LinphoneUtils.getUsernameFromAddress(sipUri));
		} else if (displayName == null) {
			contactName.setText(sipUri);
		} else {
			contactName.setText(displayName);
		}
	}

	public void changeDisplayedChat(String newSipUri, String displayName, String pictureUri) {
		this.sipUri = newSipUri;
		this.displayName = displayName;
		this.pictureUri = pictureUri;
		
		if (!message.getText().toString().equals("") && LinphoneActivity.isInstanciated()) {
			ChatStorage chatStorage = LinphoneActivity.instance().getChatStorage();
			if (chatStorage.getDraft(sipUri) == null) {
				chatStorage.saveDraft(sipUri, message.getText().toString());
			} else {
				chatStorage.updateDraft(sipUri, message.getText().toString());
			}
		} else if (LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().getChatStorage().deleteDraft(sipUri);
		}

		if (LinphoneActivity.isInstanciated()) {
			String draft = LinphoneActivity.instance().getChatStorage().getDraft(sipUri);
			if (draft == null)
				draft = "";
			message.setText(draft);
		}

		LinphoneAddress lAddress;
		try {
			lAddress = LinphoneCoreFactory.instance().createLinphoneAddress(sipUri);
			contact = ContactsManager.getInstance().findContactWithAddress(getActivity().getContentResolver(), lAddress);
		} catch (Exception e){

		}

		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			chatRoom = lc.getOrCreateChatRoom(sipUri);
			//Only works if using liblinphone storage
			chatRoom.markAsRead();
		}

		displayChatHeader(displayName, pictureUri);
		dispayMessageList();
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
		if (v.getId() == R.id.sendPicture) {
			menu.add(0, MENU_PICTURE_SMALL, 0, getString(R.string.share_picture_size_small));
			menu.add(0, MENU_PICTURE_MEDIUM, 0, getString(R.string.share_picture_size_medium));
			menu.add(0, MENU_PICTURE_LARGE, 0, getString(R.string.share_picture_size_large));
			//			Not a good idea, very big pictures cause Out of Memory exceptions, slow display, ...
			//			menu.add(0, MENU_PICTURE_REAL, 0, getString(R.string.share_picture_size_real));
		} else {
			menu.add(v.getId(), MENU_DELETE_MESSAGE, 0, getString(R.string.delete));
			menu.add(v.getId(), MENU_COPY_TEXT, 0, getString(R.string.copy_text));
		}

		LinphoneChatMessage msg = getMessageForId(v.getId());
		if (msg != null && msg.getStatus() == LinphoneChatMessage.State.NotDelivered) {
			menu.add(v.getId(), MENU_RESEND_MESSAGE, 0, getString(R.string.retry));
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case MENU_DELETE_MESSAGE:
				if (chatRoom != null) {
					LinphoneChatMessage message = getMessageForId(item.getGroupId());
					if (message != null) {
						chatRoom.deleteMessage(message);
						invalidate();
					}
				}
				break;
			case MENU_COPY_TEXT:
				copyTextMessageToClipboard(item.getGroupId());
				break;
			case MENU_RESEND_MESSAGE:
				resendMessage(item.getGroupId());
				break;
			case MENU_PICTURE_SMALL:
				sendImageMessage(filePathToUpload, SIZE_SMALL);
				break;
			case MENU_PICTURE_MEDIUM:
				sendImageMessage(filePathToUpload, SIZE_MEDIUM);
				break;
			case MENU_PICTURE_LARGE:
				sendImageMessage(filePathToUpload, SIZE_LARGE);
				break;
			case MENU_PICTURE_REAL:
				sendImageMessage(filePathToUpload, SIZE_MAX);
				break;
		}
		return true;
	}

	@Override
	public void onPause() {
		message.removeTextChangedListener(textWatcher);
		removeVirtualKeyboardVisiblityListener();


		LinphoneService.instance().removeMessageNotification();

		if (LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().updateChatFragment(null);
		}

		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			lc.removeListener(mListener);
		}

		LinphoneManager.removeListener(this);

		onSaveInstanceState(getArguments());

	//	uploadLayout.setVisibility(View.GONE);
	//	textLayout.setVisibility(View.VISIBLE);
		//progressBar.setProgress(0);

		//Hide keybord
		InputMethodManager imm = (InputMethodManager)getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(message.getWindowToken(), 0);
		super.onPause();
	}

	@SuppressLint("UseSparseArrays")
	@Override
	public void onResume() {
		message.addTextChangedListener(textWatcher);
		addVirtualKeyboardVisiblityListener();

		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			lc.addListener(mListener);
		}

		if (LinphoneActivity.isInstanciated()) {
			if (getResources().getBoolean(R.bool.show_statusbar_only_on_dialer)) {
				LinphoneActivity.instance().hideStatusBar();
			}
			LinphoneActivity.instance().updateChatFragment(this);
		}

		LinphoneAddress lAddress;
		try {
			lAddress = LinphoneCoreFactory.instance().createLinphoneAddress(sipUri);
			contact = ContactsManager.getInstance().findContactWithAddress(getActivity().getContentResolver(), lAddress);
		} catch (Exception e){

		}

		LinphoneManager.addListener(this);

		final LinphoneChatMessage msg = LinphoneManager.getInstance().getMessageUploadPending();
		if(msg != null && msg.getTo().asString().equals(sipUri)){
			//uploadLayout.setVisibility(View.VISIBLE);
		//	textLayout.setVisibility(View.GONE);
		//	if(msg.getFileTransferInformation() != null){
		//		progressBar.setProgress(msg.getFileTransferInformation().getRealSize());
		//	}

	/*		cancelUpload.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					uploadLayout.setVisibility(View.GONE);
					textLayout.setVisibility(View.VISIBLE);
					progressBar.setProgress(0);
					msg.cancelFileTransfer();
					LinphoneManager.getInstance().setUploadPendingFileMessage(null);

				}
			});*/
		}

		String draft = getArguments().getString("messageDraft");
		message.setText(draft);


		if(!newChatConversation) {
			remoteComposing.setVisibility(chatRoom.isRemoteComposing() ? View.VISIBLE : View.GONE);
			dispayMessageList();
		}
		super.onResume();
	}

	private void selectAllList(boolean isSelectAll){
		//TODO
	}

	public void quitEditMode(){
		isEditMode = false;
		editList.setVisibility(View.GONE);
		topBar.setVisibility(View.VISIBLE);
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();

		if (id == R.id.select_all) {
			deselectAll.setVisibility(View.VISIBLE);
			selectAll.setVisibility(View.GONE);
			//selectAllList(true);
			return;
		}
		if (id == R.id.deselect_all) {
			deselectAll.setVisibility(View.GONE);
			selectAll.setVisibility(View.VISIBLE);
			//selectAllList(false);
			return;
		}

		if (id == R.id.cancel) {
			quitEditMode();
			return;
		}

		if (id == R.id.delete) {
			final Dialog dialog = LinphoneActivity.instance().displayDialog(getString(R.string.delete_text));
			Button delete = (Button) dialog.findViewById(R.id.delete);
			Button cancel = (Button) dialog.findViewById(R.id.cancel);

			delete.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
				//	removeCallLogs();
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

		if(id == R.id.sendMessage){
			sendTextMessage();
		} else if (id == R.id.edit) {
			topBar.setVisibility(View.GONE);
			editList.setVisibility(View.VISIBLE);
		}
		else if (id == R.id.new_discussion) {
			//TODO call sipUri
		}
		else if(id == R.id.start_call){
			LinphoneActivity.instance().setAddresGoToDialerAndCall(sipUri, LinphoneUtils.getUsernameFromAddress(sipUri), null);
		}
	}

	private void sendTextMessage() {
		sendTextMessage(message.getText().toString());
		message.setText("");
	}

	private void sendTextMessage(String messageToSend) {
		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		boolean isNetworkReachable = lc == null ? false : lc.isNetworkReachable();

		if(newChatConversation){
			String address = searchContactField.getText().toString();
			if(address != null &&  !address.equals("")) {
				if(!address.startsWith("sip:"))
					address = "sip:" + address;
				if (!LinphoneUtils.isSipAddress(address)) {
					if (LinphoneManager.getLc().getDefaultProxyConfig() == null) {
						Log.w("Error");
					}
					address = address + "@" + LinphoneManager.getLc().getDefaultProxyConfig().getDomain();
				}

				LinphoneAddress lAddress;
				try {
					lAddress = LinphoneCoreFactory.instance().createLinphoneAddress(address);
				} catch (LinphoneCoreException e) {
					Log.e("Cannot display chat",e);
					return;
				}

				chatRoom = lc.getOrCreateChatRoom(lAddress.toString());

				if (chatRoom != null && messageToSend != null && messageToSend.length() > 0 && isNetworkReachable) {
					LinphoneChatMessage message = chatRoom.createLinphoneChatMessage(messageToSend);
					chatRoom.sendChatMessage(message);
					message.setListener(LinphoneManager.getInstance());
					Contact lContact = ContactsManager.getInstance().findContactWithAddress(getActivity().getContentResolver(), lAddress);
					if(lContact != null)
						exitNewConversationMode(lContact,lAddress.asStringUriOnly(),null);
					else
						exitNewConversationMode(null,lAddress.asStringUriOnly(),lAddress.getUserName());
				}
			}
		} else {

			if (chatRoom != null && messageToSend != null && messageToSend.length() > 0 && isNetworkReachable) {
				LinphoneChatMessage message = chatRoom.createLinphoneChatMessage(messageToSend);
				chatRoom.sendChatMessage(message);

				if (LinphoneActivity.isInstanciated()) {
					LinphoneActivity.instance().onMessageSent(sipUri, messageToSend);
				}

				message.setListener(LinphoneManager.getInstance());
				invalidate();
				Log.i("Sent message current status: " + message.getStatus());
			} else if (!isNetworkReachable && LinphoneActivity.isInstanciated()) {
				LinphoneActivity.instance().displayCustomToast(getString(R.string.error_network_unreachable), Toast.LENGTH_LONG);
			}
		}
	}

	private void sendImageMessage(String path, int imageSize) {
		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		boolean isNetworkReachable = lc == null ? false : lc.isNetworkReachable();
		invalidate();

		if (chatRoom != null && path != null && path.length() > 0 && isNetworkReachable) {
			try {
				Bitmap bm = BitmapFactory.decodeFile(path);
				if (bm != null) {
					FileUploadPrepareTask task = new FileUploadPrepareTask(getActivity(), path, imageSize);
					task.execute(bm);
				} else {
					Log.e("Error, bitmap factory can't read " + path);
				}
			} catch (RuntimeException e) {
				Log.e("Error, not enough memory to create the bitmap");
			}
		} else if (!isNetworkReachable && LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().displayCustomToast(getString(R.string.error_network_unreachable), Toast.LENGTH_LONG);
		}
	}
	
	class FileUploadPrepareTask extends AsyncTask<Bitmap, Void, byte[]> {
		private String path;
		private int imageSize;
		private ProgressDialog progressDialog;
		
		public FileUploadPrepareTask(Context context, String fileToUploadPath, int size) {
			path = fileToUploadPath;
			imageSize = size;
			//uploadLayout.setVisibility(View.VISIBLE);
			//textLayout.setVisibility(View.GONE);
			
			progressDialog = new ProgressDialog(context);
			progressDialog.setIndeterminate(true);
			progressDialog.setMessage(getString(R.string.processing_image));
			progressDialog.show();
		}

		@Override
		protected byte[] doInBackground(Bitmap... params) {
			Bitmap bm = params[0];

			if (bm.getWidth() >= bm.getHeight() && bm.getWidth() > SIZE_MAX) {
				bm = Bitmap.createScaledBitmap(bm, SIZE_MAX, (SIZE_MAX * bm.getHeight()) / bm.getWidth(), false);
			} else if (bm.getHeight() >= bm.getWidth() && bm.getHeight() > SIZE_MAX) {
				bm = Bitmap.createScaledBitmap(bm, (SIZE_MAX * bm.getWidth()) / bm.getHeight(), SIZE_MAX, false);
			}

			// Rotate the bitmap if possible/needed, using EXIF data
			try {
				if (path != null) {
					ExifInterface exif = new ExifInterface(path);
					int pictureOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0);
					Matrix matrix = new Matrix();
					if (pictureOrientation == 6) {
						matrix.postRotate(90);
					} else if (pictureOrientation == 3) {
						matrix.postRotate(180);
					} else if (pictureOrientation == 8) {
						matrix.postRotate(270);
					}
					bm = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), matrix, true);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

			ByteArrayOutputStream stream = new ByteArrayOutputStream();
			bm.compress(Bitmap.CompressFormat.PNG, 100, stream);
			byte[] byteArray = stream.toByteArray();
			return byteArray;
		}
		
		@Override
		protected void onPostExecute(byte[] result) {
			if (progressDialog != null && progressDialog.isShowing()) {
				progressDialog.dismiss();
			}
			
			mUploadingImageStream = new ByteArrayInputStream(result);
			
			LinphoneContent content = LinphoneCoreFactory.instance().createLinphoneContent("image", "jpeg", result, null);
			String fileName = path.substring(path.lastIndexOf("/") + 1);
			content.setName(fileName);
			
			LinphoneChatMessage message = chatRoom.createFileTransferMessage(content);
			message.setListener(LinphoneManager.getInstance());
			message.setAppData(path);

		 	LinphoneManager.getInstance().setUploadPendingFileMessage(message);
			LinphoneManager.getInstance().setUploadingImageStream(mUploadingImageStream);

			chatRoom.sendChatMessage(message);
			currentMessageInFileTransferUploadState = message;
		}
	}

	private LinphoneChatMessage getMessageForId(int id) {
		for (LinphoneChatMessage message : chatRoom.getHistory()) {
			if (message.getStorageId() == id) {
				return message;
			}
		}
		
		return null;
	}

	private void invalidate() {
		adapter.refreshHistory();
		adapter.notifyDataSetChanged();
		chatRoom.markAsRead();
	}

	private void resendMessage(int id) {
		LinphoneChatMessage message = getMessageForId(id);
		if (message == null)
			return;

		chatRoom.deleteMessage(getMessageForId(id));
		invalidate();

		if (message.getText() != null && message.getText().length() > 0) {
			sendTextMessage(message.getText());
		} else {
			sendImageMessage(message.getAppData(),0);
		}
	}

	private void copyTextMessageToClipboard(int id) {
		String msg = LinphoneActivity.instance().getChatStorage().getTextMessageForId(chatRoom, id);
		if (msg != null) {
			Compatibility.copyTextToClipboard(getActivity(), msg);
			LinphoneActivity.instance().displayCustomToast(getString(R.string.text_copied_to_clipboard), Toast.LENGTH_SHORT);
		}
	}

	public String getSipUri() {
		return sipUri;
	}

	private void pickImage() {
		List<Intent> cameraIntents = new ArrayList<Intent>();
		Intent captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
		File file = new File(Environment.getExternalStorageDirectory(), getString(R.string.temp_photo_name_with_date).replace("%s", String.valueOf(System.currentTimeMillis())));
		imageToUploadUri = Uri.fromFile(file);
		captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageToUploadUri);
		cameraIntents.add(captureIntent);
		
		Intent galleryIntent = new Intent();
		galleryIntent.setType("image/*");
		galleryIntent.setAction(Intent.ACTION_PICK);
		
		Intent chooserIntent = Intent.createChooser(galleryIntent, getString(R.string.image_picker_title));
		chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, cameraIntents.toArray(new Parcelable[]{}));
		
		startActivityForResult(chooserIntent, ADD_PHOTO);
	}

	public String getRealPathFromURI(Uri contentUri) {
		String[] proj = {MediaStore.Images.Media.DATA};
		CursorLoader loader = new CursorLoader(getActivity(), contentUri, proj, null, null, null);
		Cursor cursor = loader.loadInBackground();
		if (cursor != null && cursor.moveToFirst()) {
			int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
			String result = cursor.getString(column_index);
			cursor.close();
			return result;
		}
		return null;
	}

	private void exitNewConversationMode(Contact c, String address, String username){
		searchContactField.setVisibility(View.GONE);
		sipUri = address;
		messagesList.setVisibility(View.VISIBLE);
		contactName.setVisibility(View.VISIBLE);
		edit.setVisibility(View.VISIBLE);
		back.setVisibility(View.VISIBLE);
		startCall.setVisibility(View.VISIBLE);
		newChatConversation = false;
		chatRoom = LinphoneManager.getLc().getOrCreateChatRoom(address);

		if(c != null)
			changeDisplayedChat(address,c.getName(),null);
		else
			changeDisplayedChat(address,username,null);
		dispayMessageList();
	}

	private void showPopupMenuAskingImageSize(final String filePath) {
		filePathToUpload = filePath;
		try {
			sendImage.showContextMenu();
		} catch (Exception e) {
			e.printStackTrace();
		};
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == ADD_PHOTO && resultCode == Activity.RESULT_OK) {
			String fileToUploadPath = null;
			
			if (data != null && data.getData() != null) {
				fileToUploadPath = getRealPathFromURI(data.getData());
			} else if (imageToUploadUri != null) {
				fileToUploadPath = imageToUploadUri.getPath();
			}
			
			if (fileToUploadPath != null) {
				//showPopupMenuAskingImageSize(fileToUploadPath);
				sendImageMessage(fileToUploadPath,0);
			}
		} else {
			super.onActivityResult(requestCode, resultCode, data);
		}
	}

	@Override
	public void onLinphoneChatMessageStateChanged(LinphoneChatMessage msg, State state) {
		//if (state == State.FileTransferDone || state == State.FileTransferError) {
		//	uploadLayout.setVisibility(View.GONE);
		//	textLayout.setVisibility(View.VISIBLE);
		//		progressBar.setProgress(0);
		//	currentMessageInFileTransferUploadState = null;
		//}
		invalidate();
	}

	@Override
	public void onLinphoneChatMessageFileTransferReceived(LinphoneChatMessage msg, LinphoneContent content, LinphoneBuffer buffer) {
	}

	@Override
	public void onLinphoneChatMessageFileTransferSent(LinphoneChatMessage msg, LinphoneContent content, int offset, int size, LinphoneBuffer bufferToFill) {
	}

	@Override
	public void onLinphoneChatMessageFileTransferProgressChanged(LinphoneChatMessage msg, LinphoneContent content, int offset, int total) {
		progressBar.setProgress(offset * 100 / total);
	}

	class SearchContactsListAdapter extends BaseAdapter implements Filterable {
		private List<ContactAddress> contacts;
		private Cursor cursor;
		private LayoutInflater mInflater;

		SearchContactsListAdapter(List<Contact> contactsList, Cursor c, LayoutInflater inflater) {
			cursor = c;
			mInflater = inflater;
			contacts = new ArrayList<ContactAddress>();
			for(Contact con: ContactsManager.getInstance().getAllContacts()){
				for(String numberOrAddress : con.getNumbersOrAddresses()){
					contacts.add(new ContactAddress(con,numberOrAddress));
				}
			}
		}

		@Override
		public Filter getFilter() {
			return new Filter() {
				@Override
				protected void publishResults(CharSequence constraint,
											  FilterResults results) {

					if (results.count > 0) {
						contacts.clear();
						contacts = (List<ContactAddress>) results.values;
						notifyDataSetChanged();
					} else {;
						contacts.clear();
						contacts = getContactsList();
						notifyDataSetInvalidated();
					}
				}

				@Override
				protected FilterResults performFiltering(CharSequence constraint) {
					List<ContactAddress> result = new ArrayList<ContactAddress>();
					if(constraint != null) {
						for (ContactAddress c : contacts) {
							if (c.mContact.getName().toLowerCase().startsWith(constraint.toString()) || c.mAddress.startsWith(constraint.toString())) {
								result.add(c);
							}
						}
					}
					FilterResults r = new FilterResults();
					r.values = result;
					r.count = result.size();
					return r;
				}
			};
		}

		public List<ContactAddress> getContactsList(){
			List<ContactAddress> contacts = new ArrayList<ContactAddress>();
			contacts = new ArrayList<ContactAddress>();
			for(Contact con: ContactsManager.getInstance().getAllContacts()){
				for(String numberOrAddress : con.getNumbersOrAddresses()){
					contacts.add(new ContactAddress(con,numberOrAddress));
				}
			}
			return contacts;
		}

		class ContactAddress {
			public Contact mContact;
			public String mAddress;

			ContactAddress(Contact contact, String address){
				mContact = contact;
				mAddress = address;
			}
		}

		public int getCount() {
			return contacts.size();
		}

		public Object getItem(int position) {
			if (contacts == null || position >= contacts.size()) {
				return getContactsList().get(position);
			} else {
				return contacts.get(position);
			}
		}

		public long getItemId(int position) {
			return position;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			View view = null;
			ContactAddress contact;
			do {
				contact = (ContactAddress) getItem(position);
			} while (contact == null);

			if (convertView != null) {
				view = convertView;
			} else {
				view = mInflater.inflate(R.layout.search_contact_cell, parent, false);
			}

			TextView name = (TextView) view.findViewById(R.id.Contact_name);
			name.setText(contact.mContact.getName());

			final TextView address = (TextView) view.findViewById(R.id.contact_address);
			address.setText(contact.mAddress);

			final String a = contact.mAddress;
			final Contact c = contact.mContact;

			view.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
					exitNewConversationMode(c, a,null);

				}
			});

			return view;
		}
	}


}
