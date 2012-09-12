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
import java.util.List;

import org.linphone.LinphoneSimpleListener.LinphoneOnMessageReceivedListener;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneChatMessage;
import org.linphone.core.LinphoneChatMessage.State;
import org.linphone.core.LinphoneChatRoom;
import org.linphone.core.LinphoneCore;
import org.linphone.ui.AvatarWithShadow;
import org.linphone.ui.BubbleChat;

import android.net.Uri;
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
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * @author Sylvain Berfini
 */
public class ChatFragment extends Fragment implements OnClickListener, LinphoneOnMessageReceivedListener, LinphoneChatMessage.StateListener {
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
	
	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, 
        Bundle savedInstanceState) {
		sipUri = getArguments().getString("SipUri");
		String displayName = getArguments().getString("DisplayName");
		String pictureUri = getArguments().getString("PictureUri");
		
        view = inflater.inflate(R.layout.chat, container, false);
        
        contactName = (TextView) view.findViewById(R.id.contactName);
        contactPicture = (AvatarWithShadow) view.findViewById(R.id.contactPicture);
        
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
        	displayMessage(msg.getId(), msg.getMessage(), msg.getTimestamp(), msg.isIncoming(), msg.getStatus(), messagesLayout);
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
				BubbleChat bubble = new BubbleChat(layout.getContext(), id, message, time, isIncoming, status, previousMessageID);
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
	
	private void scrollToEnd() {
		mHandler.postDelayed(new Runnable() {
			@Override
			public void run() {
				messagesScrollView.fullScroll(View.FOCUS_DOWN);
			}
		}, 100);
	}

	@Override
	public void onMessageReceived(LinphoneAddress from, String message) {
		if (from.asStringUriOnly().equals(sipUri))  {
			int id = previousMessageID + 2;
			displayMessage(id, message, String.valueOf(System.currentTimeMillis()), true, null, messagesLayout);
			scrollToEnd();
		}
	}

	@Override
	public void onLinphoneChatMessageStateChanged(LinphoneChatMessage msg, State state) {
		final String finalMessage = msg.getMessage();
		final State finalState = state;
		if (LinphoneActivity.isInstanciated() && state != State.InProgress) {
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					LinphoneActivity.instance().onMessageStateChanged(sipUri, finalMessage, finalState.toInt());
					lastSentMessageBubble.updateStatusView(finalState);
				}
			});
		}
	}
	
	public String getSipUri() {
		return sipUri;
	}
}
