package org.linphone.chat;

/*
ChatMessageViewHolder.java
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

import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import org.linphone.R;

public class ChatMessageOldViewHolder extends RecyclerView.ViewHolder
        implements View.OnClickListener {
    public final LinearLayout eventLayout;
    public final TextView eventMessage;

    public final RelativeLayout bubbleLayout;
    public final LinearLayout separatorLayout;
    public final LinearLayout background;
    public final RelativeLayout avatarLayout;
    public final TextView contactName;

    public final ImageView messageStatus;
    public final ProgressBar messageSendingInProgress;
    public final LinearLayout imdmLayout;
    public final ImageView imdmIcon;
    public final TextView imdmLabel;

    public final TextView messageText;
    public final ImageView messageImage;

    public final RelativeLayout fileTransferLayout;
    public final ProgressBar fileTransferProgressBar;
    public final Button fileTransferAction;

    public final TextView fileName;
    public final Button openFileButton;

    public final CheckBox delete;

    private ChatMessageViewHolderClickListener mListener;

    public ChatMessageOldViewHolder(View view, ChatMessageViewHolderClickListener listener) {
        this(view);
        mListener = listener;
        view.setOnClickListener(this);
    }

    public ChatMessageOldViewHolder(View view) {
        super(view);
        eventLayout = view.findViewById(R.id.event);
        // eventTime = view.findViewById(R.id.event_date);
        eventMessage = view.findViewById(R.id.event_text);

        bubbleLayout = view.findViewById(R.id.bubble);
        background = view.findViewById(R.id.background);
        avatarLayout = view.findViewById(R.id.avatar_layout);
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
}
