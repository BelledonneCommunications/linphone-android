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
import org.linphone.compatibility.Compatibility;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneChatMessage;
import org.linphone.core.LinphoneChatMessage.State;
import org.linphone.core.LinphoneChatRoom;
import org.linphone.core.LinphoneCore;
import org.linphone.ui.AvatarWithShadow;
import org.linphone.ui.BubbleChat;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.preference.PreferenceManager;
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
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * @author Sylvain Berfini
 */
public class ChatFragment extends Fragment implements OnClickListener, LinphoneChatMessage.StateListener {
	private static final int ADD_PHOTO = 1337;
	private static final int MENU_DELETE_MESSAGE = 0;
	private static final int MENU_SAVE_PICTURE = 1;
	private static final int MENU_PICTURE_SMALL = 2;
	private static final int MENU_PICTURE_MEDIUM = 3;
	private static final int MENU_PICTURE_LARGE = 4;
	private static final int MENU_PICTURE_REAL = 5;
	private static final int MENU_COPY_TEXT = 6;
	private static final int COMPRESSOR_QUALITY = 100;
	private static final int SIZE_SMALL = 500;
	private static final int SIZE_MEDIUM = 1000;
	private static final int SIZE_LARGE = 1500;
	
	private LinphoneChatRoom chatRoom;
	private View view;
	private String sipUri;
	private EditText message;
	private ImageView cancelUpload;
	private TextView sendImage, sendMessage, contactName;
	private AvatarWithShadow contactPicture;
	private RelativeLayout messagesLayout, uploadLayout, textLayout;
	private ScrollView messagesScrollView;
	private int previousMessageID;
	private Handler mHandler = new Handler();
	private BubbleChat lastSentMessageBubble;
	private HashMap<Integer, String> latestImageMessages;
	
	private ProgressBar progressBar;
	private int bytesSent;
	private String uploadServerUri;
	private String fileToUploadPath;
	private Bitmap imageToUpload;
	private Uri imageToUploadUri;
	private Thread uploadThread;
	private TextWatcher textWatcher;
	private OnGlobalLayoutListener keyboardListener;
	
	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		sipUri = getArguments().getString("SipUri");
		String displayName = getArguments().getString("DisplayName");
		String pictureUri = getArguments().getString("PictureUri");
		
        view = inflater.inflate(R.layout.chat, container, false);
        
        contactName = (TextView) view.findViewById(R.id.contactName);
        contactPicture = (AvatarWithShadow) view.findViewById(R.id.contactPicture);
        
        sendMessage = (TextView) view.findViewById(R.id.sendMessage);
        sendMessage.setOnClickListener(this);
        
