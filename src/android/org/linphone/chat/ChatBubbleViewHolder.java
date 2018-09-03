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

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.linphone.R;
import org.linphone.core.ChatMessage;

public class ChatBubbleViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
    public String messageId;
    public Context mContext;
    public ChatMessage message;
    public LinearLayout eventLayout;
    public TextView eventMessage;

    public RelativeLayout bubbleLayout;
    public LinearLayout separatorLayout;
    public LinearLayout background;
    public ImageView contactPicture;
    public ImageView contactPictureMask;
    public TextView contactName;

    public ImageView messageStatus;
    public ProgressBar messageSendingInProgress;
    public LinearLayout imdmLayout;
    public ImageView imdmIcon;
    public TextView imdmLabel;

    public TextView messageText;
    public ImageView messageImage;

    public RelativeLayout fileTransferLayout;
    public ProgressBar fileTransferProgressBar;
    public Button fileTransferAction;

    public TextView fileName;
    public Button openFileButton;

    public CheckBox delete;
    private ClickListener mListener;

    public ChatBubbleViewHolder(Context context, View view, ClickListener listener) {
        super(view);
        mContext = context;

        eventLayout = view.findViewById(R.id.event);
        //eventTime = view.findViewById(R.id.event_date);
        eventMessage = view.findViewById(R.id.event_text);

        bubbleLayout = view.findViewById(R.id.bubble);
        background = view.findViewById(R.id.background);
        contactPicture = view.findViewById(R.id.contact_picture);
        contactPictureMask = view.findViewById(R.id.mask);
        contactName = view.findViewById(R.id.contact_header);

        messageStatus = view.findViewById(R.id.status);
        messageSendingInProgress = view.findViewById(R.id.inprogress);
        imdmLayout = view.findViewById(R.id.imdmLayout);
        imdmIcon = view.findViewById(R.id.imdmIcon);
        imdmLabel = view.findViewById(R.id.imdmText);

        messageText = view.findViewById(R.id.message);
        messageImage = view.findViewById(R.id.image);
        separatorLayout = view.findViewById(R.id.separator);

        fileTransferLayout = view.findViewById(R.id.file_transfer_layout);
        fileTransferProgressBar = view.findViewById(R.id.progress_bar);
        fileTransferAction = view.findViewById(R.id.file_transfer_action);

        fileName = view.findViewById(R.id.file_name);
        openFileButton = view.findViewById(R.id.open_file);

        delete = view.findViewById(R.id.delete_message);

        mListener = listener;

        view.setOnClickListener(this);
    }

    public ChatBubbleViewHolder(View view) {
        super(view);
        eventLayout = view.findViewById(R.id.event);
        //eventTime = view.findViewById(R.id.event_date);
        eventMessage = view.findViewById(R.id.event_text);

        bubbleLayout = view.findViewById(R.id.bubble);
        background = view.findViewById(R.id.background);
        contactPicture = view.findViewById(R.id.contact_picture);
        contactPictureMask = view.findViewById(R.id.mask);
        contactName = view.findViewById(R.id.contact_header);

        messageStatus = view.findViewById(R.id.status);
        messageSendingInProgress = view.findViewById(R.id.inprogress);
        imdmLayout = view.findViewById(R.id.imdmLayout);
        imdmIcon = view.findViewById(R.id.imdmIcon);
        imdmLabel = view.findViewById(R.id.imdmText);

        messageText = view.findViewById(R.id.message);
        messageImage = view.findViewById(R.id.image);
        separatorLayout = view.findViewById(R.id.separator);

        fileTransferLayout = view.findViewById(R.id.file_transfer_layout);
        fileTransferProgressBar = view.findViewById(R.id.progress_bar);
        fileTransferAction = view.findViewById(R.id.file_transfer_action);

        fileName = view.findViewById(R.id.file_name);
        openFileButton = view.findViewById(R.id.open_file);

        delete = view.findViewById(R.id.delete_message);
    }

    @Override
    public void onClick(View v) {
        if (mListener != null) {
            mListener.onItemClicked(getAdapterPosition());
        }
    }

    public interface ClickListener {
        void onItemClicked(int position);
    }
}