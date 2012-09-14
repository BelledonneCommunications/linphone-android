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
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

import org.apache.http.util.ByteArrayBuffer;
import org.linphone.LinphoneSimpleListener.LinphoneOnMessageReceivedListener;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneChatMessage;
import org.linphone.core.LinphoneChatMessage.State;
import org.linphone.core.LinphoneChatRoom;
import org.linphone.core.LinphoneCore;
import org.linphone.core.Log;
import org.linphone.ui.AvatarWithShadow;
import org.linphone.ui.BubbleChat;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v4.content.CursorLoader;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * @author Sylvain Berfini
 */
public class ChatFragment extends Fragment implements OnClickListener, LinphoneOnMessageReceivedListener, LinphoneChatMessage.StateListener {
	private static final int ADD_PHOTO = 0;
	private LinphoneChatRoom chatRoom;
	private View view;
	private String sipUri;
	private EditText message;
	private TextView contactName;
	private AvatarWithShadow contactPicture;
	private RelativeLayout messagesLayout;
	private ScrollView messagesScrollView;
	private int previousMessageID;
	private Handler mHandler = new Handler();
	private BubbleChat lastSentMessageBubble;
	private ProgressDialog mProgressDialog;
	
	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, 
        Bundle savedInstanceState) {
		sipUri = getArguments().getString("SipUri");
		String displayName = getArguments().getString("DisplayName");
		String pictureUri = getArguments().getString("PictureUri");
		
        view = inflater.inflate(R.layout.chat, container, false);
        
        contactName = (TextView) view.findViewById(R.id.contactName);
        contactPicture = (AvatarWithShadow) view.findViewById(R.id.contactPicture);
        contactPicture.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				pickImage(); //FIXME: use a specific button instead
			}
		});
        
        ImageView sendMessage = (ImageView) view.findViewById(R.id.sendMessage);
        sendMessage.setOnClickListener(this);
        message = (EditText) view.findViewById(R.id.message);
        
        messagesLayout = (RelativeLayout) view.findViewById(R.id.messages);
        messagesScrollView = (ScrollView) view.findViewById(R.id.chatScrollView);
        
        displayChat(displayName, pictureUri);
        
        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null)
			chatRoom = lc.createChatRoom(sipUri);

		return view;
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

	public void changeDisplayedChat(String sipUri, String displayName, String pictureUri) {
		this.sipUri = sipUri;
		displayChat(displayName, pictureUri);
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		menu.add(0, v.getId(), 0, getString(R.string.delete));
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		LinphoneActivity.instance().getChatStorage().deleteMessage(item.getItemId());
		invalidate();
		return true;
	}
	
	@Override
	public void onResume() {
		super.onResume();
		if (LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().selectMenu(FragmentsAvailable.CHAT);
			LinphoneActivity.instance().updateChatFragment(this);
		}
		scrollToEnd();
	}

	@Override
	public void onClick(View v) {
		sendTextMessage();
	}
	
	private void sendTextMessage() {
		if (chatRoom != null && message != null && message.getText().length() > 0) {
			String messageToSend = message.getText().toString();
			message.setText("");

			LinphoneChatMessage chatMessage = chatRoom.createLinphoneChatMessage(messageToSend);
			chatRoom.sendMessage(chatMessage, this);
			
			if (LinphoneActivity.isInstanciated()) {
				LinphoneActivity.instance().onMessageSent(sipUri, messageToSend);
			}
			
			displayMessage(previousMessageID + 2, messageToSend, String.valueOf(System.currentTimeMillis()), false, State.InProgress, messagesLayout);
			scrollToEnd();
		}
	}
	
	private void sendImageMessage(String url, Bitmap bitmap) {
		if (chatRoom != null && url != null && url.length() > 0) {
			LinphoneChatMessage chatMessage = chatRoom.createLinphoneChatMessage("");
			chatMessage.setExternalBodyUrl(url);
			chatRoom.sendMessage(chatMessage, this);
			
			if (LinphoneActivity.isInstanciated()) {
				LinphoneActivity.instance().onMessageSent(sipUri, bitmap);
			}
		}
		displayImageMessage(previousMessageID + 2, bitmap, String.valueOf(System.currentTimeMillis()), false, State.InProgress, messagesLayout);
		scrollToEnd();
	}
	
	private void scrollToEnd() {
		mHandler.postDelayed(new Runnable() {
			@Override
			public void run() {
				messagesScrollView.fullScroll(View.FOCUS_DOWN);
			}
		}, 100);
	}

	@Override
	public void onMessageReceived(LinphoneAddress from, LinphoneChatMessage message) {
		if (from.asStringUriOnly().equals(sipUri))  {
			int id = previousMessageID + 2;
			if (message.getMessage() != null) {
				displayMessage(id, message.getMessage(), String.valueOf(System.currentTimeMillis()), true, null, messagesLayout);
			} else if (message.getExternalBodyUrl() != null) {
				Bitmap bm = downloadImage(message.getExternalBodyUrl());
				displayImageMessage(id, bm, String.valueOf(System.currentTimeMillis()), true, null, messagesLayout);
			}
			scrollToEnd();
		}
	}

	@Override
	public void onLinphoneChatMessageStateChanged(LinphoneChatMessage msg, State state) {
		final String finalMessage = msg.getMessage();
		final Bitmap finalImage = downloadImage(msg.getMessage());
		final State finalState = state;
		if (LinphoneActivity.isInstanciated() && state != State.InProgress) {
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					if (finalMessage != null) {
						LinphoneActivity.instance().onMessageStateChanged(sipUri, finalMessage, finalState.toInt());
					} else if (finalImage != null) {
						LinphoneActivity.instance().onMessageStateChanged(sipUri, finalImage, finalState.toInt());
					}
					lastSentMessageBubble.updateStatusView(finalState);
				}
			});
		}
	}
	
	public String getSipUri() {
		return sipUri;
	}
	
	private void pickImage() {
		//TODO allow user to take a photo or to choose an existing one
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, ADD_PHOTO);
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
	
	private String uploadImage(String filePath, Bitmap file) {
		String upLoadServerUri = getActivity().getResources().getString(R.string.upload_url);
		
		File sourceFile = new File(filePath); 
		if (!sourceFile.isFile()) {
			Log.e("Can't read source file " + filePath + ", upload failed");
			return null;
		}
		
		String response = null;
		HttpURLConnection conn = null;
		try {		    
		    String lineEnd = "\r\n";
			String twoHyphens = "--";
			String boundary = "---------------------------14737809831466499882746641449";
			
            FileInputStream fileInputStream = new FileInputStream(sourceFile);
            URL url = new URL(upLoadServerUri);
            conn = (HttpURLConnection) url.openConnection();
            conn.setDoInput(true); 
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Connection", "Keep-Alive");
            conn.setRequestProperty("ENCTYPE", "multipart/form-data");
            conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
            conn.setRequestProperty("uploaded_file", sourceFile.getName()); 
            DataOutputStream dos = new DataOutputStream(conn.getOutputStream());
  
            dos.writeBytes(lineEnd + twoHyphens + boundary + lineEnd); 
            dos.writeBytes("Content-Disposition: form-data; name=\"userfile\"; filename=\""+ sourceFile.getName() + "\"" + lineEnd);
            dos.writeBytes("Content-Type: application/octet-stream" + lineEnd);
            dos.writeBytes(lineEnd);
  
            file.compress(CompressFormat.JPEG, 75, dos);
  
            dos.writeBytes(lineEnd);
            dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

            fileInputStream.close();
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
	    int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
	    cursor.moveToFirst();
	    return cursor.getString(column_index);
    }
	
	@Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ADD_PHOTO && resultCode == Activity.RESULT_OK) {
        		final String filePath = getRealPathFromURI(data.getData());
            	mProgressDialog = ProgressDialog.show(getActivity(), "Please wait", "Long operation starts...", true);
            	
            	new Thread(new Runnable() {
					@Override
					public void run() {
		                Bitmap bm = BitmapFactory.decodeFile(filePath);
		                final String url = uploadImage(filePath, bm);
		                
		                ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		                bm.compress(CompressFormat.JPEG, 75, outStream);
		                bm.recycle();
		                byte[] bitmap = outStream.toByteArray();
		                final Bitmap fbm = BitmapFactory.decodeByteArray(bitmap, 0, bitmap.length);
		                
		                mHandler.post(new Runnable() {
							@Override
							public void run() {
								mProgressDialog.dismiss();		
				                sendImageMessage(url, fbm);						
							}
						});
					}
				}).start();
		}
        super.onActivityResult(requestCode, resultCode, data);
    }
}
