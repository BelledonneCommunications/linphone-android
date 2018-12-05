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

import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.flexbox.FlexboxLayout;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.linphone.LinphoneActivity;
import org.linphone.R;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.ChatMessage;
import org.linphone.core.Content;
import org.linphone.mediastream.Log;
import org.linphone.utils.FileUtils;
import org.linphone.utils.LinphoneUtils;
import org.linphone.views.AsyncBitmap;
import org.linphone.views.BitmapWorkerTask;
import org.linphone.views.ContactAvatar;

public class ChatMessageViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
    public ChatMessage message;

    public LinearLayout eventLayout;
    public TextView eventMessage;

    public LinearLayout securityEventLayout;
    public TextView securityEventMessage;

    public View rightAnchor;
    public RelativeLayout bubbleLayout;
    public LinearLayout background;
    public RelativeLayout avatarLayout;

    public ProgressBar sendInProgress;
    public TextView timeText;
    public ImageView outgoingImdn;
    public TextView messageText;

    public FlexboxLayout pictures;

    public CheckBox deleteEvent;
    public CheckBox deleteMessage;

    private Context mContext;
    private ChatMessageViewHolderClickListener mListener;

    public ChatMessageViewHolder(
            Context context, View view, ChatMessageViewHolderClickListener listener) {
        this(view);
        mContext = context;
        mListener = listener;
        view.setOnClickListener(this);
    }

    public ChatMessageViewHolder(View view) {
        super(view);
        eventLayout = view.findViewById(R.id.event);
        eventMessage = view.findViewById(R.id.event_text);

        securityEventLayout = view.findViewById(R.id.security_event);
        securityEventMessage = view.findViewById(R.id.security_event_text);

        rightAnchor = view.findViewById(R.id.rightAnchor);
        bubbleLayout = view.findViewById(R.id.bubble);
        background = view.findViewById(R.id.background);
        avatarLayout = view.findViewById(R.id.avatar_layout);

        sendInProgress = view.findViewById(R.id.send_in_progress);
        timeText = view.findViewById(R.id.time);
        outgoingImdn = view.findViewById(R.id.imdn);
        messageText = view.findViewById(R.id.message);

        pictures = view.findViewById(R.id.pictures);

        deleteEvent = view.findViewById(R.id.delete_event);
        deleteMessage = view.findViewById(R.id.delete_message);
    }

    @Override
    public void onClick(View v) {
        if (mListener != null) {
            mListener.onItemClicked(getAdapterPosition());
        }
    }

    public void bindMessage(final ChatMessage message, LinphoneContact contact) {
        eventLayout.setVisibility(View.GONE);
        securityEventLayout.setVisibility(View.GONE);
        rightAnchor.setVisibility(View.VISIBLE);
        bubbleLayout.setVisibility(View.VISIBLE);
        messageText.setVisibility(View.GONE);
        timeText.setVisibility(View.VISIBLE);
        outgoingImdn.setVisibility(View.GONE);
        avatarLayout.setVisibility(View.GONE);
        sendInProgress.setVisibility(View.GONE);
        pictures.setVisibility(View.GONE);
        pictures.removeAllViews();

        ChatMessage.State status = message.getState();
        Address remoteSender = message.getFromAddress();
        String displayName;
        String time =
                LinphoneUtils.timestampToHumanDate(
                        mContext, message.getTime(), R.string.messages_date_format);

        if (message.isOutgoing()) {
            outgoingImdn.setVisibility(View.INVISIBLE); // For anchoring purposes

            if (status == ChatMessage.State.DeliveredToUser) {
                outgoingImdn.setVisibility(View.VISIBLE);
                outgoingImdn.setImageResource(R.drawable.imdn_received);
            } else if (status == ChatMessage.State.Displayed) {
                outgoingImdn.setVisibility(View.VISIBLE);
                outgoingImdn.setImageResource(R.drawable.imdn_read);
            } else if (status == ChatMessage.State.NotDelivered) {
                outgoingImdn.setVisibility(View.VISIBLE);
                outgoingImdn.setImageResource(R.drawable.imdn_error);
            } else if (status == ChatMessage.State.FileTransferError) {
                outgoingImdn.setVisibility(View.VISIBLE);
                outgoingImdn.setImageResource(R.drawable.imdn_error);
            } else if (status == ChatMessage.State.InProgress) {
                sendInProgress.setVisibility(View.VISIBLE);
            }

            timeText.setVisibility(View.VISIBLE);
            background.setBackgroundResource(R.drawable.chat_bubble_outgoing_full);
        } else {
            rightAnchor.setVisibility(View.GONE);
            avatarLayout.setVisibility(View.VISIBLE);
            background.setBackgroundResource(R.drawable.chat_bubble_incoming_full);
        }

        if (contact == null) {
            contact = ContactsManager.getInstance().findContactFromAddress(remoteSender);
        }
        if (contact != null) {
            if (contact.getFullName() != null) {
                displayName = contact.getFullName();
            } else {
                displayName = LinphoneUtils.getAddressDisplayName(remoteSender);
            }
            ContactAvatar.displayAvatar(contact, avatarLayout);
        } else {
            displayName = LinphoneUtils.getAddressDisplayName(remoteSender);
            ContactAvatar.displayAvatar(displayName, avatarLayout);
        }

        if (message.isOutgoing()) {
            timeText.setText(time);
        } else {
            timeText.setText(time + " - " + displayName);
        }

        if (message.hasTextContent()) {
            String msg = message.getTextContent();
            Spanned text = LinphoneUtils.getTextWithHttpLinks(msg);
            messageText.setText(text);
            messageText.setMovementMethod(LinkMovementMethod.getInstance());
            messageText.setVisibility(View.VISIBLE);
        }

        List<Content> fileContents = new ArrayList<>();
        for (Content c : message.getContents()) {
            if (c.isFile() || c.isFileTransfer()) {
                fileContents.add(c);
            }
        }

        if (fileContents.size() > 0) {
            pictures.setVisibility(View.VISIBLE);
            for (Content c : fileContents) {
                View content =
                        LayoutInflater.from(mContext)
                                .inflate(R.layout.chat_bubble_content, null, false);

                if (c.isFile()
                        || (c.isFileTransfer()
                                && message
                                        .isOutgoing())) { // If message is outgoing, even if content
                    // is file transfer we have the file
                    // available
                    String filePath = c.getFilePath();

                    View v;
                    if (FileUtils.isExtensionImage(filePath)) {
                        if (fileContents.size() == 1
                                && mContext.getResources()
                                        .getBoolean(
                                                R.bool.use_big_pictures_to_preview_images_file_transfers)) {
                            v = content.findViewById(R.id.bigImage);
                            loadBitmap(c.getFilePath(), ((ImageView) v));
                        } else {
                            v = content.findViewById(R.id.image);
                            loadBitmap(c.getFilePath(), ((ImageView) v));
                        }
                    } else {
                        v = content.findViewById(R.id.file);
                        ((TextView) v).setText(FileUtils.getNameFromFilePath(filePath));
                    }
                    v.setVisibility(View.VISIBLE);
                    v.setTag(c.getFilePath());
                    v.setOnClickListener(
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    openFile((String) v.getTag());
                                }
                            });
                } else {
                    Button download = content.findViewById(R.id.download);
                    download.setVisibility(View.VISIBLE);

                    if (mContext.getPackageManager()
                                    .checkPermission(
                                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                            mContext.getPackageName())
                            == PackageManager.PERMISSION_GRANTED) {
                        String filename = c.getName();
                        File file = new File(FileUtils.getStorageDirectory(mContext), filename);

                        int prefix = 1;
                        while (file.exists()) {
                            file =
                                    new File(
                                            FileUtils.getStorageDirectory(mContext),
                                            prefix + "_" + filename);
                            Log.w(
                                    "File with that name already exists, renamed to "
                                            + prefix
                                            + "_"
                                            + filename);
                            prefix += 1;
                        }

                        download.setTag(c);
                        c.setFilePath(file.getPath());
                        download.setOnClickListener(
                                new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        Content c = (Content) v.getTag();
                                        message.downloadContent(c);
                                    }
                                });
                    } else {
                        Log.w(
                                "WRITE_EXTERNAL_STORAGE permission not granted, won't be able to store the downloaded file");
                        LinphoneActivity.instance().checkAndRequestExternalStoragePermission();
                    }
                }

                pictures.addView(content);
            }
        }
    }

    private void openFile(String path) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        File file;
        Uri contentUri;
        if (path.startsWith("file://")) {
            path = path.substring("file://".length());
            file = new File(path);
            contentUri =
                    FileProvider.getUriForFile(
                            mContext,
                            mContext.getResources().getString(R.string.file_provider),
                            file);
        } else if (path.startsWith("content://")) {
            contentUri = Uri.parse(path);
        } else {
            file = new File(path);
            try {
                contentUri =
                        FileProvider.getUriForFile(
                                mContext,
                                mContext.getResources().getString(R.string.file_provider),
                                file);
            } catch (Exception e) {
                contentUri = Uri.parse(path);
            }
        }

        String type = null;
        String extension = MimeTypeMap.getFileExtensionFromUrl(contentUri.toString());
        if (extension != null) {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }
        if (type != null) {
            intent.setDataAndType(contentUri, type);
        } else {
            intent.setDataAndType(contentUri, "*/*");
        }
        intent.addFlags(FLAG_GRANT_READ_URI_PERMISSION);
        mContext.startActivity(intent);
    }

    private void loadBitmap(String path, ImageView imageView) {
        if (cancelPotentialWork(path, imageView)) {
            Bitmap defaultBitmap =
                    BitmapFactory.decodeResource(mContext.getResources(), R.drawable.chat_file);
            BitmapWorkerTask task = new BitmapWorkerTask(mContext, imageView, defaultBitmap);
            final AsyncBitmap asyncBitmap =
                    new AsyncBitmap(mContext.getResources(), defaultBitmap, task);
            imageView.setImageDrawable(asyncBitmap);
            task.execute(path);
        }
    }

    private boolean cancelPotentialWork(String path, ImageView imageView) {
        final BitmapWorkerTask bitmapWorkerTask = BitmapWorkerTask.getBitmapWorkerTask(imageView);

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
}
