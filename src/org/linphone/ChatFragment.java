package org.linphone;
/*
ChatFragment.java
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
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.http.util.ByteArrayBuffer;
import org.linphone.LinphoneSimpleListener.LinphoneOnComposingReceivedListener;
import org.linphone.compatibility.Compatibility;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneChatMessage;
import org.linphone.core.LinphoneChatMessage.State;
import org.linphone.core.LinphoneChatRoom;
import org.linphone.core.LinphoneCore;
import org.linphone.mediastream.Log;
import org.linphone.ui.AvatarWithShadow;
import org.linphone.ui.BubbleChat;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v4.content.CursorLoader;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * @author Sylvain Berfini
 */
public class ChatFragment extends Fragment implements OnClickListener, LinphoneChatMessage.StateListener, LinphoneOnComposingReceivedListener {
	private static final int ADD_PHOTO = 1337;
	private static final int MENU_DELETE_MESSAGE = 0;
	private static final int MENU_SAVE_PICTURE = 1;
	private static final int MENU_PICTURE_SMALL = 2;
	private static final int MENU_PICTURE_MEDIUM = 3;
	private static final int MENU_PICTURE_LARGE = 4;
	private static final int MENU_PICTURE_REAL = 5;
	private static final int MENU_COPY_TEXT = 6;
	private static final int MENU_RESEND_MESSAGE = 7;
	private static final int COMPRESSOR_QUALITY = 100;
	private static final int SIZE_SMALL = 500;
	private static final int SIZE_MEDIUM = 1000;
	private static final int SIZE_LARGE = 1500;

	private LinphoneChatRoom chatRoom;
	private View view;
	private String sipUri;
	private EditText message;
	private ImageView cancelUpload;
	private TextView sendImage, sendMessage, contactName, remoteComposing;
	private AvatarWithShadow contactPicture;
	private RelativeLayout uploadLayout, textLayout;
	private Handler mHandler = new Handler();
	private List<BubbleChat> lastSentMessagesBubbles;
	private HashMap<Integer, String> latestImageMessages;
	private boolean useLinphoneMessageStorage;
	private List<Integer> openImagesId ;
	private ListView messagesList;

	private ProgressBar progressBar;
	private int bytesSent;
	private String uploadServerUri;
	private String fileToUploadPath;
	private Bitmap imageToUpload;
	private Uri imageToUploadUri;
	private Thread uploadThread;
	private TextWatcher textWatcher;
	private OnGlobalLayoutListener keyboardListener;
	private ChatMessageAdapter adapter;

	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		sipUri = getArguments().getString("SipUri");
		String displayName = getArguments().getString("DisplayName");
		String pictureUri = getArguments().getString("PictureUri");

        view = inflater.inflate(R.layout.chat, container, false);

        useLinphoneMessageStorage = getResources().getBoolean(R.bool.use_linphone_chat_storage);

        contactName = (TextView) view.findViewById(R.id.contactName);
        contactPicture = (AvatarWithShadow) view.findViewById(R.id.contactPicture);

        sendMessage = (TextView) view.findViewById(R.id.sendMessage);
        sendMessage.setOnClickListener(this);

        remoteComposing = (TextView) view.findViewById(R.id.remoteComposing);
        remoteComposing.setVisibility(View.GONE);
        
        messagesList = (ListView) view.findViewById(R.id.chatMessageList);
        openImagesId = new ArrayList<Integer>();
        
