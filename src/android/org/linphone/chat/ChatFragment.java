package org.linphone.chat;

/*
ChatFragment.java
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

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.MimeTypeMap;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.SearchContactsListAdapter;
import org.linphone.receivers.ContactsUpdatedListener;
import org.linphone.activities.LinphoneActivity;
import org.linphone.contacts.LinphoneContact;
import org.linphone.LinphoneManager;
import org.linphone.LinphoneService;
import org.linphone.LinphoneUtils;
import org.linphone.R;
import org.linphone.compatibility.Compatibility;
import org.linphone.core.Address;
import org.linphone.core.Buffer;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatMessage.State;
import org.linphone.core.ChatMessageListener;
import org.linphone.core.ChatRoom;
import org.linphone.core.Content;
import org.linphone.core.Core;
import org.linphone.core.Factory;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.Friend;
import org.linphone.core.FriendList;
import org.linphone.mediastream.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;
import static org.linphone.fragments.FragmentsAvailable.CHAT;

interface ChatUpdatedListener {
	void onChatUpdated();
}

public class ChatFragment extends Fragment implements OnClickListener, ChatMessageListener, ContactsUpdatedListener {
	private static final int ADD_PHOTO = 1337;
	private static final int MENU_DELETE_MESSAGE = 0;
	private static final int MENU_COPY_TEXT = 6;
	private static final int MENU_RESEND_MESSAGE = 7;
	private static final int SIZE_SMALL = 500;
	private static final int SIZE_MAX = 2048;

	private ChatRoom chatRoom;
	private String sipUri;
	private EditText message;
	private ImageView edit, selectAll, deselectAll, startCall, delete, sendImage, sendMessage, cancel;
	private TextView contactName, remoteComposing;
	private ImageView back, backToCall, infos;
	private EditText searchContactField;
	private LinearLayout topBar, editList, event;
	private SearchContactsListAdapter searchAdapter;
	private ListView messagesList, resultContactsSearch;
	private LayoutInflater inflater;
	private Bitmap defaultBitmap;

	private boolean isEditMode = false;
	private LinphoneContact contact;
	private Uri imageToUploadUri;
	private TextWatcher textWatcher;
	private ViewTreeObserver.OnGlobalLayoutListener keyboardListener;
	private ChatMessageAdapter adapter;

	private CoreListenerStub mListener;
	private boolean newChatConversation = false;
	private String fileSharedUri;

	private static ArrayList<ChatUpdatedListener> ChatUpdatedListeners;

	public static void createIfNotExist() {
		if (ChatUpdatedListeners == null)
			ChatUpdatedListeners = new ArrayList<>();
	}

	public static void addChatListener(ChatUpdatedListener listener) {
		ChatUpdatedListeners.add(listener);
	}
	public static void removeChatListener(ChatUpdatedListener listener) {
		ChatUpdatedListeners.remove(listener);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final View view = inflater.inflate(R.layout.chat, container, false);

		LinphoneManager.addListener(this);
		// Retain the fragment across configuration changes
		setRetainInstance(true);

		this.inflater = inflater;

		if(getArguments() == null || getArguments().getString("SipUri") == null) {
			newChatConversation = true;
		} else {
			//Retrieve parameter from intent
			sipUri = getArguments().getString("SipUri");
			newChatConversation = false;
		}

		//Initialize UI
		defaultBitmap = BitmapFactory.decodeResource(getActivity().getResources(), R.drawable.chat_picture_over);

		contactName = (TextView) view.findViewById(R.id.contact_name);
		messagesList = (ListView) view.findViewById(R.id.chat_message_list);
		searchContactField = (EditText) view.findViewById(R.id.search_contact_field);
		resultContactsSearch = (ListView) view.findViewById(R.id.result_contacts);

		editList = (LinearLayout) view.findViewById(R.id.edit_list);
		topBar = (LinearLayout) view.findViewById(R.id.top_bar);

		sendMessage = (ImageView) view.findViewById(R.id.send_message);
		sendMessage.setOnClickListener(this);

		remoteComposing = (TextView) view.findViewById(R.id.remote_composing);
		remoteComposing.setVisibility(View.GONE);

		cancel = (ImageView) view.findViewById(R.id.cancel);
		cancel.setOnClickListener(this);

		edit = (ImageView) view.findViewById(R.id.edit);
		edit.setOnClickListener(this);

		startCall = (ImageView) view.findViewById(R.id.start_call);
		startCall.setOnClickListener(this);

		infos = view.findViewById(R.id.group_infos);
		infos.setVisibility(View.GONE);

		backToCall = (ImageView) view.findViewById(R.id.back_to_call);
		backToCall.setOnClickListener(this);

		selectAll = (ImageView) view.findViewById(R.id.select_all);
		selectAll.setOnClickListener(this);

		deselectAll = (ImageView) view.findViewById(R.id.deselect_all);
		deselectAll.setOnClickListener(this);

		delete = (ImageView) view.findViewById(R.id.delete);
		delete.setOnClickListener(this);

		if (getArguments().getString("fileSharedUri") != null && getArguments().getString("fileSharedUri") != "")
			fileSharedUri = getArguments().getString("fileSharedUri");
		else
			fileSharedUri = null;
		if (newChatConversation) {
			initNewChatConversation();
		}

		message = (EditText) view.findViewById(R.id.message);

		if (getArguments().getString("messageDraft") != null)
			message.setText(getArguments().getString("messageDraft"));

		sendImage = (ImageView) view.findViewById(R.id.send_picture);
		if (!getResources().getBoolean(R.bool.disable_chat_send_file)) {
			sendImage.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (LinphoneManager.getLc().limeEnabled() == Core.LimeState.Mandatory
							&& (chatRoom == null || !chatRoom.limeAvailable())){
						askingForLimeCall();
						return;
					}
					pickImage();
					LinphoneActivity.instance().checkAndRequestPermissionsToSendImage();
				}
			});
			//registerForContextMenu(sendImage);
		} else {
			sendImage.setEnabled(false);
		}

		back = (ImageView) view.findViewById(R.id.back);
		if(getResources().getBoolean(R.bool.isTablet)){
			back.setVisibility(View.INVISIBLE);
		} else {
			back.setOnClickListener(this);
		}

		mListener = new CoreListenerStub(){
			@Override
			public void onMessageReceived(Core lc, ChatRoom cr, ChatMessage message) {
				Address from = cr.getPeerAddress();
				if (from.asStringUriOnly().equals(sipUri)) {
					//LinphoneService.instance().removeMessageNotification();
					cr.markAsRead();
					LinphoneActivity.instance().updateMissedChatCount();
					adapter.addMessage(message);

					String externalBodyUrl = message.getExternalBodyUrl();
					Content fileTransferContent = message.getFileTransferInformation();
					if (externalBodyUrl != null || fileTransferContent != null) {
						LinphoneActivity.instance().checkAndRequestExternalStoragePermission();
					}
				}
				if (getResources().getBoolean(R.bool.isTablet)) {
					for (ChatUpdatedListener c : ChatUpdatedListeners) {
						c.onChatUpdated();
					}
				}
			}

			@Override
			public void onIsComposingReceived(Core lc, ChatRoom room) {
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

		return view;
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		if (message != null) {
			outState.putString("messageDraft", message.getText().toString());
		}
		if (contact != null) {
			outState.putSerializable("contactDraft",contact);
			outState.putString("sipUriDraft",sipUri);

		}
		if (sipUri != null) {
			outState.putString("sipUriDraft", sipUri);
			outState.putString("SipUri", sipUri);
	}
		outState.putString("fileSharedUri", "");

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
			//topBar.setVisibility(View.GONE);
		}
		LinphoneActivity.instance().hideTabBar(true);
		//contactPicture.setVisibility(View.GONE);
	}

	public void hideKeyboardVisibleMode() {
		boolean isOrientationLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
		//contactPicture.setVisibility(View.VISIBLE);
		if (isOrientationLandscape && topBar != null) {
			//topBar.setVisibility(View.VISIBLE);
		}
		LinphoneActivity.instance().hideTabBar(false);
	}

	public int getNbItemsChecked(){
		int size = messagesList.getAdapter().getCount();
		int nb = 0;
		for(int i=0; i<size; i++) {
			if(messagesList.isItemChecked(i)) {
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

	public void initChatRoom(String sipUri) {
		Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		Address lAddress = null;
		if (sipUri == null) {
			contact = null; // Tablet rotation issue
			initNewChatConversation();
		} else {
			try {
				lAddress = lc.interpretUrl(sipUri);
				if (lAddress == null) LinphoneActivity.instance().goToDialerFragment();
			} catch (Exception e) {
				//TODO Error popup
				LinphoneActivity.instance().goToDialerFragment();
			}

			if (lAddress != null) {
				chatRoom = lc.getChatRoom(lAddress);
				chatRoom.markAsRead();
				LinphoneActivity.instance().updateMissedChatCount();
				contact = ContactsManager.getInstance().findContactFromAddress(lAddress);
				if (chatRoom != null) {
					searchContactField.setVisibility(View.GONE);
					resultContactsSearch.setVisibility(View.GONE);
					displayChatHeader(lAddress);
					removedList();
					remoteComposing.setVisibility(chatRoom.isRemoteComposing() ? View.VISIBLE : View.GONE);
				}
			}
		}
	}

	private void redrawMessageList() {
		if (adapter != null) {
			adapter.notifyDataSetChanged();
		}
		//messagesList.invalidateViews();
	}

	private void removedList() {
		if (chatRoom != null) {
			if (adapter != null) {
				adapter.refreshHistory();
			} else {
				adapter = new ChatMessageAdapter(getActivity());
			}
		}
		messagesList.setAdapter(adapter);
		messagesList.setVisibility(ListView.VISIBLE);
	}

	private void displayChatHeader(Address address) {
		if (contact != null || address != null) {
			if (contact != null) {
				contactName.setText(contact.getFullName());
			} else {
				contactName.setText(LinphoneUtils.getAddressDisplayName(address));
			}
			topBar.setVisibility(View.VISIBLE);
			edit.setVisibility(View.VISIBLE);
			contactName.setVisibility(View.VISIBLE);
		}
	}

	public void changeDisplayedChat(String newSipUri, String displayName, String pictureUri, String message, String fileUri) {
		this.sipUri = newSipUri;
		if(message != null)
			this.message.setText(message);
		if(fileUri != null)
			fileSharedUri = fileUri;
		initChatRoom(sipUri);
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
		menu.add(v.getId(), MENU_DELETE_MESSAGE, 0, getString(R.string.delete));
		menu.add(v.getId(), MENU_COPY_TEXT, 0, getString(R.string.copy_text));

	/*	ChatMessage msg = getMessageForId(v.getId());
		if (msg != null && msg.getStatus() == ChatMessage.State.NotDelivered) {
			menu.add(v.getId(), MENU_RESEND_MESSAGE, 0, getString(R.string.retry));
		}
	*/
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case MENU_DELETE_MESSAGE:
				if (chatRoom != null) {
					ChatMessage message = getMessageForId(item.getGroupId());
					if (message != null) {
						chatRoom.deleteMessage(message);
						if (getResources().getBoolean(R.bool.isTablet) && chatRoom.getHistorySize() <= 0) {
							if (LinphoneActivity.isInstanciated()) {
								LinphoneActivity.instance().displayChat("", null, null);
								LinphoneActivity.instance().onMessageSent("", null);
								initNewChatConversation();
							}
						}
						invalidate();
					}
				}
				break;
			case MENU_COPY_TEXT:
				copyTextMessageToClipboard(item.getGroupId());
				break;
			/*case MENU_RESEND_MESSAGE:
				resendMessage(item.getGroupId());
				break;
			*/
		}
		return true;
	}

	@Override
	public void onPause() {
		message.removeTextChangedListener(textWatcher);
		removeVirtualKeyboardVisiblityListener();

		LinphoneService.instance().removeMessageNotification();

		Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			lc.removeListener(mListener);
		}

		LinphoneManager.removeListener(this);
		onSaveInstanceState(getArguments());

		//Hide keybord
		InputMethodManager imm = (InputMethodManager)getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(message.getWindowToken(), 0);

		super.onPause();
	}

	@Override
	public void onDestroy() {
		if (adapter != null) {
			adapter.destroy();
		}
		if (defaultBitmap != null) {
			defaultBitmap.recycle();
			defaultBitmap = null;
		}
		super.onDestroy();
	}

	@SuppressLint("UseSparseArrays")
	@Override
	public void onResume() {
		super.onResume();
		ContactsManager.addContactsListener(this);
		message.addTextChangedListener(textWatcher);
		addVirtualKeyboardVisiblityListener();

		Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			lc.addListener(mListener);
		}

		if (LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().selectMenu(CHAT);
		}

		LinphoneManager.addListener(this);

		// Force hide keyboard
		getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

		String draft = getArguments().getString("messageDraft");
		message.setText(draft);
		contact = (LinphoneContact)getArguments().getSerializable("contactDraft");
		if (contact != null) {
			contactName.setText(contact.getFullName());
			sipUri = getArguments().getString("SipUri");
			newChatConversation = false;
			getArguments().clear();
		}else if(getArguments().getString("sipUriDraft") != null){
			sipUri = getArguments().getString("sipUriDraft");
			newChatConversation = false;
		}else if (sipUri != null) {
			newChatConversation = false;
		}else {
			sipUri = null;
			newChatConversation = true;
		}

		if(LinphoneManager.getLc().inCall()){
			backToCall.setVisibility(View.VISIBLE);
			startCall.setVisibility(View.GONE);
		} else {
			if(!newChatConversation) {
				backToCall.setVisibility(View.GONE);
				startCall.setVisibility(View.VISIBLE);
			}
		}

		if (!newChatConversation || contact != null) {
			initChatRoom(sipUri);
			searchContactField.setVisibility(View.GONE);
			resultContactsSearch.setVisibility(View.GONE);
			remoteComposing.setVisibility(chatRoom.isRemoteComposing() ? View.VISIBLE : View.GONE);
		}
	}

	private void selectAllList(boolean isSelectAll) {
		int size = messagesList.getAdapter().getCount();
		for (int i = 0; i < size; i++) {
			messagesList.setItemChecked(i, isSelectAll);
		}
	}

	public void quitEditMode(){
		isEditMode = false;
		editList.setVisibility(View.GONE);
		topBar.setVisibility(View.VISIBLE);
		redrawMessageList();
	}

	private void removeChats(){
		int size = messagesList.getAdapter().getCount();
		for (int i = 0; i < size; i++) {
			if (messagesList.isItemChecked(i)) {
				ChatMessage message = (ChatMessage) messagesList.getAdapter().getItem(i);
				chatRoom.deleteMessage(message);
			}
		}
		if (getResources().getBoolean(R.bool.isTablet) && chatRoom.getHistorySize() <= 0) {
			if (LinphoneActivity.isInstanciated()) {
				LinphoneActivity.instance().displayChat("", null, null);
				LinphoneActivity.instance().onMessageSent("", null);
				initNewChatConversation();
			}
		}
		invalidate();
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();

		if (id == R.id.back_to_call) {
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
					removeChats();
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
		if(id == R.id.send_message){
			sendTextMessage();
		}
		if (id == R.id.edit) {
			topBar.setVisibility(View.INVISIBLE);
			editList.setVisibility(View.VISIBLE);
			isEditMode = true;
			redrawMessageList();
		}
		if(id == R.id.start_call){
			LinphoneActivity.instance().setAddresGoToDialerAndCall(sipUri, LinphoneUtils.getUsernameFromAddress(sipUri), null);
		}
		if (id == R.id.back) {
			cleanIntentAndFiles();
			getFragmentManager().popBackStackImmediate();
		}
	}

	private void cleanIntentAndFiles() {
		if (getArguments().getString("fileSharedUri") != null){
			getArguments().remove("fileSharedUri");
			fileSharedUri = null;
		}
		message.setText("");
		if (getArguments().getString("messageDraft") != null)
			getArguments().remove("messageDraft");

		this.getArguments().clear();
	}

	private void sendTextMessage() {
		if (!LinphoneManager.isInstanciated() || LinphoneManager.getLc() == null ||
				(searchContactField.getVisibility() == View.VISIBLE
						&& searchContactField.getText().toString().length() < 1))
			return;
		Core.LimeState state = LinphoneManager.getLc().limeEnabled();

		if ((state == Core.LimeState.Disabled
				|| state == Core.LimeState.Preferred)
				|| (state == Core.LimeState.Mandatory
				&& chatRoom != null && chatRoom.limeAvailable())){
			sendTextMessage(message.getText().toString());
			message.setText("");
			invalidate();
			return;
		}

		askingForLimeCall();
	}

	private void sendTextMessage(String messageToSend) {
		Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		boolean isNetworkReachable = lc == null ? false : lc.isNetworkReachable();
		Address lAddress = null;

		//Start new conversation in fast chat
		if(newChatConversation && chatRoom == null) {
			String address = searchContactField.getText().toString().toLowerCase(Locale.getDefault());
			if (address != null && !address.equals("")) {
				initChatRoom(address);
			}
		}
		if (chatRoom != null && messageToSend != null && messageToSend.length() > 0 && isNetworkReachable) {
			ChatMessage message = chatRoom.createMessage(messageToSend);
			chatRoom.sendChatMessage(message);
			lAddress = chatRoom.getPeerAddress();

			if (LinphoneActivity.isInstanciated()) {
				LinphoneActivity.instance().onMessageSent(sipUri, messageToSend);
			}

			message.setListener(LinphoneManager.getInstance());
			if (newChatConversation) {
				exitNewConversationMode(lAddress.asStringUriOnly());
			} else {
				adapter.addMessage(message);
			}

			Log.i("Sent message current status: " + message.getState());
		} else if (!isNetworkReachable && LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().displayCustomToast(getString(R.string.error_network_unreachable), Toast.LENGTH_LONG);
		}
	}

	private void sendImageMessage(String path, int imageSize) {
		if(path.contains("file://")) {
			path = path.substring(7);
		}
		if(path.contains("%20")) {
			path = path.replace("%20", "-");
		}
		Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		boolean isNetworkReachable = lc == null ? false : lc.isNetworkReachable();
		if(newChatConversation && chatRoom == null) {
			String address = searchContactField.getText().toString();
			if (address != null && !address.equals("")) {
				initChatRoom(address);
			}
		}
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
			 fileSharedUri = null;
		} else if (!isNetworkReachable && LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().displayCustomToast(getString(R.string.error_network_unreachable), Toast.LENGTH_LONG);
		}
	}

	private void sendFileSharingMessage(String path, int size ) {
		if (path.contains("file://")) {
			path = path.substring(7);
		} else if (path.contains("com.android.contacts/contacts/")) {
			path = getCVSPathFromLookupUri(path).toString();
		} else if (path.contains("vcard") || path.contains("vcf")) {
			path = (LinphoneUtils.createCvsFromString(LinphoneActivity.instance().getCVSPathFromOtherUri(path).toString())).toString();
		} else if (path.contains("content://")){
			path = LinphoneUtils.getFilePath(this.getActivity().getApplicationContext(), Uri.parse(path));
		}
		if(path.contains("%20")) {
			path = path.replace("%20", "-");
		}
		Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		boolean isNetworkReachable = lc == null ? false : lc.isNetworkReachable();
		if (newChatConversation && chatRoom == null) {
			String address = searchContactField.getText().toString();
			if (address != null && !address.equals("")) {
				initChatRoom(address);
			}
		}

		if (chatRoom != null && path != null && path.length() > 0 && isNetworkReachable) {
			try {
				File fileShared = new File(path);
				if (fileShared != null) {
					FileSharingUploadPrepareTask task = new FileSharingUploadPrepareTask(getActivity(), path, size);
					task.execute(fileShared);
				} else {
					Log.e("Error, fileShared can't be read " + path);
				}
			} catch (RuntimeException e) {
				Log.e("Error, not enough memory to create the fileShared");
			}
			fileSharedUri = null;
		} else if (!isNetworkReachable && LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().displayCustomToast(getString(R.string.error_network_unreachable), Toast.LENGTH_LONG);
		}
	}

	private void askingForLimeCall() {
		final Dialog dialog = LinphoneActivity.instance().displayDialog(getString(R.string.lime_not_verified));
		Button delete = (Button) dialog.findViewById(R.id.delete_button);
		delete.setText(getString(R.string.call));
		Button cancel = (Button) dialog.findViewById(R.id.cancel);
		cancel.setText(getString(R.string.no));

		delete.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				//Start new conversation in fast chat
				if(newChatConversation && chatRoom == null) {
					String address = searchContactField.getText().toString().toLowerCase(Locale.getDefault());
					if (address != null && !address.equals("")) {
						initChatRoom(address);
					}
				}
				LinphoneManager.getInstance().newOutgoingCall(chatRoom.getPeerAddress().asStringUriOnly()
						, chatRoom.getPeerAddress().getDisplayName());
				dialog.dismiss();
			}
		});

		cancel.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				dialog.dismiss();
			}
		});
		if(LinphoneManager.getLc().limeEnabled() == Core.LimeState.Mandatory)
			dialog.show();
	}

	private ChatMessage getMessageForId(int id) {
		if (adapter == null) return null;
		for (int i = 0; i < adapter.getCount(); i++) {
			ChatMessage message = adapter.getItem(i);
			if (message.getStorageId() == id) {
				return message;
			}
		}
		return null;
	}

	private void invalidate() {
		adapter.refreshHistory();
		chatRoom.markAsRead();
		if (getResources().getBoolean(R.bool.isTablet)) {
			for (ChatUpdatedListener c : ChatUpdatedListeners) {
				c.onChatUpdated();
			}
		}
	}

	private void resendMessage(ChatMessage message) {
		Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (message == null || message.getState() != State.NotDelivered || !lc.isNetworkReachable())
			return;

		message.resend();
		invalidate();
	}

	private void resendMessage(int id) {
		ChatMessage message = getMessageForId(id);
		Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (message == null || message.getState() != State.NotDelivered || !lc.isNetworkReachable()){
				return;
		}
		message.resend();
		invalidate();
	}

	private void copyTextMessageToClipboard(int id) {
		ChatMessage message = null;
		for (int i = 0; i < adapter.getCount(); i++) {
			ChatMessage msg = adapter.getItem(i);
			if (msg.getStorageId() == id) {
				message = msg;
				break;
			}
		}

		String txt = null;
		if (message != null) {
			txt = message.getText();
		}
		if (txt != null) {
			ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
		    ClipData clip = android.content.ClipData.newPlainText("Message", txt);
		    clipboard.setPrimaryClip(clip);
			LinphoneActivity.instance().displayCustomToast(getString(R.string.text_copied_to_clipboard), Toast.LENGTH_SHORT);
		}
	}

	//File transfer
	private void pickImage() {
		List<Intent> cameraIntents = new ArrayList<Intent>();
		Intent captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
		File file = new File(Environment.getExternalStorageDirectory(), getString(R.string.temp_photo_name_with_date).replace("%s", String.valueOf(System.currentTimeMillis())+".jpeg"));
		imageToUploadUri = Uri.fromFile(file);
		captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageToUploadUri);
		cameraIntents.add(captureIntent);

		Intent galleryIntent = new Intent();
		galleryIntent.setType("image/*");
		galleryIntent.setAction(Intent.ACTION_PICK);

		Intent fileIntent = new Intent();
		fileIntent.setType("*/*");
		fileIntent.setAction(Intent.ACTION_GET_CONTENT);
		cameraIntents.add(fileIntent);

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

	public Uri getCVSPathFromLookupUri(String content) {
		String contactId = LinphoneUtils.getNameFromFilePath(content);
		FriendList[] friendList = LinphoneManager.getLc().getFriendsLists();
		for (FriendList list : friendList) {
			for (Friend friend : list.getFriends()) {
				if (friend.getRefKey().toString().equals(contactId)) {
					String contactVcard = friend.getVcard().asVcard4String();
					Uri path = LinphoneUtils.createCvsFromString(contactVcard);
					return path;
				}
			}
		}
		return null;
	}

	public Uri getCVSPathFromLookupUri(Uri contentUri) {
		String content = contentUri.getPath();
		Uri uri = getCVSPathFromLookupUri(content);
		return uri;
	}

	@Override
	public void onContactsUpdated() {
		if(LinphoneActivity.isInstanciated()
				&& LinphoneActivity.instance().getCurrentFragment() == CHAT
				&& fileSharedUri != null || message.getText() != null){
			initNewChatConversation();
		}
	}

	class FileUploadPrepareTask extends AsyncTask<Bitmap, Void, byte[]> {
		private String path;
		private ProgressDialog progressDialog;

		public FileUploadPrepareTask(Context context, String fileToUploadPath, int size) {
			path = fileToUploadPath;

			progressDialog = new ProgressDialog(context);
			progressDialog.setIndeterminate(true);
			progressDialog.setMessage(getString(R.string.processing_image));
			progressDialog.show();
		}

		@Override
		protected byte[] doInBackground(Bitmap... params) {
			Bitmap bm = params[0];
			Bitmap bm_tmp = null;

			if (bm.getWidth() >= bm.getHeight() && bm.getWidth() > SIZE_MAX) {
				bm_tmp = Bitmap.createScaledBitmap(bm, SIZE_MAX, (SIZE_MAX * bm.getHeight()) / bm.getWidth(), false);

			} else if (bm.getHeight() >= bm.getWidth() && bm.getHeight() > SIZE_MAX) {
				bm_tmp = Bitmap.createScaledBitmap(bm, (SIZE_MAX * bm.getWidth()) / bm.getHeight(), SIZE_MAX, false);
			}

			if (bm_tmp != null) {
				bm.recycle();
				bm = bm_tmp;
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
					bm_tmp = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), matrix, true);
				}
			} catch (Exception e) {
				Log.e(e);
			}

			if (bm_tmp != null) {
				if (bm_tmp != bm) {
					bm.recycle();
					bm = bm_tmp;
				} else {
					bm_tmp = null;
				}
			}

			ByteArrayOutputStream stream = new ByteArrayOutputStream();
			String extension = LinphoneUtils.getExtensionFromFileName(path);
			if (extension != null && extension.toLowerCase(Locale.getDefault()).equals("png")) {
				bm.compress(Bitmap.CompressFormat.PNG, 100, stream);
			} else {
				bm.compress(Bitmap.CompressFormat.JPEG, 100, stream);
			}

			if (bm_tmp != null) {
				bm_tmp.recycle();
				bm_tmp = null;
			}
			bm.recycle();
			bm = null;

			byte[] byteArray = stream.toByteArray();
			return byteArray;
		}

		@Override
		protected void onPostExecute(byte[] result) {
			if (progressDialog != null && progressDialog.isShowing()) {
				progressDialog.dismiss();
			}
			String fileName = path.substring(path.lastIndexOf("/") + 1);
			String extension = LinphoneUtils.getExtensionFromFileName(fileName);
			Content content = Factory.instance().createContent(); // "image", extension, result, null
			content.setType("image");
			content.setSubtype(extension);
			content.setBuffer(result, result.length);
			content.setName(fileName);
			ChatMessage message = chatRoom.createFileTransferMessage(content);
			message.setListener(LinphoneManager.getInstance());
			message.setAppdata(path);

			LinphoneManager.getInstance().setUploadPendingFileMessage(message);
			LinphoneManager.getInstance().setUploadingImage(result);

			chatRoom.sendChatMessage(message);
			adapter.addMessage(message);
		}
	}

	class FileSharingUploadPrepareTask extends AsyncTask<File, Void, byte[]> {
		private String path;
		private ProgressDialog progressDialog;

		public FileSharingUploadPrepareTask(Context context, String fileToUploadPath, int size) {
			path = fileToUploadPath;

			progressDialog = new ProgressDialog(context);
			progressDialog.setIndeterminate(true);
			progressDialog.setMessage(getString(R.string.processing_image));
			progressDialog.show();
		}

		@Override
		protected byte[] doInBackground(File... params) {
			File file = params[0];

			byte[] byteArray = new byte[(int) file.length()];
			FileInputStream fis = null;
			try {
				fis = new FileInputStream(file);
				fis.read(byteArray); //read file into bytes[]
				fis.close();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}catch (IOException e) {
				e.printStackTrace();
			}

			return byteArray;
		}

		@Override
		protected void onPostExecute(byte[] result) {
			if (progressDialog != null && progressDialog.isShowing()) {
				progressDialog.dismiss();
			}

			String fileName = path.substring(path.lastIndexOf("/") + 1);
			String extension = LinphoneUtils.getExtensionFromFileName(fileName);
			Content content = Factory.instance().createContent();//"file", extension, result, null
			content.setType("file");
			content.setSubtype(extension);
			content.setBuffer(result, result.length);
			content.setName(fileName);
			content.setName(fileName);

			ChatMessage message = chatRoom.createFileTransferMessage(content);
			message.setListener(LinphoneManager.getInstance());
			message.setAppdata(path);

			LinphoneManager.getInstance().setUploadPendingFileMessage(message);
			LinphoneManager.getInstance().setUploadingImage(result);

			chatRoom.sendChatMessage(message);
			adapter.addMessage(message);
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(data != null) {
			if (requestCode == ADD_PHOTO && resultCode == Activity.RESULT_OK) {
				String fileToUploadPath = null;
				if (data != null && data.getData() != null) {
					if(data.getData().toString().contains("com.android.contacts/contacts/")){
						if(getCVSPathFromLookupUri(data.getData()) != null)
							fileToUploadPath = getCVSPathFromLookupUri(data.getData()).toString();
						else {
							LinphoneActivity.instance().displayCustomToast("Something wrong happened", Toast.LENGTH_LONG);
							return;
						}
					} else {
						fileToUploadPath = getRealPathFromURI(data.getData());
					}
					if (fileToUploadPath == null) {
						fileToUploadPath = data.getData().toString();
					}
				} else if (imageToUploadUri != null) {
					fileToUploadPath = imageToUploadUri.getPath();
				}
				if (LinphoneUtils.isExtensionImage(fileToUploadPath))
					sendImageMessage(fileToUploadPath, 0);
				else
					sendFileSharingMessage(fileToUploadPath, 0);
			} else {
				super.onActivityResult(requestCode, resultCode, data);
			}
		} else {
			if (LinphoneUtils.isExtensionImage(imageToUploadUri.getPath()))
				sendImageMessage(imageToUploadUri.getPath(), 0);
		}
	}

	//New conversation
	private void exitNewConversationMode(String address) {
		sipUri = address;
		searchContactField.setVisibility(View.GONE);
		resultContactsSearch.setVisibility(View.GONE);
		messagesList.setVisibility(View.VISIBLE);
		contactName.setVisibility(View.VISIBLE);
		edit.setVisibility(View.VISIBLE);
		startCall.setVisibility(View.VISIBLE);

		if(getResources().getBoolean(R.bool.isTablet)){
			back.setVisibility(View.INVISIBLE);
		} else {
			back.setOnClickListener(this);
		}

		newChatConversation = false;
		initChatRoom(sipUri);

		if(fileSharedUri != null){
			//save SipUri into bundle
			onSaveInstanceState(getArguments());
			if(LinphoneUtils.isExtensionImage(fileSharedUri)) {
				sendImageMessage(fileSharedUri, 0);
			}else {
				sendFileSharingMessage(fileSharedUri, 0);
			}
			fileSharedUri = null;
		}
	}

	private void initNewChatConversation(){
		newChatConversation = true;
		chatRoom = null;
		messagesList.setVisibility(View.GONE);
		edit.setVisibility(View.INVISIBLE);
		startCall.setVisibility(View.INVISIBLE);
		contactName.setVisibility(View.INVISIBLE);
		resultContactsSearch.setVisibility(View.VISIBLE);
		searchAdapter = new SearchContactsListAdapter(null, inflater, null);
		searchAdapter.setListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				exitNewConversationMode((String)view.getTag(R.id.contact_search_name));
			}
		});
		resultContactsSearch.setAdapter(searchAdapter);
		searchContactField.setVisibility(View.VISIBLE);
		searchContactField.setText("");
		searchContactField.requestFocus();
		searchContactField.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
										  int after) {}

			@Override
			public void afterTextChanged(Editable s) {
				searchAdapter.searchContacts(searchContactField.getText().toString(), resultContactsSearch);
			}
		});
	}

	class ChatMessageAdapter extends BaseAdapter {
		private

		ArrayList<ChatMessage> history;
		Context context;

		public ChatMessageAdapter(Context c) {
			context = c;
			history = new ArrayList<ChatMessage>();
			refreshHistory();
		}

		public void destroy() {
			if (history != null) {
				history.clear();
			}
		}

		public void refreshHistory() {
			if (history == null || chatRoom == null) return;
			history.clear();
			ChatMessage[] messages = chatRoom.getHistory(0);
			history.addAll(Arrays.asList(messages));
			notifyDataSetChanged();
		}

		public void addMessage(ChatMessage message) {
			history.add(message);
			notifyDataSetChanged();
			messagesList.setSelection(getCount() - 1);
		}

		public void refreshMessageCell(ChatMessage msg){

		}

		@Override
		public int getCount() {
			return history.size();
		}

		@Override
		public ChatMessage getItem(int position) {
			return history.get(position);
		}

		@Override
		public long getItemId(int position) {
			return history.get(position).getStorageId();
		}

		@Override
		public View getView(final int position, View convertView, ViewGroup parent) {
			final ChatMessage message = history.get(position);
			View view = null;
			final ChatBubbleViewHolder holder;
			boolean sameMessage = false;

			if (convertView != null) {
				view = convertView;
				holder = (ChatBubbleViewHolder) view.getTag();
				//LinphoneManager.removeListener(holder);
			} else {
				view = LayoutInflater.from(context).inflate(R.layout.chat_bubble, null);
				holder = new ChatBubbleViewHolder(view);
				view.setTag(holder);
			}

			/*LinphoneManager.addListener(holder);
			if (holder.id == message.getStorageId()) {
				// Horrible workaround to not reload image on edit chat list
				if (holder.messageImage.getTag() != null
						&& (holder.messageImage.getTag().equals(message.getAppdata())
							|| ((String) holder.messageImage.getTag()).substring(7).equals(message.getAppdata()))
						){
					sameMessage = true;
				}
			} else {
				holder.id = message.getStorageId();
			}
			view.setId(holder.id);*/
			registerForContextMenu(view);
			view.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					if (!message.isSecured() && !message.isOutgoing() &&
							LinphoneManager.getLc().limeEnabled() == Core.LimeState.Mandatory) {
						LinphoneUtils.displayErrorAlert(getString(R.string.message_not_encrypted), LinphoneActivity.instance());
					}
					if(message.getState() == State.NotDelivered) {
						resendMessage(message);
						//resendMessage(holder.id);
					}
				}
			});

			ChatMessage.State status = message.getState();
			String externalBodyUrl = message.getExternalBodyUrl();
			Content fileTransferContent = message.getFileTransferInformation();

			holder.eventLayout.setVisibility(View.GONE);
			holder.delete.setVisibility(View.GONE);
			holder.messageText.setVisibility(View.GONE);
			holder.messageImage.setVisibility(View.GONE);
			holder.fileExtensionLabel.setVisibility(View.GONE);
			holder.fileNameLabel.setVisibility(View.GONE);
			holder.fileTransferLayout.setVisibility(View.GONE);
			holder.fileTransferProgressBar.setProgress(0);
			holder.fileTransferAction.setEnabled(true);
			holder.messageStatus.setVisibility(View.INVISIBLE);
			holder.messageSendingInProgress.setVisibility(View.GONE);

			String displayName = message.getFromAddress().getDisplayName();
			if (displayName == null) {
				displayName = message.getFromAddress().getUsername();
			}
			if (!message.isOutgoing()) {
				if (contact != null) {
					if (contact != null && contact.getFullName() != null) {
						displayName = contact.getFullName();
					}
					if (contact.hasPhoto()) {
						LinphoneUtils.setThumbnailPictureFromUri(LinphoneActivity.instance(), holder.contactPicture, contact.getThumbnailUri());
					} else {
						holder.contactPicture.setImageBitmap(ContactsManager.getInstance().getDefaultAvatarBitmap());
					}
				} else {
					holder.contactPicture.setImageBitmap(ContactsManager.getInstance().getDefaultAvatarBitmap());
				}
			} else {
				holder.contactPicture.setImageBitmap(ContactsManager.getInstance().getDefaultAvatarBitmap());
			}
			holder.contactName.setText(timestampToHumanDate(context, message.getTime()) + " - " + displayName);

			if (status == ChatMessage.State.InProgress) {
				holder.messageSendingInProgress.setVisibility(View.VISIBLE);
			}
			if (!message.isSecured() && !message.isOutgoing() &&
					LinphoneManager.getLc().limeEnabled() == Core.LimeState.Mandatory && status != ChatMessage.State.InProgress) {
				holder.messageStatus.setVisibility(View.VISIBLE);
				holder.messageStatus.setImageResource(R.drawable.chat_unsecure);
			}
			if(status == State.DeliveredToUser && message.isOutgoing()){
				holder.imdmLayout.setVisibility(View.VISIBLE);
				holder.imdmIcon.setImageResource(R.drawable.chat_delivered);
				holder.imdmLabel.setText(R.string.delivered);
				holder.imdmLabel.setTextColor(getResources().getColor(R.color.colorD));
			}
			else if(status == State.Displayed && message.isOutgoing()){
				holder.imdmLayout.setVisibility(View.VISIBLE);
				holder.imdmIcon.setImageResource(R.drawable.chat_read);
				holder.imdmLabel.setText(R.string.displayed);
				holder.imdmLabel.setTextColor(getResources().getColor(R.color.colorK));
			}
			else if(status == State.NotDelivered && message.isOutgoing()){
				holder.imdmLayout.setVisibility(View.VISIBLE);
				holder.imdmIcon.setImageResource(R.drawable.chat_error);
				holder.imdmLabel.setText(R.string.resend);
				holder.imdmLabel.setTextColor(getResources().getColor(R.color.colorI));
			}else
				holder.imdmLayout.setVisibility(View.INVISIBLE);
			if(!message.isOutgoing()){
				holder.imdmLayout.setVisibility(View.INVISIBLE);
			}


			if (externalBodyUrl != null || fileTransferContent != null) {
				String appData = message.getAppdata();


				if (message.isOutgoing() && appData != null) {
					holder.messageImage.setVisibility(View.VISIBLE);
					if (!sameMessage) {
						loadBitmap(message.getAppdata(), holder.messageImage);
						holder.messageImage.setTag(message.getAppdata());
					}

					if (LinphoneManager.getInstance().getMessageUploadPending() != null  && LinphoneManager.getInstance().getMessageUploadPending().getStorageId() == message.getStorageId()) {
						holder.messageSendingInProgress.setVisibility(View.GONE);
						holder.fileTransferLayout.setVisibility(View.VISIBLE);
						//LinphoneManager.addListener(holder);
					}
				} else {
					if (appData != null && !LinphoneManager.getInstance().isMessagePending(message) && appData.contains(context.getString(R.string.temp_photo_name_with_date).split("%s")[0])) {
						appData = null;
					}

					if (appData == null) {
						//LinphoneManager.addListener(holder);
						holder.fileTransferLayout.setVisibility(View.VISIBLE);
					}else if(status == State.NotDelivered && message.isOutgoing()){
						holder.fileTransferLayout.setVisibility(View.VISIBLE);
						holder.imdmLayout.setVisibility(View.VISIBLE);
						holder.imdmIcon.setImageResource(R.drawable.chat_error);
						holder.imdmLabel.setText(R.string.resend);
						holder.imdmLabel.setTextColor(getResources().getColor(R.color.colorI));
					}else {
						if (LinphoneManager.getInstance().isMessagePending(message) && status != State.FileTransferDone) {
							//LinphoneManager.addListener(holder);
							//holder.fileTransferAction.setEnabled(false);
							holder.fileTransferLayout.setVisibility(View.VISIBLE);
						} else {
							//LinphoneManager.removeListener(holder);
							holder.fileTransferLayout.setVisibility(View.GONE);
							holder.messageImage.setVisibility(View.VISIBLE);
							if (!sameMessage) {
								loadBitmap(appData, holder.messageImage);
								holder.messageImage.setTag(message.getAppdata());
							}
							//removedList();
						}
					}
				}
			} else {
				Spanned text = null;
				String msg = message.getText();
				if (msg != null) {
					text = getTextWithHttpLinks(msg);
					holder.messageText.setText(text);
					holder.messageText.setMovementMethod(LinkMovementMethod.getInstance());
					holder.messageText.setVisibility(View.VISIBLE);
				}
			}

			if (message.isOutgoing()) {
				holder.fileTransferAction.setText(getString(R.string.cancel));
				holder.fileTransferAction.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						if (LinphoneManager.getInstance().getMessageUploadPending() != null) {
							holder.fileTransferProgressBar.setVisibility(View.GONE);
							holder.fileTransferProgressBar.setProgress(0);
							message.cancelFileTransfer();
							LinphoneManager.getInstance().setUploadPendingFileMessage(null);
						}
					}
				});
				chatRoom.markAsRead();

			} else {
				holder.imdmLayout.setVisibility(View.INVISIBLE);
				holder.fileTransferAction.setText(getString(R.string.accept));
				holder.fileTransferAction.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						if (context.getPackageManager().checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, context.getPackageName()) == PackageManager.PERMISSION_GRANTED) {
							v.setEnabled(false);
							String filename = message.getFileTransferInformation().getName();
							String filename2 = context.getString(R.string.temp_photo_name_with_date).replace("%s", String.valueOf(System.currentTimeMillis())) ; //+ "." + extension;
							File file = new File(Environment.getExternalStorageDirectory(), filename);
							message.setAppdata(file.getPath());
							LinphoneManager.getInstance().addDownloadMessagePending(message);
							message.setListener(LinphoneManager.getInstance());
							message.setFileTransferFilepath(file.getPath());
							message.downloadFile();
						} else {
							Log.w("WRITE_EXTERNAL_STORAGE permission not granted, won't be able to store the downloaded file");
							LinphoneActivity.instance().checkAndRequestExternalStoragePermission();
						}
					}
				});
				chatRoom.markAsRead();
			}


			RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
			if (message.isOutgoing()) {
				layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
				layoutParams.setMargins(100, 10, 10, 10);
				holder.background.setBackgroundResource(R.drawable.resizable_chat_bubble_outgoing);
				Compatibility.setTextAppearance(holder.contactName, getActivity(), R.style.font3);
				Compatibility.setTextAppearance(holder.fileTransferAction, getActivity(), R.style.font15);
				holder.fileTransferAction.setBackgroundResource(R.drawable.resizable_confirm_delete_button);
				holder.contactPictureMask.setImageResource(R.drawable.avatar_chat_mask_outgoing);
			} else {
				if (isEditMode) {
					layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
					layoutParams.setMargins(100, 10, 10, 10);
				} else {
					layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
					layoutParams.setMargins(10, 10, 100, 10);
				}
				holder.background.setBackgroundResource(R.drawable.resizable_chat_bubble_incoming);
				Compatibility.setTextAppearance(holder.contactName, getActivity(), R.style.font9);
				Compatibility.setTextAppearance(holder.fileTransferAction, getActivity(), R.style.font8);
				holder.fileTransferAction.setBackgroundResource(R.drawable.resizable_assistant_button);
				holder.contactPictureMask.setImageResource(R.drawable.avatar_chat_mask);
			}
			holder.bubbleLayout.setLayoutParams(layoutParams);

			if(message.getAppdata() != null && holder.fileTransferLayout.getVisibility() != View.VISIBLE){
				if(LinphoneUtils.isExtensionImage(message.getAppdata())){
					holder.fileExtensionLabel.setVisibility(View.GONE);
					holder.fileNameLabel.setVisibility(View.GONE);
				}else {
					String extension = (LinphoneUtils.getExtensionFromFileName(message.getAppdata()));
					if(extension != null)
						extension = extension.toUpperCase();
					else
						extension = "FILE";

					if (extension.length() > 4)
						extension = extension.substring(0, 3);

					holder.fileExtensionLabel.setText(extension);
					holder.fileExtensionLabel.setVisibility(View.VISIBLE);
					holder.fileNameLabel.setText(LinphoneUtils.getNameFromFilePath(message.getAppdata()));
					holder.fileNameLabel.setVisibility(View.VISIBLE);
					holder.fileExtensionLabel.setOnClickListener(new OnClickListener() {
						@Override
						public void onClick(View v) {
							Intent intent = new Intent(Intent.ACTION_VIEW);
							File file = null;
							Uri contentUri = null;
							String imageUri = (String)holder.messageImage.getTag();
							if (imageUri.startsWith("file://")) {
								imageUri = imageUri.substring("file://".length());
								file = new File(imageUri);
								contentUri = FileProvider.getUriForFile(getActivity(), "org.linphone.provider", file);
							} else if (imageUri.startsWith("content://")) {
								contentUri = Uri.parse(imageUri);
							} else {
								file = new File(imageUri);
								contentUri = FileProvider.getUriForFile(getActivity(), "org.linphone.provider", file);
							}
							String type = null;
							String extension = MimeTypeMap.getFileExtensionFromUrl(contentUri.toString());
							if (extension != null) {
								type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
							}
							if(type != null) {
								intent.setDataAndType(contentUri, type);
							}else {
								intent.setDataAndType(contentUri, "*/*");
							}
							intent.addFlags(FLAG_GRANT_READ_URI_PERMISSION);
							context.startActivity(intent);
						}
					});
				}
			}

			if (isEditMode) {
				holder.delete.setVisibility(View.VISIBLE);
				holder.delete.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
					@Override
					public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
						messagesList.setItemChecked(position, b);
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

				if (messagesList.isItemChecked(position)) {
					holder.delete.setChecked(true);
				} else {
					holder.delete.setChecked(false);
				}
			}

			if (getResources().getBoolean(R.bool.isTablet)) {
				for (ChatUpdatedListener c : ChatUpdatedListeners) {
					c.onChatUpdated();
				}
			}

			return view;
		}

		private String timestampToHumanDate(Context context, long timestamp) {
			try {
				Calendar cal = Calendar.getInstance();
				cal.setTimeInMillis(timestamp);

				SimpleDateFormat dateFormat;
				if (isToday(cal)) {
					dateFormat = new SimpleDateFormat(context.getResources().getString(R.string.today_date_format));
				} else {
					dateFormat = new SimpleDateFormat(context.getResources().getString(R.string.messages_date_format));
				}

				return dateFormat.format(cal.getTime());
			} catch (NumberFormatException nfe) {
				return String.valueOf(timestamp);
			}
		}

		private boolean isToday(Calendar cal) {
			return isSameDay(cal, Calendar.getInstance());
		}

		private boolean isSameDay(Calendar cal1, Calendar cal2) {
			if (cal1 == null || cal2 == null) {
				return false;
			}

			return (cal1.get(Calendar.ERA) == cal2.get(Calendar.ERA) &&
					cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
					cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR));
		}

		private Spanned getTextWithHttpLinks(String text) {
			if (text.contains("<")) {
				text = text.replace("<", "&lt;");
			}
			if (text.contains(">")) {
				text = text.replace(">", "&gt;");
			}
			if (text.contains("\n")) {
				text = text.replace("\n", "<br>");
			}
			if (text.contains("http://")) {
				int indexHttp = text.indexOf("http://");
				int indexFinHttp = text.indexOf(" ", indexHttp) == -1 ? text.length() : text.indexOf(" ", indexHttp);
				String link = text.substring(indexHttp, indexFinHttp);
				String linkWithoutScheme = link.replace("http://", "");
				text = text.replaceFirst(Pattern.quote(link), "<a href=\"" + link + "\">" + linkWithoutScheme + "</a>");
			}
			if (text.contains("https://")) {
				int indexHttp = text.indexOf("https://");
				int indexFinHttp = text.indexOf(" ", indexHttp) == -1 ? text.length() : text.indexOf(" ", indexHttp);
				String link = text.substring(indexHttp, indexFinHttp);
				String linkWithoutScheme = link.replace("https://", "");
				text = text.replaceFirst(Pattern.quote(link), "<a href=\"" + link + "\">" + linkWithoutScheme + "</a>");
			}

			return Compatibility.fromHtml(text);
		}

		public void loadBitmap(String path, ImageView imageView) {
			if (cancelPotentialWork(path, imageView)) {
				if(LinphoneUtils.isExtensionImage(path))
					defaultBitmap = BitmapFactory.decodeResource(getActivity().getResources(), R.drawable.chat_attachment_over);
				else
					defaultBitmap = BitmapFactory.decodeResource(getActivity().getResources(), R.drawable.chat_attachment);

				BitmapWorkerTask task = new BitmapWorkerTask(imageView);
				final AsyncBitmap asyncBitmap = new AsyncBitmap(context.getResources(), defaultBitmap, task);
				imageView.setImageDrawable(asyncBitmap);
				task.execute(path);
			}
		}

		private class BitmapWorkerTask extends AsyncTask<String, Void, Bitmap> {
			private final WeakReference<ImageView> imageViewReference;
			public String path;

			public BitmapWorkerTask(ImageView imageView) {
				path = null;
				// Use a WeakReference to ensure the ImageView can be garbage collected
				imageViewReference = new WeakReference<ImageView>(imageView);
			}

			// Decode image in background.
			@Override
			protected Bitmap doInBackground(String... params) {
				path = params[0];
				Bitmap bm = null;
				Bitmap thumbnail = null;
				if(LinphoneUtils.isExtensionImage(path)) {
					if (path.startsWith("content")) {
						try {
							bm = MediaStore.Images.Media.getBitmap(context.getContentResolver(), Uri.parse(path));
						} catch (FileNotFoundException e) {
							Log.e(e);
						} catch (IOException e) {
							Log.e(e);
						}
					} else {
						bm = BitmapFactory.decodeFile(path);
					}

					// Rotate the bitmap if possible/needed, using EXIF data
					try {
						Bitmap bm_tmp;
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
						bm_tmp = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), matrix, true);
						if (bm_tmp != bm) {
							bm.recycle();
							bm = bm_tmp;
						} else {
							bm_tmp = null;
						}
					} catch (Exception e) {
						Log.e(e);
					}

					if (bm != null) {
						thumbnail = ThumbnailUtils.extractThumbnail(bm, SIZE_SMALL, SIZE_SMALL);
						bm.recycle();
					}
					return thumbnail;
				}else
					return defaultBitmap;
			}

			// Once complete, see if ImageView is still around and set bitmap.
			@Override
			protected void onPostExecute(Bitmap bitmap) {
				if (isCancelled()) {
					bitmap = null;
				}
				if (imageViewReference != null && bitmap != null) {
					final ImageView imageView = imageViewReference.get();
					final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);
					if (this == bitmapWorkerTask && imageView != null) {
						imageView.setImageBitmap(bitmap);
						//Force scroll too bottom with setSelection() after image loaded and last messages
						if(((messagesList.getLastVisiblePosition() >= (getCount() - 1)) && (messagesList.getFirstVisiblePosition() <= (getCount() - 1))))
							messagesList.setSelection(getCount() - 1);
						imageView.setTag(path);
						imageView.setOnClickListener(new OnClickListener() {
							@Override
							public void onClick(View v) {
								Intent intent = new Intent(Intent.ACTION_VIEW);
								File file = null;
								Uri contentUri = null;
								String imageUri = (String)v.getTag();
								if (imageUri.startsWith("file://")) {
									imageUri = imageUri.substring("file://".length());
									file = new File(imageUri);
									contentUri = FileProvider.getUriForFile(getActivity(), "org.linphone.provider", file);
								} else if (imageUri.startsWith("content://")) {
									contentUri = Uri.parse(imageUri);
								} else {
									file = new File(imageUri);
									contentUri = FileProvider.getUriForFile(getActivity(), "org.linphone.provider", file);
								}
							    String type = null;
                                String extension = MimeTypeMap.getFileExtensionFromUrl(contentUri.toString());
                                if (extension != null) {
                                    type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                                }
                                if(type != null) {
                                    intent.setDataAndType(contentUri, type);
                                }else {
                                    intent.setDataAndType(contentUri, "*/*");
                                }
                                intent.addFlags(FLAG_GRANT_READ_URI_PERMISSION);
								context.startActivity(intent);
							}
						});
					}
				}
			}
		}

		class AsyncBitmap extends BitmapDrawable {
			private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

			public AsyncBitmap(Resources res, Bitmap bitmap, BitmapWorkerTask bitmapWorkerTask) {
				super(res, bitmap);
				bitmapWorkerTaskReference = new WeakReference<BitmapWorkerTask>(bitmapWorkerTask);
			}

			public BitmapWorkerTask getBitmapWorkerTask() {
				return bitmapWorkerTaskReference.get();
			}
		}

		private boolean cancelPotentialWork(String path, ImageView imageView) {
			final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

			if (bitmapWorkerTask != null) {
				final String bitmapData = bitmapWorkerTask.path;
				// If bitmapData is not yet set or it differs from the new data
				if (bitmapData == null || bitmapData != path) {
					// Cancel previous task
					bitmapWorkerTask.cancel(true);
				} else {
					// The same work is already in progress
					return false;
				}
			}
			// No task associated with the ImageView, or an existing task was cancelled
			return true;
		}

		private BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {
			if (imageView != null) {
				final Drawable drawable = imageView.getDrawable();
				if (drawable instanceof AsyncBitmap) {
					final AsyncBitmap asyncDrawable = (AsyncBitmap) drawable;
					return asyncDrawable.getBitmapWorkerTask();
				}
			}
			return null;
		}
	}

	//ChatMessage Listener
	@Override
	public void onMsgStateChanged(ChatMessage msg, State state) {
		redrawMessageList();
	}

	@Override
	public void onFileTransferRecv(ChatMessage msg, Content content, Buffer buffer) {}

	@Override
	public Buffer onFileTransferSend(ChatMessage msg, Content content, int offset, int size) {
		return null;
	}

	@Override
	public void onFileTransferProgressIndication(ChatMessage msg, Content content, int offset, int total) {}
}
