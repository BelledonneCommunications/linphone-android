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
import org.linphone.core.LinphoneChatRoom;
import org.linphone.core.LinphoneCore;
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
public class ChatFragment extends Fragment implements OnClickListener, LinphoneOnMessageReceivedListener {
	private LinphoneChatRoom chatRoom;
	private String sipUri;
	private EditText message;
	private RelativeLayout messagesLayout;
	private ScrollView messagesScrollView;
	private int previousMessageID;
	private Handler mHandler = new Handler();
	
	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, 
        Bundle savedInstanceState) {
		sipUri = getArguments().getString("SipUri");
		String name = getArguments().getString("DisplayName");
		String pictureUri = getArguments().getString("PictureUri");
		
        View view = inflater.inflate(R.layout.chat, container, false);
        
        TextView contactName = (TextView) view.findViewById(R.id.contactName);
        if (name == null && getResources().getBoolean(R.bool.only_display_username_if_unknown) && LinphoneUtils.isSipAddress(sipUri)) {
        	contactName.setText(LinphoneUtils.getUsernameFromAddress(sipUri));
		} else if (name == null) {
			contactName.setText(sipUri);
		}
        else {
			contactName.setText(name);
		}
        
        ImageView contactPicture = (ImageView) view.findViewById(R.id.contactPicture);
        if (pictureUri != null) {
        	LinphoneUtils.setImagePictureFromUri(view.getContext(), contactPicture, Uri.parse(pictureUri), R.drawable.unknown_small);
        }
        
        ImageView sendMessage = (ImageView) view.findViewById(R.id.sendMessage);
        sendMessage.setOnClickListener(this);
        message = (EditText) view.findViewById(R.id.message);
        
        messagesLayout = (RelativeLayout) view.findViewById(R.id.messages);
        
        messagesScrollView = (ScrollView) view.findViewById(R.id.chatScrollView);
        messagesScrollView.post(new Runnable() {
            @Override
            public void run() {
            	scrollToEnd();
            }
        });
        
        invalidate();
        
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
        	displayMessage(msg.getId(), msg.getMessage(), msg.getTimestamp(), msg.isIncoming(), messagesLayout);
        	chatStorage.markMessageAsRead(msg.getId());
        }
        LinphoneActivity.instance().updateMissedChatCount();
        
        scrollToEnd();
	}
	
	private void displayMessage(final int id, final String message, final String time, final boolean isIncoming, final RelativeLayout layout) {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				BubbleChat bubble = new BubbleChat(layout.getContext(), id, message, time, isIncoming, previousMessageID);
				previousMessageID = id;
				layout.addView(bubble.getView());
				registerForContextMenu(bubble.getView());
			}
		});
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
		if (chatRoom != null && message != null) {
			String messageToSend = message.getText().toString();
			message.setText("");
			
			chatRoom.sendMessage(messageToSend);
			
			if (LinphoneActivity.isInstanciated()) {
				LinphoneActivity.instance().onMessageSent(sipUri, messageToSend);
			}
			
			displayMessage(previousMessageID + 1, messageToSend, getString(R.string.now_date_format), false, messagesLayout);
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
			displayMessage(previousMessageID + 1, message, getString(R.string.now_date_format), true, messagesLayout);
			scrollToEnd();
		}
	}
}
