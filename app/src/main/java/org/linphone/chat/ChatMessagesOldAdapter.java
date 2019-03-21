package org.linphone.chat;

/*
ChatMessagesAdapter.java
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
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.compatibility.Compatibility;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatMessageListenerStub;
import org.linphone.core.Content;
import org.linphone.core.EventLog;
import org.linphone.core.LimeState;
import org.linphone.core.tools.Log;
import org.linphone.utils.FileUtils;
import org.linphone.utils.LinphoneUtils;
import org.linphone.utils.SelectableAdapter;
import org.linphone.utils.SelectableHelper;
import org.linphone.views.AsyncBitmap;
import org.linphone.views.BitmapWorkerTask;
import org.linphone.views.ContactAvatar;

public class ChatMessagesOldAdapter extends SelectableAdapter<ChatMessageOldViewHolder>
        implements ChatMessagesGenericAdapter {

    private static final int MARGIN_BETWEEN_MESSAGES = 10;
    private static final int SIDE_MARGIN = 100;
    private final Context mContext;
    private List<EventLog> mHistory;
    private List<LinphoneContact> mParticipants;
    private final int mItemResource;
    private Bitmap mDefaultBitmap;
    private final ChatMessagesFragment mFragment;
    private final ChatMessageListenerStub mListener;

    private final ChatMessageViewHolderClickListener mClickListener;

    public ChatMessagesOldAdapter(
            ChatMessagesFragment fragment,
            SelectableHelper helper,
            int itemResource,
            EventLog[] history,
            ArrayList<LinphoneContact> participants,
            ChatMessageViewHolderClickListener clickListener) {
        super(helper);
        mFragment = fragment;
        mContext = mFragment.getActivity();
        mItemResource = itemResource;
        mHistory = new ArrayList<>(Arrays.asList(history));
        Collections.reverse(mHistory);
        mParticipants = participants;
        mClickListener = clickListener;
        mListener =
                new ChatMessageListenerStub() {
                    @Override
                    public void onFileTransferProgressIndication(
                            ChatMessage message, Content content, int offset, int total) {
                        ChatMessageOldViewHolder holder =
                                (ChatMessageOldViewHolder) message.getUserData();
                        if (holder == null) return;

                        if (offset == total) {
                            holder.fileTransferProgressBar.setVisibility(View.GONE);
                            holder.fileTransferAction.setVisibility(View.GONE);
                            holder.fileTransferLayout.setVisibility(View.GONE);

                            displayAttachedFile(message, holder);
                        } else {
                            holder.fileTransferProgressBar.setVisibility(View.VISIBLE);
                            holder.fileTransferProgressBar.setProgress(offset * 100 / total);
                        }
                    }

                    @Override
                    public void onMsgStateChanged(ChatMessage message, ChatMessage.State state) {
                        if (state == ChatMessage.State.FileTransferDone) {
                            if (!message.isOutgoing()) {
                                message.setAppdata(message.getFileTransferFilepath());
                            }
                            message.setFileTransferFilepath(
                                    null); // Not needed anymore, will help differenciate between
                            // InProgress states for file transfer / message sending
                        }
                        for (int i = 0; i < mHistory.size(); i++) {
                            EventLog log = mHistory.get(i);
                            if (log.getType() == EventLog.Type.ConferenceChatMessage
                                    && log.getChatMessage() == message) {
                                notifyItemChanged(i);
                                break;
                            }
                        }
                    }
                };
    }

    @Override
    public ChatMessageOldViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(mItemResource, parent, false);
        ChatMessageOldViewHolder VH = new ChatMessageOldViewHolder(v, mClickListener);

        // Allows onLongClick ContextMenu on bubbles
        mFragment.registerForContextMenu(v);
        v.setTag(VH);
        return VH;
    }

    @Override
    public void onBindViewHolder(@NonNull final ChatMessageOldViewHolder holder, int position) {
        final EventLog event = mHistory.get(position);
        holder.eventLayout.setVisibility(View.GONE);
        holder.bubbleLayout.setVisibility(View.GONE);
        holder.delete.setVisibility(isEditionEnabled() ? View.VISIBLE : View.GONE);
        holder.messageText.setVisibility(View.GONE);
        holder.messageImage.setVisibility(View.GONE);
        holder.fileTransferLayout.setVisibility(View.GONE);
        holder.fileTransferProgressBar.setProgress(0);
        holder.fileTransferAction.setEnabled(true);
        holder.fileName.setVisibility(View.GONE);
        holder.openFileButton.setVisibility(View.GONE);
        holder.messageStatus.setVisibility(View.INVISIBLE);
        holder.messageSendingInProgress.setVisibility(View.GONE);
        holder.imdmLayout.setVisibility(View.INVISIBLE);

        if (isEditionEnabled()) {
            holder.delete.setOnCheckedChangeListener(null);
            holder.delete.setChecked(isSelected(position));
            holder.delete.setTag(position);
        }

        if (event.getType() == EventLog.Type.ConferenceChatMessage) {
            holder.bubbleLayout.setVisibility(View.VISIBLE);
            final ChatMessage message = event.getChatMessage();

            if (position > 0
                    && mContext.getResources()
                            .getBoolean(R.bool.lower_space_between_chat_bubbles_if_same_person)) {
                EventLog previousEvent = (EventLog) getItem(position - 1);
                if (previousEvent.getType() == EventLog.Type.ConferenceChatMessage) {
                    ChatMessage previousMessage = previousEvent.getChatMessage();
                    if (previousMessage.getFromAddress().weakEqual(message.getFromAddress())) {
                        holder.separatorLayout.setVisibility(View.GONE);
                    }
                } else {
                    // No separator if previous event is not a message
                    holder.separatorLayout.setVisibility(View.GONE);
                }
            }

            message.setUserData(holder);
            message.addListener(mListener);

            RelativeLayout.LayoutParams layoutParams =
                    new RelativeLayout.LayoutParams(
                            RelativeLayout.LayoutParams.WRAP_CONTENT,
                            RelativeLayout.LayoutParams.WRAP_CONTENT);

            ChatMessage.State status = message.getState();
            Address remoteSender = message.getFromAddress();
            String displayName;

            LinphoneContact contact = null;
            if (message.isOutgoing()) {
                if (status == ChatMessage.State.InProgress) {
                    holder.messageSendingInProgress.setVisibility(View.VISIBLE);
                }

                if (!message.isSecured()
                        && LinphoneManager.getLc().limeEnabled() == LimeState.Mandatory
                        && status != ChatMessage.State.InProgress) {
                    holder.messageStatus.setVisibility(View.VISIBLE);
                    holder.messageStatus.setImageResource(R.drawable.chat_unsecure);
                }

                if (status == ChatMessage.State.DeliveredToUser) {
                    holder.imdmLayout.setVisibility(View.VISIBLE);
                    holder.imdmIcon.setImageResource(R.drawable.imdn_received);
                    holder.imdmLabel.setText(R.string.delivered);
                    holder.imdmLabel.setTextColor(
                            mContext.getResources().getColor(R.color.grey_color));
                } else if (status == ChatMessage.State.Displayed) {
                    holder.imdmLayout.setVisibility(View.VISIBLE);
                    holder.imdmIcon.setImageResource(R.drawable.imdn_read);
                    holder.imdmLabel.setText(R.string.displayed);
                    holder.imdmLabel.setTextColor(
                            mContext.getResources().getColor(R.color.imdn_read_color));
                } else if (status == ChatMessage.State.NotDelivered) {
                    holder.imdmLayout.setVisibility(View.VISIBLE);
                    holder.imdmIcon.setImageResource(R.drawable.imdn_error);
                    holder.imdmLabel.setText(R.string.error);
                    holder.imdmLabel.setTextColor(
                            mContext.getResources().getColor(R.color.red_color));
                } else if (status == ChatMessage.State.FileTransferError) {
                    holder.imdmLayout.setVisibility(View.VISIBLE);
                    holder.imdmIcon.setImageResource(R.drawable.imdn_error);
                    holder.imdmLabel.setText(R.string.file_transfer_error);
                    holder.imdmLabel.setTextColor(
                            mContext.getResources().getColor(R.color.red_color));
                }

                // layoutParams allow bubbles alignment during selection mode
                if (isEditionEnabled()) {
                    layoutParams.addRule(RelativeLayout.LEFT_OF, holder.delete.getId());
                    layoutParams.setMargins(
                            SIDE_MARGIN,
                            MARGIN_BETWEEN_MESSAGES / 2,
                            0,
                            MARGIN_BETWEEN_MESSAGES / 2);
                } else {
                    layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                    layoutParams.setMargins(
                            SIDE_MARGIN,
                            MARGIN_BETWEEN_MESSAGES / 2,
                            0,
                            MARGIN_BETWEEN_MESSAGES / 2);
                }

                holder.background.setBackgroundResource(R.drawable.resizable_chat_bubble_outgoing);
                Compatibility.setTextAppearance(holder.contactName, mContext, R.style.font3);
                Compatibility.setTextAppearance(
                        holder.fileTransferAction, mContext, R.style.font15);
                holder.fileTransferAction.setBackgroundResource(
                        R.drawable.resizable_confirm_delete_button);
            } else {
                for (LinphoneContact c : mParticipants) {
                    if (c != null && c.hasAddress(remoteSender.asStringUriOnly())) {
                        contact = c;
                        break;
                    }
                }

                if (isEditionEnabled()) {
                    layoutParams.addRule(RelativeLayout.LEFT_OF, holder.delete.getId());
                    layoutParams.setMargins(
                            SIDE_MARGIN,
                            MARGIN_BETWEEN_MESSAGES / 2,
                            0,
                            MARGIN_BETWEEN_MESSAGES / 2);
                } else {
                    layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
                    layoutParams.setMargins(
                            0,
                            MARGIN_BETWEEN_MESSAGES / 2,
                            SIDE_MARGIN,
                            MARGIN_BETWEEN_MESSAGES / 2);
                }

                holder.background.setBackgroundResource(R.drawable.resizable_chat_bubble_incoming);
                Compatibility.setTextAppearance(
                        holder.contactName, mContext, R.style.contact_organization_font);
                Compatibility.setTextAppearance(
                        holder.fileTransferAction, mContext, R.style.button_font);
                holder.fileTransferAction.setBackgroundResource(
                        R.drawable.resizable_assistant_button);
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
                ContactAvatar.displayAvatar(contact, holder.avatarLayout);
            } else {
                displayName = LinphoneUtils.getAddressDisplayName(remoteSender);
                ContactAvatar.displayAvatar(displayName, holder.avatarLayout);
            }
            holder.contactName.setText(
                    LinphoneUtils.timestampToHumanDate(
                                    mContext, message.getTime(), R.string.messages_date_format)
                            + " - "
                            + displayName);

            if (message.hasTextContent()) {
                String msg = message.getTextContent();
                Spanned text = LinphoneUtils.getTextWithHttpLinks(msg);
                holder.messageText.setText(text);
                holder.messageText.setMovementMethod(LinkMovementMethod.getInstance());
                holder.messageText.setVisibility(View.VISIBLE);
            }

            String externalBodyUrl = message.getExternalBodyUrl();
            Content fileTransferContent = message.getFileTransferInformation();

            boolean hasFile = message.getAppdata() != null;
            boolean hasFileTransfer = externalBodyUrl != null;
            for (Content c : message.getContents()) {
                if (c.isFile()) {
                    hasFile = true;
                } else if (c.isFileTransfer()) {
                    hasFileTransfer = true;
                }
            }
            if (hasFile) { // Something to display
                displayAttachedFile(message, holder);
            }

            if (hasFileTransfer) { // Incoming file transfer not yet downloaded
                holder.fileName.setVisibility(View.VISIBLE);
                holder.fileName.setText(fileTransferContent.getName());

                holder.fileTransferLayout.setVisibility(View.VISIBLE);
                holder.fileTransferProgressBar.setVisibility(View.GONE);
                if (message.isFileTransferInProgress()) { // Incoming file transfer in progress
                    holder.fileTransferAction.setVisibility(View.GONE);
                } else {
                    holder.fileTransferAction.setText(mContext.getString(R.string.accept));
                    holder.fileTransferAction.setOnClickListener(
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    if (mContext.getPackageManager()
                                                    .checkPermission(
                                                            Manifest.permission
                                                                    .WRITE_EXTERNAL_STORAGE,
                                                            mContext.getPackageName())
                                            == PackageManager.PERMISSION_GRANTED) {
                                        v.setEnabled(false);
                                        String filename =
                                                message.getFileTransferInformation().getName();
                                        File file =
                                                new File(
                                                        FileUtils.getStorageDirectory(mContext),
                                                        filename);
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
                                        message.setFileTransferFilepath(file.getPath());
                                        message.downloadFile();

                                    } else {
                                        Log.w(
                                                "WRITE_EXTERNAL_STORAGE permission not granted, won't be able to store the downloaded file");
                                        LinphoneActivity.instance()
                                                .checkAndRequestExternalStoragePermission();
                                    }
                                }
                            });
                }
            } else if (message.isFileTransferInProgress()) { // Outgoing file transfer in progress
                holder.messageSendingInProgress.setVisibility(View.GONE);
                holder.fileTransferLayout.setVisibility(View.VISIBLE);
                holder.fileTransferAction.setText(mContext.getString(R.string.cancel));
                holder.fileTransferAction.setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                message.cancelFileTransfer();
                                notifyItemChanged(holder.getAdapterPosition());
                            }
                        });
            }

            holder.bubbleLayout.setLayoutParams(layoutParams);
        } else { // Event is not chat message
            holder.eventLayout.setVisibility(View.VISIBLE);
            holder.eventMessage.setTextColor(
                    mContext.getResources().getColor(R.color.light_grey_color));
            holder.eventLayout.setBackgroundResource(R.drawable.event_decoration_gray);
            holder.eventLayout.setBackgroundResource(R.drawable.event_decoration_gray);

            Address address = event.getParticipantAddress();
            if (address == null && event.getType() == EventLog.Type.ConferenceSecurityEvent) {
                address = event.getSecurityEventFaultyDeviceAddress();
            }
            String displayName = "";
            if (address != null) {
                LinphoneContact contact =
                        ContactsManager.getInstance().findContactFromAddress(address);
                if (contact != null) {
                    displayName = contact.getFullName();
                } else {
                    displayName = LinphoneUtils.getAddressDisplayName(address);
                }
            }

            switch (event.getType()) {
                case ConferenceCreated:
                    holder.eventMessage.setText(mContext.getString(R.string.conference_created));
                    break;
                case ConferenceTerminated:
                    holder.eventMessage.setText(mContext.getString(R.string.conference_destroyed));
                    break;
                case ConferenceParticipantAdded:
                    holder.eventMessage.setText(
                            mContext.getString(R.string.participant_added)
                                    .replace("%s", displayName));
                    break;
                case ConferenceParticipantRemoved:
                    holder.eventMessage.setText(
                            mContext.getString(R.string.participant_removed)
                                    .replace("%s", displayName));
                    break;
                case ConferenceSubjectChanged:
                    holder.eventMessage.setText(
                            mContext.getString(R.string.subject_changed)
                                    .replace("%s", event.getSubject()));
                    break;
                case ConferenceParticipantSetAdmin:
                    holder.eventMessage.setText(
                            mContext.getString(R.string.admin_set).replace("%s", displayName));
                    break;
                case ConferenceParticipantUnsetAdmin:
                    holder.eventMessage.setText(
                            mContext.getString(R.string.admin_unset).replace("%s", displayName));
                    break;
                case ConferenceParticipantDeviceAdded:
                    holder.eventMessage.setText(
                            mContext.getString(R.string.device_added).replace("%s", displayName));
                    break;
                case ConferenceParticipantDeviceRemoved:
                    holder.eventMessage.setText(
                            mContext.getString(R.string.device_removed).replace("%s", displayName));
                    break;
                case ConferenceSecurityEvent:
                    holder.eventMessage.setTextColor(
                            mContext.getResources().getColor(R.color.red_color));
                    holder.eventLayout.setBackgroundResource(R.drawable.event_decoration_red);
                    holder.eventLayout.setBackgroundResource(R.drawable.event_decoration_red);

                    switch (event.getSecurityEventType()) {
                        case EncryptionIdentityKeyChanged:
                            holder.eventMessage.setText(
                                    mContext.getString(R.string.lime_identity_key_changed)
                                            .replace("%s", displayName));
                            break;
                        case ManInTheMiddleDetected:
                            holder.eventMessage.setText(
                                    mContext.getString(R.string.man_in_the_middle_detected)
                                            .replace("%s", displayName));
                            break;
                        case SecurityLevelDowngraded:
                            holder.eventMessage.setText(
                                    mContext.getString(R.string.security_level_downgraded)
                                            .replace("%s", displayName));
                            break;
                        case ParticipantMaxDeviceCountExceeded:
                            holder.eventMessage.setText(
                                    mContext.getString(R.string.participant_max_count_exceeded)
                                            .replace("%s", displayName));
                            break;
                        case None:
                        default:
                            break;
                    }
                    break;
                case None:
                default:
                    holder.eventMessage.setText(
                            mContext.getString(R.string.unexpected_event)
                                    .replace("%s", displayName)
                                    .replace("%i", String.valueOf(event.getType().toInt())));
                    break;
            }
        }
    }

    @Override
    public int getItemCount() {
        return mHistory.size();
    }

    public void addToHistory(EventLog log) {
        mHistory.add(0, log);
        notifyItemInserted(0);
    }

    public void addAllToHistory(ArrayList<EventLog> logs) {
        int currentSize = mHistory.size() - 1;
        Collections.reverse(logs);
        mHistory.addAll(logs);
        notifyItemRangeInserted(currentSize + 1, logs.size());
    }

    public void setContacts(ArrayList<LinphoneContact> participants) {
        mParticipants = participants;
    }

    public void refresh(EventLog[] history) {
        mHistory = new ArrayList<>(Arrays.asList(history));
        Collections.reverse(mHistory);
        notifyDataSetChanged();
    }

    public void clear() {
        for (EventLog event : mHistory) {
            if (event.getType() == EventLog.Type.ConferenceChatMessage) {
                ChatMessage message = event.getChatMessage();
                message.removeListener(mListener);
            }
        }
        mHistory.clear();
    }

    public Object getItem(int i) {
        return mHistory.get(i);
    }

    public void removeItem(int i) {
        mHistory.remove(i);
        notifyItemRemoved(i);
    }

    private void loadBitmap(String path, ImageView imageView) {
        if (cancelPotentialWork(path, imageView)) {
            mDefaultBitmap =
                    BitmapFactory.decodeResource(mContext.getResources(), R.drawable.chat_file);
            BitmapWorkerTask task = new BitmapWorkerTask(mContext, imageView, mDefaultBitmap);
            final AsyncBitmap asyncBitmap =
                    new AsyncBitmap(mContext.getResources(), mDefaultBitmap, task);
            imageView.setImageDrawable(asyncBitmap);
            task.execute(path);
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

    private void displayAttachedFile(ChatMessage message, ChatMessageOldViewHolder holder) {
        holder.fileName.setVisibility(View.VISIBLE);

        String appData = message.getAppdata();
        if (appData == null) {
            for (Content c : message.getContents()) {
                if (c.isFile()) {
                    appData = c.getFilePath();
                }
            }
        }

        if (appData != null) {
            FileUtils.scanFile(message);
            holder.fileName.setText(FileUtils.getNameFromFilePath(appData));
            if (FileUtils.isExtensionImage(appData)) {
                holder.messageImage.setVisibility(View.VISIBLE);
                loadBitmap(appData, holder.messageImage);
                holder.messageImage.setTag(appData);
            } else {
                holder.openFileButton.setVisibility(View.VISIBLE);
                holder.openFileButton.setTag(appData);
                holder.openFileButton.setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                openFile((String) v.getTag());
                            }
                        });
            }
        }
    }

    private boolean cancelPotentialWork(String path, ImageView imageView) {
        final BitmapWorkerTask bitmapWorkerTask = BitmapWorkerTask.getBitmapWorkerTask(imageView);

        if (bitmapWorkerTask != null) {
            final String bitmapData = bitmapWorkerTask.path;
            // If bitmapData is not yet set or it differs from the new data
            if (bitmapData == null || !bitmapData.equals(path)) {
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
