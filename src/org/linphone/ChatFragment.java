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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.linphone.compatibility.Compatibility;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneBuffer;
import org.linphone.core.LinphoneChatMessage;
import org.linphone.core.LinphoneChatMessage.LinphoneChatMessageListener;
import org.linphone.core.LinphoneChatMessage.State;
import org.linphone.core.LinphoneChatRoom;
import org.linphone.core.LinphoneContent;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneCoreListenerBase;
import org.linphone.mediastream.Log;
import org.linphone.ui.AvatarWithShadow;
import org.linphone.ui.BubbleChat;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v4.content.CursorLoader;
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
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

public class ChatFragment extends Fragment implements OnClickListener, LinphoneChatMessageListener {
	private static ChatFragment instance;

	private static final int ADD_PHOTO = 1337;
	private static final int MENU_DELETE_MESSAGE = 0;
	private static final int MENU_COPY_TEXT = 6;
	private static final int MENU_RESEND_MESSAGE = 7;

	private LinphoneChatRoom chatRoom;
	private String sipUri;
	private String displayName;
	private String pictureUri;
	private EditText message;
	private ImageView cancelUpload;
	private LinearLayout topBar;
	private TextView sendImage, sendMessage, contactName, remoteComposing, back;
	private AvatarWithShadow contactPicture;
	private RelativeLayout uploadLayout, textLayout;
	private ListView messagesList;

	private ProgressBar progressBar;
	private Uri imageToUploadUri;
	private TextWatcher textWatcher;
	private ViewTreeObserver.OnGlobalLayoutListener keyboardListener;
	private ChatMessageAdapter adapter;
	private Handler mHandler = new Handler();
	
	private LinphoneCoreListenerBase mListener;
	private ByteArrayOutputStream mDownloadedImageStream;
	private int mDownloadedImageStreamSize;
	private LinphoneChatMessage currentMessageInFileTransferUploadState;

	public static boolean isInstanciated() {
		return instance != null;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		instance = this;
		View view = inflater.inflate(R.layout.chat, container, false);

		//Retrieve parameter from intent
		sipUri = getArguments().getString("SipUri");
		displayName = getArguments().getString("DisplayName");
		pictureUri = getArguments().getString("PictureUri");

		//Initialize UI
		contactName = (TextView) view.findViewById(R.id.contactName);
		contactPicture = (AvatarWithShadow) view.findViewById(R.id.contactPicture);
		messagesList = (ListView) view.findViewById(R.id.chatMessageList);
		textLayout = (RelativeLayout) view.findViewById(R.id.messageLayout);
		progressBar = (ProgressBar) view.findViewById(R.id.progressbar);
		topBar = (LinearLayout) view.findViewById(R.id.topbar);

		sendMessage = (TextView) view.findViewById(R.id.sendMessage);
		sendMessage.setOnClickListener(this);

		remoteComposing = (TextView) view.findViewById(R.id.remoteComposing);
		remoteComposing.setVisibility(View.GONE);

		uploadLayout = (RelativeLayout) view.findViewById(R.id.uploadLayout);
		uploadLayout.setVisibility(View.GONE);

		displayChatHeader(displayName, pictureUri);

		//Manage multiline
		message = (EditText) view.findViewById(R.id.message);
		if (!getResources().getBoolean(R.bool.allow_chat_multiline)) {
			message.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE);
			message.setMaxLines(1);
		}