        message = (EditText) view.findViewById(R.id.message);
        if (!getActivity().getResources().getBoolean(R.bool.allow_chat_multiline)) {
        	message.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE);
        	message.setMaxLines(1);
        }
        uploadLayout = (RelativeLayout) view.findViewById(R.id.uploadLayout);
        textLayout = (RelativeLayout) view.findViewById(R.id.messageLayout);
        
        progressBar = (ProgressBar) view.findViewById(R.id.progressbar);
        sendImage = (TextView) view.findViewById(R.id.sendPicture);
        if (!getResources().getBoolean(R.bool.disable_chat_send_file)) {
	        registerForContextMenu(sendImage);
	        sendImage.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					pickImage();
				}
			});
        } else {
        	sendImage.setEnabled(false);
        }
  
        cancelUpload = (ImageView) view.findViewById(R.id.cancelUpload);
        cancelUpload.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				uploadThread.interrupt();
				uploadLayout.setVisibility(View.GONE);
				textLayout.setVisibility(View.VISIBLE);
				progressBar.setProgress(0);
			}
		});

        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			chatRoom = lc.getOrCreateChatRoom(sipUri);
			//Only works if using liblinphone storage
			chatRoom.markAsRead();
		}
		
        displayChatHeader(displayName, pictureUri);

		uploadServerUri = LinphonePreferences.instance().getSharingPictureServerUrl();

        textWatcher = new TextWatcher() {
			public void afterTextChanged(Editable arg0) {

			}

			public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {

			}

			public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3)
			{
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
		if (LinphoneActivity.isInstanciated()) {
			InputMethodManager imm = (InputMethodManager) LinphoneActivity.instance().getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.hideSoftInputFromWindow(view.getWindowToken(),0);
		}

		// Workaround for SGS3 issue
		if (savedInstanceState != null) {
			fileToUploadPath = savedInstanceState.getString("fileToUploadPath");
			imageToUpload = savedInstanceState.getParcelable("imageToUpload");
		}
		if (fileToUploadPath != null || imageToUpload != null) {
			sendImage.post(new Runnable() {
				@Override
				public void run() {
					sendImage.showContextMenu();
				}
			});
		}

		return view;
    }

	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putString("fileToUploadPath", fileToUploadPath);
		outState.putParcelable("imageToUpload", imageToUpload);
		super.onSaveInstanceState(outState);
	}

	private void addVirtualKeyboardVisiblityListener() {
		keyboardListener = new OnGlobalLayoutListener() {
			@Override
			public void onGlobalLayout() {
			    Rect visibleArea = new Rect();
			    view.getWindowVisibleDisplayFrame(visibleArea);

			    int heightDiff = view.getRootView().getHeight() - (visibleArea.bottom - visibleArea.top);
			    if (heightDiff > 200) {
			    	showKeyboardVisibleMode();
			    } else {
			    	hideKeyboardVisibleMode();
			    }
			}
		};
		view.getViewTreeObserver().addOnGlobalLayoutListener(keyboardListener);
	}

	private void removeVirtualKeyboardVisiblityListener() {
		Compatibility.removeGlobalLayoutListener(view.getViewTreeObserver(), keyboardListener);
	}

	public void showKeyboardVisibleMode() {
		LinphoneActivity.instance().hideMenu(true);
		contactPicture.setVisibility(View.GONE);
		scrollToEnd();
	}

	public void hideKeyboardVisibleMode() {
		LinphoneActivity.instance().hideMenu(false);
		contactPicture.setVisibility(View.VISIBLE);
		//scrollToEnd();
	}
	
	class ChatMessageAdapter extends BaseAdapter {
		LinphoneChatMessage[] history;
		Context context;
		 
		public ChatMessageAdapter(Context context, LinphoneChatMessage[] history) {
			this.history = history; 
			this.context = context;
		}
		
		public void refreshHistory(){
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
			BubbleChat bubble;
			LinphoneChatMessage msg = history[position];
			View v;
			Boolean showImage = false;
			
			if (msg.getExternalBodyUrl() != null ) {
				if(!openImagesId.isEmpty() && openImagesId.contains(msg.getStorageId())) showImage = true;	
				bubble = displayImageMessage(msg.getStorageId(), null, msg.getTime(), !msg.isOutgoing(), msg.getStatus(), context, msg.getExternalBodyUrl(),showImage);
			} else {
				bubble = displayMessage(msg.getStorageId(), msg.getText(), msg.getTime(), !msg.isOutgoing(), msg.getStatus(), context);
				
			}
			
			v = bubble.getView();
			bubble.setNativeMessageObject(msg);
			registerForContextMenu(v);
			
			RelativeLayout rlayout = new RelativeLayout(context);
			rlayout.addView(v);
			
			return rlayout;
		}
	 }
	
	public View getBubble(int position){
		return messagesList.getChildAt(position);
	}
	
	public void dispayMessageList(){
		 adapter = new ChatMessageAdapter(this.getActivity(), chatRoom.getHistory());
		 messagesList.setAdapter(adapter);  
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
        	LinphoneUtils.setImagePictureFromUri(view.getContext(), contactPicture.getView(), Uri.parse(pictureUri), R.drawable.unknown_small);
        } else {
        	contactPicture.setImageResource(R.drawable.unknown_small);
        }
	}

	private BubbleChat displayMessage(int id, String message, long time, boolean isIncoming, LinphoneChatMessage.State status, Context context) {
		BubbleChat bubble = new BubbleChat(context, id, message, null, time, isIncoming, status, null);
		if (!isIncoming) {
			if (lastSentMessagesBubbles == null)
				lastSentMessagesBubbles = new ArrayList<BubbleChat>();
			lastSentMessagesBubbles.add(bubble);
		}
		return bubble;
	}

	private BubbleChat displayImageMessage(int id, Bitmap image, long time, boolean isIncoming, LinphoneChatMessage.State status, Context context, final String url, boolean show) {
		final BubbleChat bubble = new BubbleChat(context, id, null, image, time, isIncoming, status, url);
		if (!isIncoming) {
			if (lastSentMessagesBubbles == null)
				lastSentMessagesBubbles = new ArrayList<BubbleChat>();
			lastSentMessagesBubbles.add(bubble);
		}

		final View v = bubble.getView();
		final int finalId = id;

		if (url.startsWith("http")) { // Download
			bubble.setShowOrDownloadText(getString(R.string.download_image));
			bubble.setShowOrDownloadImageButtonListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
					v.findViewById(R.id.spinner).setVisibility(View.VISIBLE);
					v.findViewById(R.id.download).setVisibility(View.GONE);

					new Thread(new Runnable() {
						@Override
						public void run() {
							final Bitmap bm = ChatFragment.downloadImage(url);
							if (bm != null) {
								if (useLinphoneMessageStorage) {
									String newFileUrl = saveImage(bm, finalId, getMessageForId(finalId));
									bubble.updateUrl(newFileUrl);
									adapter.refreshHistory();
									openImagesId.add(finalId);
								} else {
									LinphoneActivity.instance().getChatStorage().saveImage(finalId, bm);
								}
								mHandler.post(new Runnable() {
									@Override
									public void run() {
										((ImageView)v.findViewById(R.id.image)).setImageBitmap(bm);
										v.findViewById(R.id.image).setVisibility(View.VISIBLE);
										v.findViewById(R.id.spinner).setVisibility(View.GONE);
									}
								});
							} else {
								mHandler.post(new Runnable() {
									@Override
									public void run() {
										v.findViewById(R.id.spinner).setVisibility(View.GONE);
										v.findViewById(R.id.download).setVisibility(View.VISIBLE);
										LinphoneActivity.instance().displayCustomToast(getString(R.string.download_image_failed), Toast.LENGTH_LONG);
									}
								});
							}
						}
					}).start();
				}
			});
		} else { // Show
			if(show){
				Bitmap bm = BitmapFactory.decodeFile(url);
				((ImageView)v.findViewById(R.id.image)).setImageBitmap(bm);
				v.findViewById(R.id.image).setVisibility(View.VISIBLE);
				v.findViewById(R.id.download).setVisibility(View.GONE);
			} else {
				bubble.setShowOrDownloadText(getString(R.string.show_image));
				bubble.setShowOrDownloadImageButtonListener(new OnClickListener() {
					@Override
					public void onClick(View view) {
						Bitmap bm = BitmapFactory.decodeFile(url);
						if (bm != null) {
							openImagesId.add(finalId);
							((ImageView)v.findViewById(R.id.image)).setImageBitmap(bm);
							v.findViewById(R.id.image).setVisibility(View.VISIBLE);
							v.findViewById(R.id.download).setVisibility(View.GONE);
						}
					}
				});
			}	
		}
		
		return bubble;
	}

	public void changeDisplayedChat(String newSipUri, String displayName, String pictureUri) {
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

		sipUri = newSipUri;
		if (LinphoneActivity.isInstanciated()) {
			String draft = LinphoneActivity.instance().getChatStorage().getDraft(sipUri);
			if (draft == null)
				draft = "";
			message.setText(draft);
		}

		displayChatHeader(displayName, pictureUri);
		displayMessages();
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		if (v.getId() == R.id.sendPicture) {
			menu.add(0, MENU_PICTURE_SMALL, 0, getString(R.string.share_picture_size_small));
			menu.add(0, MENU_PICTURE_MEDIUM, 0, getString(R.string.share_picture_size_medium));
			menu.add(0, MENU_PICTURE_LARGE, 0, getString(R.string.share_picture_size_large));
//			Not a good idea, very big pictures cause Out of Memory exceptions, slow display, ...
//			menu.add(0, MENU_PICTURE_REAL, 0, getString(R.string.share_picture_size_real));
		} else {
			menu.add(v.getId(), MENU_DELETE_MESSAGE, 0, getString(R.string.delete));
			ImageView iv = (ImageView) v.findViewById(R.id.image);
			if (iv != null && iv.getVisibility() == View.VISIBLE) {
				if (!useLinphoneMessageStorage) {
					menu.add(v.getId(), MENU_SAVE_PICTURE, 0, getString(R.string.save_picture));
				}
			} else {
				menu.add(v.getId(), MENU_COPY_TEXT, 0, getString(R.string.copy_text));
			}

			LinphoneChatMessage msg = getMessageForId(v.getId());
			if (msg != null && msg.getStatus() == LinphoneChatMessage.State.NotDelivered) {
				menu.add(v.getId(), MENU_RESEND_MESSAGE, 0, getString(R.string.retry));
			}
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case MENU_DELETE_MESSAGE:
			LinphoneActivity.instance().getChatStorage().deleteMessage(chatRoom, item.getGroupId());
			hideMessageBubble(item.getGroupId());
			break;
		case MENU_SAVE_PICTURE:
			saveImage(item.getGroupId());
			break;
		case MENU_COPY_TEXT:
			copyTextMessageToClipboard(item.getGroupId());
			break;
		case MENU_PICTURE_SMALL:
			uploadAndSendImage(fileToUploadPath, imageToUpload, ImageSize.SMALL);
			break;
		case MENU_PICTURE_MEDIUM:
			uploadAndSendImage(fileToUploadPath, imageToUpload, ImageSize.MEDIUM);
			break;
		case MENU_PICTURE_LARGE:
			uploadAndSendImage(fileToUploadPath, imageToUpload, ImageSize.LARGE);
			break;
		case MENU_PICTURE_REAL:
			uploadAndSendImage(fileToUploadPath, imageToUpload, ImageSize.REAL);
			break;
		case MENU_RESEND_MESSAGE:
			resendMessage(item.getGroupId());
			break;
		}
		return true;
	}

	@Override
	public void onPause() {
		latestImageMessages = null;
		message.removeTextChangedListener(textWatcher);
		removeVirtualKeyboardVisiblityListener();

		LinphoneService.instance().removeMessageNotification();

		if (LinphoneManager.isInstanciated())
			LinphoneManager.getInstance().setOnComposingReceivedListener(null);

		super.onPause();

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
	}

	@SuppressLint("UseSparseArrays")
	@Override
	public void onResume() {
		latestImageMessages = new HashMap<Integer, String>();
		message.addTextChangedListener(textWatcher);
		addVirtualKeyboardVisiblityListener();

		if (LinphoneManager.isInstanciated())
			LinphoneManager.getInstance().setOnComposingReceivedListener(this);

		super.onResume();

		if (LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().selectMenu(FragmentsAvailable.CHAT);
			LinphoneActivity.instance().updateChatFragment(this);

			if (getResources().getBoolean(R.bool.show_statusbar_only_on_dialer)) {
				LinphoneActivity.instance().hideStatusBar();
			}
		}

		if (LinphoneActivity.isInstanciated()) {
			String draft = LinphoneActivity.instance().getChatStorage().getDraft(sipUri);
			message.setText(draft);
		}

		remoteComposing.setVisibility(chatRoom.isRemoteComposing() ? View.VISIBLE : View.GONE);

		displayMessages();
	}

	@Override
	public void onClick(View v) {
		sendTextMessage();
	}

	private void displayMessages() {
		dispayMessageList();
	}

	private void sendTextMessage() {
		sendTextMessage(message.getText().toString());
		message.setText("");
	}

	private void sendTextMessage(String messageToSend) {
		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		boolean isNetworkReachable = lc == null ? false : lc.isNetworkReachable();

		if (chatRoom != null && messageToSend != null && messageToSend.length() > 0 && isNetworkReachable) {
			LinphoneChatMessage chatMessage = chatRoom.createLinphoneChatMessage(messageToSend);
			chatRoom.sendMessage(chatMessage, this);

			if (LinphoneActivity.isInstanciated()) {
				LinphoneActivity.instance().onMessageSent(sipUri, messageToSend);	
			}

			adapter.refreshHistory();
			adapter.notifyDataSetChanged();
			
			Log.i("Sent message current status: " + chatMessage.getStatus());
			scrollToEnd();
		} else if (!isNetworkReachable && LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().displayCustomToast(getString(R.string.error_network_unreachable), Toast.LENGTH_LONG);
		}
	}

	private void sendImageMessage(String url, Bitmap bitmap) {
		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		boolean isNetworkReachable = lc == null ? false : lc.isNetworkReachable();

		if (chatRoom != null && url != null && url.length() > 0 && isNetworkReachable) {
			LinphoneChatMessage chatMessage = chatRoom.createLinphoneChatMessage("");
			chatMessage.setExternalBodyUrl(url);
			chatRoom.sendMessage(chatMessage, this);

			int newId = -1;
			if (LinphoneActivity.isInstanciated()) {
				newId = LinphoneActivity.instance().onMessageSent(sipUri, bitmap, url);
			}
			newId = chatMessage.getStorageId();
			latestImageMessages.put(newId, url);

			if (useLinphoneMessageStorage)
				url = saveImage(bitmap, newId, chatMessage);
			
			adapter.refreshHistory();
			adapter.notifyDataSetChanged();
			
			scrollToEnd();
		} else if (!isNetworkReachable && LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().displayCustomToast(getString(R.string.error_network_unreachable), Toast.LENGTH_LONG);
		}
	}

	private LinphoneChatMessage getMessageForId(int id) {
		LinphoneChatMessage msg = null;
		try {
			msg = LinphoneActivity.instance().getChatStorage().getMessage(chatRoom, id);
		} catch (Exception e) {}

		if (msg == null) {
			for (BubbleChat bubble : lastSentMessagesBubbles) {
				if (bubble.getId() == id) {
					return bubble.getNativeMessageObject();
				}
			}
		}

		return msg;
	}

	private void hideMessageBubble(int id) {
		adapter.refreshHistory();
		adapter.notifyDataSetChanged();
	}

	private void resendMessage(int id) {
		LinphoneChatMessage message = getMessageForId(id);
		if (message == null)
			return;

		LinphoneActivity.instance().getChatStorage().deleteMessage(chatRoom, id);
		hideMessageBubble(id);

		if (message.getText() != null && message.getText().length() > 0) {
			sendTextMessage(message.getText());
		} else {
			sendImageMessage(message.getExternalBodyUrl(), null);
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

	public void onMessageReceived(final int id, LinphoneAddress from, final LinphoneChatMessage message) {
		if (from.asStringUriOnly().equals(sipUri))  {
			if (message.getText() != null) {
				mHandler.post(new Runnable() {
					@Override
					public void run() {
						adapter.refreshHistory();
						adapter.notifyDataSetChanged();
					}
				});
			} else if (message.getExternalBodyUrl() != null) {
				if (!useLinphoneMessageStorage) {
					byte[] rawImage = LinphoneActivity.instance().getChatStorage().getRawImageFromMessage(id);
					if (rawImage != null) {
						Bitmap bm = BitmapFactory.decodeByteArray(rawImage, 0, rawImage.length);
					}
				}
				mHandler.post(new Runnable() {
					@Override
					public void run() {
						adapter.refreshHistory();
						adapter.notifyDataSetChanged();
					}
				});
			}
			scrollToEnd();
		}
	}

	@Override
	public synchronized void onLinphoneChatMessageStateChanged(LinphoneChatMessage msg, State state) {
		final LinphoneChatMessage finalMessage = msg;
		final String finalImage = finalMessage.getExternalBodyUrl();
		final State finalState=state;
		if (LinphoneActivity.isInstanciated() && state != State.InProgress) {
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					if (finalMessage != null && !finalMessage.equals("")) {
						LinphoneActivity.instance().onMessageStateChanged(sipUri, finalMessage.getText(), finalState.toInt());
					} else if (finalImage != null && !finalImage.equals("")) {
						if (latestImageMessages != null && latestImageMessages.containsValue(finalImage)) {
							int id = -1;
							for (int key : latestImageMessages.keySet()) {
								String object = latestImageMessages.get(key);
								if (object.equals(finalImage)) {
									id = key;
									break;
								}
							}
							if (id != -1) {
								LinphoneActivity.instance().onImageMessageStateChanged(sipUri, id, finalState.toInt());
							}
						}
					}

					if (lastSentMessagesBubbles != null && lastSentMessagesBubbles.size() > 0) {
						for (BubbleChat bubble : lastSentMessagesBubbles) {
							if (bubble.getNativeMessageObject() == finalMessage) {
								bubble.updateStatusView(finalState);
							}
						}
					}
					adapter.notifyDataSetChanged();
				}
				
			});
		}
	}

	public String getSipUri() {
		return sipUri;
	}

	private void pickImage() {
	    final List<Intent> cameraIntents = new ArrayList<Intent>();
	    final Intent captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
	    File file = new File(Environment.getExternalStorageDirectory(), getString(R.string.temp_photo_name));
	    imageToUploadUri = Uri.fromFile(file);
    	captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageToUploadUri);
	    cameraIntents.add(captureIntent);

	    final Intent galleryIntent = new Intent();
	    galleryIntent.setType("image/*");
	    galleryIntent.setAction(Intent.ACTION_GET_CONTENT);

	    final Intent chooserIntent = Intent.createChooser(galleryIntent, getString(R.string.image_picker_title));
	    chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, cameraIntents.toArray(new Parcelable[]{}));

	    startActivityForResult(chooserIntent, ADD_PHOTO);
    }

	public static Bitmap downloadImage(String stringUrl) {
		URL url;
		Bitmap bm = null;
		try {
			url = new URL(stringUrl);
			URLConnection ucon = url.openConnection();
	        InputStream is = ucon.getInputStream();
	        BufferedInputStream bis = new BufferedInputStream(is);

	        ByteArrayBuffer baf = new ByteArrayBuffer(50);
	        int current = 0;
	        while ((current = bis.read()) != -1) {
	                baf.append((byte) current);
	        }

	        byte[] rawImage = baf.toByteArray();
	        bm = BitmapFactory.decodeByteArray(rawImage, 0, rawImage.length);
			bis.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return bm;
	}

	private void saveImage(int id) {
		byte[] rawImage = LinphoneActivity.instance().getChatStorage().getRawImageFromMessage(id);
		Bitmap bm = BitmapFactory.decodeByteArray(rawImage, 0, rawImage.length);
		if (saveImage(bm, id, null) != null) {
			Toast.makeText(getActivity(), getString(R.string.image_saved), Toast.LENGTH_SHORT).show();
		} else {
			Toast.makeText(getActivity(), getString(R.string.image_not_saved), Toast.LENGTH_LONG).show();
		}
	}

	private String saveImage(Bitmap bm, int id, LinphoneChatMessage message) {
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

			if (useLinphoneMessageStorage) {
				//Update url path in liblinphone database
				if (message == null) {
					LinphoneChatMessage[] history = chatRoom.getHistory();
					for (LinphoneChatMessage msg : history) {
						if (msg.getStorageId() == id) {
							message = msg;
							break;
						}
					}
				}
				message.setExternalBodyUrl(path + filename);
				chatRoom.updateUrl(message);
			}

			MediaStore.Images.Media.insertImage(getActivity().getContentResolver(),file.getAbsolutePath(),file.getName(),file.getName());
			return file.getAbsolutePath();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	private long hashBitmap(Bitmap bmp){
		long hash = 31; // Random prime number
		for(int x = 0; x < bmp.getWidth(); x++){
			for (int y = 0; y < bmp.getHeight(); y++){
				hash *= (bmp.getPixel(x,y) + 31);
			}
		}
		return hash;
	}

	private String uploadImage(String filePath, Bitmap file, int compressorQuality, final int imageSize) {
		String fileName;
		if (filePath != null) {
			File sourceFile = new File(filePath);
			fileName = sourceFile.getName();
		} else {
			fileName = getString(R.string.temp_photo_name_with_date).replace("%s", String.valueOf(System.currentTimeMillis()));
		}

		if (getResources().getBoolean(R.bool.hash_images_as_name_before_upload)) {
			fileName = String.valueOf(hashBitmap(file)) + ".jpg";
		}

		String response = null;
		HttpURLConnection conn = null;
		try {
		    String lineEnd = "\r\n";
			String twoHyphens = "--";
			String boundary = "---------------------------14737809831466499882746641449";

            URL url = new URL(uploadServerUri);
            conn = (HttpURLConnection) url.openConnection();
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Connection", "Keep-Alive");
            conn.setRequestProperty("ENCTYPE", "multipart/form-data");
            conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
            conn.setRequestProperty("uploaded_file", fileName);

            ProgressOutputStream pos = new ProgressOutputStream(conn.getOutputStream());
            pos.setListener(new OutputStreamListener() {
				@Override
				public void onBytesWrite(int count) {
					bytesSent += count;
					progressBar.setProgress(bytesSent * 100 / imageSize);
				}
            });
            DataOutputStream dos = new DataOutputStream(pos);

            dos.writeBytes(lineEnd + twoHyphens + boundary + lineEnd);
            dos.writeBytes("Content-Disposition: form-data; name=\"userfile\"; filename=\""+ fileName + "\"" + lineEnd);
            dos.writeBytes("Content-Type: application/octet-stream" + lineEnd);
            dos.writeBytes(lineEnd);

            file.compress(CompressFormat.JPEG, compressorQuality, dos);

            dos.writeBytes(lineEnd);
            dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

            dos.flush();
            dos.close();

            InputStream is = conn.getInputStream();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            int bytesRead;
            byte[] bytes = new byte[1024];
            while((bytesRead = is.read(bytes)) != -1) {
                baos.write(bytes, 0, bytesRead);
            }
            byte[] bytesReceived = baos.toByteArray();
            baos.close();
            is.close();

            response = new String(bytesReceived);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
            if (conn != null) {
                conn.disconnect();
            }
        }

		return response;
	}

	public String getRealPathFromURI(Uri contentUri) {
		String[] proj = { MediaStore.Images.Media.DATA };
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

	private void showPopupMenuAskingImageSize(final String filePath, final Bitmap image) {
		fileToUploadPath = filePath;
		imageToUpload = image;
		try {
			sendImage.showContextMenu();
		} catch (Exception e) { e.printStackTrace(); };
	}

	private void uploadAndSendImage(final String filePath, final Bitmap image, final ImageSize size) {
		uploadLayout.setVisibility(View.VISIBLE);
    	textLayout.setVisibility(View.GONE);

    	uploadThread = new Thread(new Runnable() {
			@Override
			public void run() {
				Bitmap bm = null;
				String url = null;

				if (!uploadThread.isInterrupted()) {
					if (filePath != null) {
		                bm = BitmapFactory.decodeFile(filePath);
		                if (bm != null && size != ImageSize.REAL) {
		                	int pixelsMax = size == ImageSize.SMALL ? SIZE_SMALL : size == ImageSize.MEDIUM ? SIZE_MEDIUM : SIZE_LARGE;
		                    if (bm.getWidth() > bm.getHeight() && bm.getWidth() > pixelsMax) {
		                    	bm = Bitmap.createScaledBitmap(bm, pixelsMax, (pixelsMax * bm.getHeight()) / bm.getWidth(), false);
		                    } else if (bm.getHeight() > bm.getWidth() && bm.getHeight() > pixelsMax) {
		                    	bm = Bitmap.createScaledBitmap(bm, (pixelsMax * bm.getWidth()) / bm.getHeight(), pixelsMax, false);
		                    }
		                }
					} else if (image != null) {
						bm = image;
					}
				}

				// Rotate the bitmap if possible/needed, using EXIF data
				try {
					if (filePath != null) {
						ExifInterface exif = new ExifInterface(filePath);
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

                ByteArrayOutputStream outStream = new ByteArrayOutputStream();
                if (bm != null) {
                	bm.compress(CompressFormat.JPEG, COMPRESSOR_QUALITY, outStream);
                }

                if (!uploadThread.isInterrupted() && bm != null) {
	                url = uploadImage(filePath, bm, COMPRESSOR_QUALITY, outStream.size());
	                File file = new File(Environment.getExternalStorageDirectory(), getString(R.string.temp_photo_name));
	                file.delete();
                }

                if (!uploadThread.isInterrupted()) {
                    final Bitmap fbm = bm;
                    final String furl = url;
	                mHandler.post(new Runnable() {
						@Override
						public void run() {
							uploadLayout.setVisibility(View.GONE);
							textLayout.setVisibility(View.VISIBLE);
							progressBar.setProgress(0);
			            	if (furl != null) {
			            		sendImageMessage(furl, fbm);
			            	} else {
			            		Toast.makeText(getActivity(), getString(R.string.error), Toast.LENGTH_LONG).show();
			            	}
						}
					});
                }
			}
		});
    	uploadThread.start();
	}

	@Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ADD_PHOTO && resultCode == Activity.RESULT_OK) {
        	if (data != null && data.getExtras() != null && data.getExtras().get("data") != null) {
        		Bitmap bm = (Bitmap) data.getExtras().get("data");
        		showPopupMenuAskingImageSize(null, bm);
        	}
        	else if (data != null && data.getData() != null) {
	    		String filePath = getRealPathFromURI(data.getData());
	        	showPopupMenuAskingImageSize(filePath, null);
        	}
        	else if (imageToUploadUri != null) {
        		String filePath = imageToUploadUri.getPath();
        		showPopupMenuAskingImageSize(filePath, null);
        	}
        	else {
        		File file = new File(Environment.getExternalStorageDirectory(), getString(R.string.temp_photo_name));
        		if (file.exists()) {
	        	    imageToUploadUri = Uri.fromFile(file);
	        	    String filePath = imageToUploadUri.getPath();
	        		showPopupMenuAskingImageSize(filePath, null);
        		}
        	}
		} else {
			super.onActivityResult(requestCode, resultCode, data);
		}
    }

	class ProgressOutputStream extends OutputStream {
		OutputStream outputStream;
		private OutputStreamListener listener;

		public ProgressOutputStream(OutputStream stream) {
			outputStream = stream;
		}

		public void setListener(OutputStreamListener listener) {
			this.listener = listener;
		}

		@Override
		public void write(int oneByte) throws IOException {
			outputStream.write(oneByte);
		}

		@Override
		public void write(byte[] buffer, int offset, int count)
				throws IOException {
			listener.onBytesWrite(count);
			outputStream.write(buffer, offset, count);
		}
	}

	interface OutputStreamListener {
		public void onBytesWrite(int count);
	}

	enum ImageSize {
		SMALL,
		MEDIUM,
		LARGE,
		REAL;
	}

	@Override
	public void onComposingReceived(LinphoneChatRoom room) {
		if (chatRoom != null && room != null && chatRoom.getPeerAddress().asStringUriOnly().equals(room.getPeerAddress().asStringUriOnly())) {
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					remoteComposing.setVisibility(chatRoom.isRemoteComposing() ? View.VISIBLE : View.GONE);
				}
			});
		}
	}
}
