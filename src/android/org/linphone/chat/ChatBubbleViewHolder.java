/*
ChatBubbleViewHolder.java
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

package org.linphone.chat;

import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.linphone.R;
import org.linphone.core.Buffer;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatMessageListener;
import org.linphone.core.Content;

public class ChatBubbleViewHolder implements ChatMessageListener {
    public int id;

    public LinearLayout eventLayout;
    public TextView eventTime;
    public TextView eventMessage;

    public RelativeLayout bubbleLayout;
    public CheckBox delete;
    public LinearLayout background;
    public ImageView contactPicture;
    public TextView contactName;
    public TextView messageText;
    public ImageView messageImage;
    public RelativeLayout fileTransferLayout;
    public ProgressBar fileTransferProgressBar;
    public Button fileTransferAction;
    public ImageView messageStatus;
    public ProgressBar messageSendingInProgress;
    public ImageView contactPictureMask;
    public LinearLayout imdmLayout;
    public ImageView imdmIcon;
    public TextView imdmLabel;
    public TextView fileExtensionLabel;
    public TextView fileNameLabel;

    public ChatBubbleViewHolder(View view) {
        id = view.getId();

        eventLayout = view.findViewById(R.id.event);
        eventTime = view.findViewById(R.id.event_date);
        eventMessage = view.findViewById(R.id.event_text);

        bubbleLayout = (RelativeLayout) view.findViewById(R.id.bubble);
        delete = (CheckBox) view.findViewById(R.id.delete_message);
        background = (LinearLayout) view.findViewById(R.id.background);
        contactPicture = (ImageView) view.findViewById(R.id.contact_picture);
        contactName = (TextView) view.findViewById(R.id.contact_header);
        messageText = (TextView) view.findViewById(R.id.message);
        messageImage = (ImageView) view.findViewById(R.id.image);
        fileTransferLayout = (RelativeLayout) view.findViewById(R.id.file_transfer_layout);
        fileTransferProgressBar = (ProgressBar) view.findViewById(R.id.progress_bar);
        fileTransferAction = (Button) view.findViewById(R.id.file_transfer_action);
        messageStatus = (ImageView) view.findViewById(R.id.status);
        messageSendingInProgress = (ProgressBar) view.findViewById(R.id.inprogress);
        contactPictureMask = (ImageView) view.findViewById(R.id.mask);
        imdmLayout = (LinearLayout) view.findViewById(R.id.imdmLayout);
        imdmIcon = (ImageView) view.findViewById(R.id.imdmIcon);
        imdmLabel = (TextView) view.findViewById(R.id.imdmText);
        fileExtensionLabel = (TextView) view.findViewById(R.id.file_extension);
        fileNameLabel = (TextView) view.findViewById(R.id.file_name);
    }

    @Override
    public void onMsgStateChanged(ChatMessage msg, ChatMessage.State state) {

    }

    @Override
    public void onFileTransferRecv(ChatMessage msg, Content content, Buffer buffer) {

    }

    @Override
    public Buffer onFileTransferSend(ChatMessage message, Content content, int offset, int size) {
        return null;
    }

    @Override
    public void onFileTransferProgressIndication(ChatMessage msg, Content content, int offset, int total) {
        if (msg.getStorageId() == id) fileTransferProgressBar.setProgress(offset * 100 / total);
    }
}