        message = (EditText) view.findViewById(R.id.message);
        if (!getActivity().getResources().getBoolean(R.bool.allow_chat_multiline)) {
        	message.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE);
        	message.setMaxLines(1);
        }
        
        uploadLayout = (RelativeLayout) view.findViewById(R.id.uploadLayout);
        textLayout = (RelativeLayout) view.findViewById(R.id.messageLayout);
        
        messagesLayout = (RelativeLayout) view.findViewById(R.id.messages);
        messagesScrollView = (ScrollView) view.findViewById(R.id.chatScrollView);
        progressBar = (ProgressBar) view.findViewById(R.id.progressbar);
        
        sendImage = (TextView) view.findViewById(R.id.sendPicture);
        registerForContextMenu(sendImage);
        sendImage.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				pickImage();
			}
		});
        
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
        
        displayChat(displayName, pictureUri);
        
        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			chatRoom = lc.createChatRoom(sipUri);
		}
		
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(LinphoneActivity.instance());
		uploadServerUri = prefs.getString(getString(R.string.pref_image_sharing_server_key), getString(R.string.pref_image_sharing_server_default));
		
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
		scrollToEnd();
	}
	
	private void invalidate() {
		messagesLayout.removeAllViews();		
		List<ChatMessage> messagesList = LinphoneActivity.instance().getChatMessages(sipUri);
		
		previousMessageID = -1;
		ChatStorage chatStorage = LinphoneActivity.instance().getChatStorage();
        for (ChatMessage msg : messagesList) {
        	if (msg.getMessage() != null) {
        		displayMessage(msg.getId(), msg.getMessage(), msg.getTimestamp(), msg.isIncoming(), msg.getStatus(), messagesLayout);
        	} else {
        		displayImageMessage(msg.getId(), msg.getImage(), msg.getTimestamp(), msg.isIncoming(), msg.getStatus(), messagesLayout);
        	}
        	chatStorage.markMessageAsRead(msg.getId());
        }
        LinphoneActivity.instance().updateMissedChatCount();
        
        scrollToEnd();
	}
	
	private void displayChat(String displayName, String pictureUri) {
		if (displayName == null && getResources().getBoolean(R.bool.only_display_username_if_unknown) && LinphoneUtils.isSipAddress(sipUri)) {
        	contactName.setText(LinphoneUtils.getUsernameFromAddress(sipUri));
		} else if (displayName == null) {
			contactName.setText(sipUri);
		}
        else {
			contactName.setText(displayName);
		}
		
        if (pictureUri != null) {
        	LinphoneUtils.setImagePictureFromUri(view.getContext(), contactPicture.getView(), Uri.parse(pictureUri), R.drawable.unknown_small);
        } else {
        	contactPicture.setImageResource(R.drawable.unknown_small);
        }
        
        messagesScrollView.post(new Runnable() {
            @Override
            public void run() {
            	scrollToEnd();
            }
        });
        
        invalidate();
	}
	
	private void displayMessage(final int id, final String message, final String time, final boolean isIncoming, final LinphoneChatMessage.State status, final RelativeLayout layout) {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				BubbleChat bubble = new BubbleChat(layout.getContext(), id, message, null, time, isIncoming, status, previousMessageID);
				if (!isIncoming) {
					lastSentMessageBubble = bubble;
				}
				previousMessageID = id;
				layout.addView(bubble.getView());
				registerForContextMenu(bubble.getView());
			}
		});
	}
	
	private void displayImageMessage(final int id, final Bitmap image, final String time, final boolean isIncoming, final LinphoneChatMessage.State status, final RelativeLayout layout) {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				BubbleChat bubble = new BubbleChat(layout.getContext(), id, null, image, time, isIncoming, status, previousMessageID);
				if (!isIncoming) {
					lastSentMessageBubble = bubble;
				}
				previousMessageID = id;
				layout.addView(bubble.getView());
				registerForContextMenu(bubble.getView());
			}
		});
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
		displayChat(displayName, pictureUri);
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
				menu.add(v.getId(), MENU_SAVE_PICTURE, 0, getString(R.string.save_picture));
			} else {
				menu.add(v.getId(), MENU_COPY_TEXT, 0, getString(R.string.copy_text));
			}
		}
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case MENU_DELETE_MESSAGE:
			LinphoneActivity.instance().getChatStorage().deleteMessage(item.getGroupId());
			invalidate();
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
		}
		return true;
	}
	
	@Override
	public void onPause() {
		latestImageMessages = null;
		message.removeTextChangedListener(textWatcher);
		removeVirtualKeyboardVisiblityListener();

		LinphoneService.instance().removeMessageNotification();
		
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
		
		super.onResume();

		if (LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().selectMenu(FragmentsAvailable.CHAT);
			LinphoneActivity.instance().updateChatFragment(this);
		}
		scrollToEnd();
		
		if (LinphoneActivity.isInstanciated()) {
			String draft = LinphoneActivity.instance().getChatStorage().getDraft(sipUri);
			message.setText(draft);
		}
	}

	@Override
	public void onClick(View v) {
		sendTextMessage();
	}
	
	private void sendTextMessage() {
		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		boolean isNetworkReachable = lc == null ? false : lc.isNetworkReachable();
		
		if (chatRoom != null && message != null && message.getText().length() > 0 && isNetworkReachable) {
			String messageToSend = message.getText().toString();
			message.setText("");

			LinphoneChatMessage chatMessage = chatRoom.createLinphoneChatMessage(messageToSend);
			chatRoom.sendMessage(chatMessage, this);
			
			int newId = -1;
			if (LinphoneActivity.isInstanciated()) {
				newId = LinphoneActivity.instance().onMessageSent(sipUri, messageToSend);
			}
			
			displayMessage(newId, messageToSend, String.valueOf(System.currentTimeMillis()), false, State.InProgress, messagesLayout);
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
			latestImageMessages.put(newId, url);
			
			displayImageMessage(newId, bitmap, String.valueOf(System.currentTimeMillis()), false, State.InProgress, messagesLayout);
			scrollToEnd();
		} else if (!isNetworkReachable && LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().displayCustomToast(getString(R.string.error_network_unreachable), Toast.LENGTH_LONG);
		}
	}
	
	private void scrollToEnd() {
		mHandler.postDelayed(new Runnable() {
			@Override
			public void run() {
				messagesScrollView.fullScroll(View.FOCUS_DOWN);
			}
		}, 100);
	}
	
	private void copyTextMessageToClipboard(int id) {
		String msg = LinphoneActivity.instance().getChatStorage().getTextMessageForId(id);
		if (msg != null) {
			Compatibility.copyTextToClipboard(getActivity(), msg);
			LinphoneActivity.instance().displayCustomToast(getString(R.string.text_copied_to_clipboard), Toast.LENGTH_SHORT);
		}
	}

	public void onMessageReceived(int id, LinphoneAddress from, LinphoneChatMessage message) {
		if (from.asStringUriOnly().equals(sipUri))  {
			if (message.getText() != null) {
				displayMessage(id, message.getText(), String.valueOf(System.currentTimeMillis()), true, null, messagesLayout);
			} else if (message.getExternalBodyUrl() != null) {
				byte[] rawImage = LinphoneActivity.instance().getChatStorage().getRawImageFromMessage(id);
				Bitmap bm = BitmapFactory.decodeByteArray(rawImage, 0, rawImage.length);
				displayImageMessage(id, bm, String.valueOf(System.currentTimeMillis()), true, null, messagesLayout);
			}
			scrollToEnd();
		}
	}

	@Override
	public void onLinphoneChatMessageStateChanged(LinphoneChatMessage msg, State state) {
		final String finalMessage = msg.getText();
		final String finalImage = msg.getExternalBodyUrl();
		final State finalState = state;
		if (LinphoneActivity.isInstanciated() && state != State.InProgress) {
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					if (finalMessage != null && !finalMessage.equals("")) {
						LinphoneActivity.instance().onMessageStateChanged(sipUri, finalMessage, finalState.toInt());
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
					if (lastSentMessageBubble != null) {
						lastSentMessageBubble.updateStatusView(finalState);
					}
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
		try {
			byte[] rawImage = LinphoneActivity.instance().getChatStorage().getRawImageFromMessage(id);
			Bitmap bm = BitmapFactory.decodeByteArray(rawImage, 0, rawImage.length);
			
			String path = Environment.getExternalStorageDirectory().toString();
			OutputStream fOut = null;
			File file = new File(path, getString(R.string.picture_name_format).replace("%s", String.valueOf(id)));
			fOut = new FileOutputStream(file);

			bm.compress(Bitmap.CompressFormat.JPEG, 100, fOut);
			fOut.flush();
			fOut.close();

			Toast.makeText(getActivity(), getString(R.string.image_saved), Toast.LENGTH_SHORT).show();
			MediaStore.Images.Media.insertImage(getActivity().getContentResolver(),file.getAbsolutePath(),file.getName(),file.getName());
		} catch (Exception e) {
			Toast.makeText(getActivity(), getString(R.string.image_not_saved), Toast.LENGTH_LONG).show();
			e.printStackTrace();
		}
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
}