		sendImage = (TextView) view.findViewById(R.id.sendPicture);
		if (!getResources().getBoolean(R.bool.disable_chat_send_file)) {
			sendImage.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					pickImage();
				}
			});
		} else {
			sendImage.setEnabled(false);
		}

		back = (TextView) view.findViewById(R.id.back);
		if (back != null) {
			back.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					getActivity().finish();
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
				}
			}
		});

		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			chatRoom = lc.getOrCreateChatRoom(sipUri);
			//Only works if using liblinphone storage
			chatRoom.markAsRead();
		}
		
		mListener = new LinphoneCoreListenerBase(){
			@Override
			public void messageReceived(LinphoneCore lc, LinphoneChatRoom cr, LinphoneChatMessage message) {
				LinphoneAddress from = cr.getPeerAddress();
				if (from.asStringUriOnly().equals(sipUri)) {
					invalidate();
					scrollToEnd();
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
		contactPicture.setVisibility(View.GONE);
		scrollToEnd();
	}

	public void hideKeyboardVisibleMode() {
		boolean isOrientationLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
		contactPicture.setVisibility(View.VISIBLE);
		if (isOrientationLandscape && topBar != null) {
			topBar.setVisibility(View.VISIBLE);
		}
		scrollToEnd();
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
			
			BubbleChat bubble = new BubbleChat(context, message, ChatFragment.this);
			View v = bubble.getView();

			registerForContextMenu(v);
			RelativeLayout rlayout = new RelativeLayout(context);
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
		if (displayName == null && getResources().getBoolean(R.bool.only_display_username_if_unknown) && LinphoneUtils.isSipAddress(sipUri)) {
			contactName.setText(LinphoneUtils.getUsernameFromAddress(sipUri));
		} else if (displayName == null) {
			contactName.setText(sipUri);
		} else {
			contactName.setText(displayName);
		}

		if (pictureUri != null) {
			LinphoneUtils.setImagePictureFromUri(getActivity(), contactPicture.getView(), Uri.parse(pictureUri), R.drawable.unknown_small);
		} else {
			contactPicture.setImageResource(R.drawable.unknown_small);
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
		menu.add(v.getId(), MENU_DELETE_MESSAGE, 0, getString(R.string.delete));
		menu.add(v.getId(), MENU_COPY_TEXT, 0, getString(R.string.copy_text));

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
		}
		return true;
	}

	@Override
	public void onPause() {
		message.removeTextChangedListener(textWatcher);
		removeVirtualKeyboardVisiblityListener();

		LinphoneService.instance().removeMessageNotification();

		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			lc.removeListener(mListener);
		}

		getActivity().getIntent().putExtra("messageDraft", message.getText().toString());
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
		}

		String draft = getArguments().getString("messageDraft");
		message.setText(draft);

		remoteComposing.setVisibility(chatRoom.isRemoteComposing() ? View.VISIBLE : View.GONE);
		dispayMessageList();

		super.onResume();
	}

	@Override
	public void onClick(View v) {
		sendTextMessage();
	}

	private void sendTextMessage() {
		sendTextMessage(message.getText().toString());
		message.setText("");
	}

	private void sendTextMessage(String messageToSend) {
		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		boolean isNetworkReachable = lc == null ? false : lc.isNetworkReachable();

		if (chatRoom != null && messageToSend != null && messageToSend.length() > 0 && isNetworkReachable) {
			LinphoneChatMessage message = chatRoom.createLinphoneChatMessage(messageToSend);
			message.setListener(this);
			chatRoom.sendChatMessage(message);

			if (LinphoneActivity.isInstanciated()) {
				LinphoneActivity.instance().onMessageSent(sipUri, messageToSend);
			}

			invalidate();

			Log.i("Sent message current status: " + message.getStatus());
			scrollToEnd();
		} else if (!isNetworkReachable && LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().displayCustomToast(getString(R.string.error_network_unreachable), Toast.LENGTH_LONG);
		}
	}

	private void sendImageMessage(String path) {
		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		boolean isNetworkReachable = lc == null ? false : lc.isNetworkReachable();

		if (chatRoom != null && path != null && path.length() > 0 && isNetworkReachable) {
			Bitmap bm = BitmapFactory.decodeFile(path);
			if (bm != null) {
				ByteArrayOutputStream stream = new ByteArrayOutputStream();
				bm.compress(Bitmap.CompressFormat.PNG, 100, stream);
				byte[] byteArray = stream.toByteArray();
				
				LinphoneContent content = LinphoneCoreFactory.instance().createLinphoneContent("image", "jpeg", byteArray, null);
				String fileName = path.substring(path.lastIndexOf("/") + 1);
				content.setName(fileName);
				
				LinphoneChatMessage message = chatRoom.createFileTransferMessage(content);
				message.setFileTransferFilepath(path);
				message.setListener(this);
				message.setAppData(path);
				
				uploadLayout.setVisibility(View.VISIBLE);
				textLayout.setVisibility(View.GONE);
				
				chatRoom.sendChatMessage(message);
				currentMessageInFileTransferUploadState = message;
			} else {
				Log.e("Error, bitmap factory can't read " + path);
			}
		} else if (!isNetworkReachable && LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().displayCustomToast(getString(R.string.error_network_unreachable), Toast.LENGTH_LONG);
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
			sendImageMessage(message.getAppData());
		}
	}

	private void scrollToEnd() {
		messagesList.smoothScrollToPosition(messagesList.getCount());
		chatRoom.markAsRead();
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
				final String filePath = fileToUploadPath;
				mHandler.post(new Runnable() {
					@Override
					public void run() {
						sendImageMessage(filePath);
					}
				});
			}
		} else {
			super.onActivityResult(requestCode, resultCode, data);
		}
	}

	@Override
	public void onLinphoneChatMessageStateChanged(LinphoneChatMessage msg, State state) {
		if (LinphoneActivity.isInstanciated() && state != LinphoneChatMessage.State.InProgress) {
			if (msg != null) {
				LinphoneActivity.instance().onMessageStateChanged(sipUri, msg.getText(), state.toInt());
			}
			invalidate();
		}
		
		if (state == State.FileTransferDone && mDownloadedImageStream != null) {
			byte[] bytes = mDownloadedImageStream.toByteArray();
			Bitmap bm = BitmapFactory.decodeByteArray(bytes, 0, mDownloadedImageStreamSize);
			
			String path = msg.getExternalBodyUrl();
			String fileName = path.substring(path.lastIndexOf("/") + 1);
			String url = MediaStore.Images.Media.insertImage(getActivity().getContentResolver(), bm, fileName, null);
			if (url != null) {
				msg.setAppData(url);
			}
			
			mDownloadedImageStream = null;
			mDownloadedImageStreamSize = 0;
		} else if (state == State.FileTransferDone || state == State.FileTransferError) {
			uploadLayout.setVisibility(View.GONE);
			textLayout.setVisibility(View.VISIBLE);
			progressBar.setProgress(0);
			currentMessageInFileTransferUploadState = null;
		}
		invalidate();
	}

	@Override
	public void onLinphoneChatMessageFileTransferReceived(LinphoneChatMessage msg, LinphoneContent content, LinphoneBuffer buffer) {
		if (mDownloadedImageStream == null) {
			mDownloadedImageStream = new ByteArrayOutputStream();
			mDownloadedImageStreamSize = 0;
		}

		if (buffer != null && buffer.getSize() > 0) {
			try {
				mDownloadedImageStream.write(buffer.getContent());
				mDownloadedImageStreamSize += buffer.getSize();
			} catch (IOException e) {
				Log.e(e);
			}
		}
	}

	@Override
	public void onLinphoneChatMessageFileTransferSent(LinphoneChatMessage msg, LinphoneContent content, int offset, int size, LinphoneBuffer bufferToFill) {
		
	}

	@Override
	public void onLinphoneChatMessageFileTransferProgressChanged(LinphoneChatMessage msg, LinphoneContent content, int offset, int total) {
		progressBar.setProgress(offset * 100 / total);
	}
}
