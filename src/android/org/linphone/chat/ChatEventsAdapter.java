/*
ChatEventsAdapter.java
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

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.content.FileProvider;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import org.linphone.LinphoneManager;
import org.linphone.LinphoneUtils;
import org.linphone.R;
import org.linphone.activities.LinphoneActivity;
import org.linphone.compatibility.Compatibility;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatMessageListenerStub;
import org.linphone.core.Content;
import org.linphone.core.EventLog;
import org.linphone.core.LimeState;
import org.linphone.mediastream.Log;
import org.linphone.ui.SelectableAdapter;
import org.linphone.ui.SelectableHelper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;

public class ChatEventsAdapter extends SelectableAdapter<ChatBubbleViewHolder> {

    private static int MARGIN_BETWEEN_MESSAGES = 10;
    private static int SIDE_MARGIN = 100;
    private Context mContext;
    private List<EventLog> mHistory;
    private List<LinphoneContact> mParticipants;
    private int mItemResource;
    private Bitmap mDefaultBitmap;
    private GroupChatFragment mFragment;
    private ChatMessageListenerStub mListener;

    private ChatBubbleViewHolder.ClickListener mClickListener;

    public ChatEventsAdapter(GroupChatFragment fragment, SelectableHelper helper, int itemResource, EventLog[] history, ArrayList<LinphoneContact> participants, ChatBubbleViewHolder.ClickListener clickListener) {
        super(helper);
        mFragment = fragment;
        mContext = mFragment.getActivity();
        mItemResource = itemResource;
        mHistory = new ArrayList<>(Arrays.asList(history));
        Collections.reverse(mHistory);
        mParticipants = participants;
        mClickListener = clickListener;
        mListener = new ChatMessageListenerStub() {
            @Override
            public void onFileTransferProgressIndication(ChatMessage message, Content content, int offset, int total) {
                ChatBubbleViewHolder holder = (ChatBubbleViewHolder) message.getUserData();
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
                    message.setFileTransferFilepath(null); // Not needed anymore, will help differenciate between InProgress states for file transfer / message sending
                }
                for (int i = 0; i < mHistory.size(); i++) {
                    EventLog log = mHistory.get(i);
                    if (log.getType() == EventLog.Type.ConferenceChatMessage && log.getChatMessage() == message) {
                        notifyItemChanged(i);
                        break;
                    }
                }

            }
        };
    }

    @Override
    public ChatBubbleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(mItemResource, parent, false);
        ChatBubbleViewHolder VH = new ChatBubbleViewHolder(mContext, v, mClickListener);

        //Allows onLongClick ContextMenu on bubbles
        mFragment.registerForContextMenu(v);
        v.setTag(VH);
        return VH;
    }

    @Override
    public void onBindViewHolder(@NonNull ChatBubbleViewHolder holder, final int position) {
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
        holder.contactPicture.setImageBitmap(ContactsManager.getInstance().getDefaultAvatarBitmap());

        if (isEditionEnabled()) {
            holder.delete.setOnCheckedChangeListener(null);
            holder.delete.setChecked(isSelected(position));
            holder.delete.setTag(position);
        }

        if (event.getType() == EventLog.Type.ConferenceChatMessage) {
            holder.bubbleLayout.setVisibility(View.VISIBLE);
            final ChatMessage message = event.getChatMessage();

            if (position > 0 && mContext.getResources().getBoolean(R.bool.lower_space_between_chat_bubbles_if_same_person)) {
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

            holder.messageId = message.getMessageId();
            message.setUserData(holder);

            RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);

            ChatMessage.State status = message.getState();
            Address remoteSender = message.getFromAddress();
            String displayName;

            LinphoneContact contact = null;
            if (message.isOutgoing()) {
                message.setListener(mListener);

                if (status == ChatMessage.State.InProgress) {
                    holder.messageSendingInProgress.setVisibility(View.VISIBLE);
                }

                if (!message.isSecured() && LinphoneManager.getLc().limeEnabled() == LimeState.Mandatory && status != ChatMessage.State.InProgress) {
                    holder.messageStatus.setVisibility(View.VISIBLE);
                    holder.messageStatus.setImageResource(R.drawable.chat_unsecure);
                }

                if (status == ChatMessage.State.Delivered) {
				    /*holder.imdmLayout.setVisibility(View.VISIBLE);
				    holder.imdmLabel.setText(R.string.sent);
				    holder.imdmIcon.setImageResource(R.drawable.chat_delivered);
				    holder.imdmLabel.setTextColor(mContext.getResources().getColor(R.color.colorD));*/
                } else if (status == ChatMessage.State.DeliveredToUser) {
                    holder.imdmLayout.setVisibility(View.VISIBLE);
                    holder.imdmIcon.setImageResource(R.drawable.chat_delivered);
                    holder.imdmLabel.setText(R.string.delivered);
                    holder.imdmLabel.setTextColor(mContext.getResources().getColor(R.color.colorD));
                } else if (status == ChatMessage.State.Displayed) {
                    holder.imdmLayout.setVisibility(View.VISIBLE);
                    holder.imdmIcon.setImageResource(R.drawable.chat_read);
                    holder.imdmLabel.setText(R.string.displayed);
                    holder.imdmLabel.setTextColor(mContext.getResources().getColor(R.color.colorK));
                } else if (status == ChatMessage.State.NotDelivered) {
                    holder.imdmLayout.setVisibility(View.VISIBLE);
                    holder.imdmIcon.setImageResource(R.drawable.chat_error);
                    holder.imdmLabel.setText(R.string.error);
                    holder.imdmLabel.setTextColor(mContext.getResources().getColor(R.color.colorI));
                } else if (status == ChatMessage.State.FileTransferError) {
                    holder.imdmLayout.setVisibility(View.VISIBLE);
                    holder.imdmIcon.setImageResource(R.drawable.chat_error);
                    holder.imdmLabel.setText(R.string.file_transfer_error);
                    holder.imdmLabel.setTextColor(mContext.getResources().getColor(R.color.colorI));
                }

                //layoutParams allow bubbles alignment during selection mode
                if (isEditionEnabled()) {
                    layoutParams.addRule(RelativeLayout.LEFT_OF, holder.delete.getId());
                    layoutParams.setMargins(SIDE_MARGIN, MARGIN_BETWEEN_MESSAGES / 2, 0, MARGIN_BETWEEN_MESSAGES / 2);
                } else {
                    layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                    layoutParams.setMargins(SIDE_MARGIN, MARGIN_BETWEEN_MESSAGES / 2, 0, MARGIN_BETWEEN_MESSAGES / 2);
                }

                holder.background.setBackgroundResource(R.drawable.resizable_chat_bubble_outgoing);
                Compatibility.setTextAppearance(holder.contactName, mContext, R.style.font3);
                Compatibility.setTextAppearance(holder.fileTransferAction, mContext, R.style.font15);
                holder.fileTransferAction.setBackgroundResource(R.drawable.resizable_confirm_delete_button);
                holder.contactPictureMask.setImageResource(R.drawable.avatar_chat_mask_outgoing);
            } else {
                for (LinphoneContact c : mParticipants) {
                    if (c != null && c.hasAddress(remoteSender.asStringUriOnly())) {
                        contact = c;
                        break;
                    }
                }

                if (isEditionEnabled()) {
                    layoutParams.addRule(RelativeLayout.LEFT_OF, holder.delete.getId());
                    layoutParams.setMargins(SIDE_MARGIN, MARGIN_BETWEEN_MESSAGES / 2, 0, MARGIN_BETWEEN_MESSAGES / 2);
                } else {
                    layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
                    layoutParams.setMargins(0, MARGIN_BETWEEN_MESSAGES / 2, SIDE_MARGIN, MARGIN_BETWEEN_MESSAGES / 2);
                }

                holder.background.setBackgroundResource(R.drawable.resizable_chat_bubble_incoming);
                Compatibility.setTextAppearance(holder.contactName, mContext, R.style.font9);
                Compatibility.setTextAppearance(holder.fileTransferAction, mContext, R.style.font8);
                holder.fileTransferAction.setBackgroundResource(R.drawable.resizable_assistant_button);
                holder.contactPictureMask.setImageResource(R.drawable.avatar_chat_mask);
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

                holder.contactPicture.setImageBitmap(ContactsManager.getInstance().getDefaultAvatarBitmap());
                if (contact.hasPhoto()) {
                    LinphoneUtils.setThumbnailPictureFromUri(LinphoneActivity.instance(), holder.contactPicture, contact.getThumbnailUri());
                }
            } else {
                displayName = LinphoneUtils.getAddressDisplayName(remoteSender);
                holder.contactPicture.setImageBitmap(ContactsManager.getInstance().getDefaultAvatarBitmap());
            }
            holder.contactName.setText(LinphoneUtils.timestampToHumanDate(mContext, message.getTime(), R.string.messages_date_format) + " - " + displayName);

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
                    holder.fileTransferAction.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (mContext.getPackageManager().checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, mContext.getPackageName()) == PackageManager.PERMISSION_GRANTED) {
                                v.setEnabled(false);
                                String filename = message.getFileTransferInformation().getName();
                                File file = new File(LinphoneUtils.getStorageDirectory(mContext), filename);
                                int prefix = 1;
                                while (file.exists()) {
                                    file = new File(LinphoneUtils.getStorageDirectory(mContext), prefix + "_" + filename);
                                    Log.w("File with that name already exists, renamed to " + prefix + "_" + filename);
                                    prefix += 1;
                                }
                                message.setListener(mListener);
                                message.setFileTransferFilepath(file.getPath());
                                message.downloadFile();

                            } else {
                                Log.w("WRITE_EXTERNAL_STORAGE permission not granted, won't be able to store the downloaded file");
                                LinphoneActivity.instance().checkAndRequestExternalStoragePermission();
                            }
                        }
                    });
                }
            } else if (message.isFileTransferInProgress()) { // Outgoing file transfer in progress
                message.setListener(mListener); // add the listener for file upload progress display
                holder.messageSendingInProgress.setVisibility(View.GONE);
                holder.fileTransferLayout.setVisibility(View.VISIBLE);
                holder.fileTransferAction.setText(mContext.getString(R.string.cancel));
                holder.fileTransferAction.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        message.cancelFileTransfer();
                        notifyItemChanged(position);
                    }
                });
            }

            holder.bubbleLayout.setLayoutParams(layoutParams);
        } else { // Event is not chat message
            holder.eventLayout.setVisibility(View.VISIBLE);

            Address address = event.getParticipantAddress();
            String displayName = null;
            if (address != null) {
                LinphoneContact contact = ContactsManager.getInstance().findContactFromAddress(address);
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
                    holder.eventMessage.setText(mContext.getString(R.string.participant_added).replace("%s", displayName));
                    break;
                case ConferenceParticipantRemoved:
                    holder.eventMessage.setText(mContext.getString(R.string.participant_removed).replace("%s", displayName));
                    break;
                case ConferenceSubjectChanged:
                    holder.eventMessage.setText(mContext.getString(R.string.subject_changed).replace("%s", event.getSubject()));
                    break;
                case ConferenceParticipantSetAdmin:
                    holder.eventMessage.setText(mContext.getString(R.string.admin_set).replace("%s", displayName));
                    break;
                case ConferenceParticipantUnsetAdmin:
                    holder.eventMessage.setText(mContext.getString(R.string.admin_unset).replace("%s", displayName));
                    break;
                case ConferenceParticipantDeviceAdded:
                    holder.eventMessage.setText(mContext.getString(R.string.device_added).replace("%s", displayName));
                    break;
                case ConferenceParticipantDeviceRemoved:
                    holder.eventMessage.setText(mContext.getString(R.string.device_removed).replace("%s", displayName));
                    break;
                case None:
                default:
                    //TODO
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
                message.setListener(null);
            }
        }
        mHistory.clear();
    }

    public int getCount() {
        return mHistory.size();
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
            if (LinphoneUtils.isExtensionImage(path)) {
                mDefaultBitmap = BitmapFactory.decodeResource(mContext.getResources(), R.drawable.chat_attachment_over);
            } else {
                mDefaultBitmap = BitmapFactory.decodeResource(mContext.getResources(), R.drawable.chat_attachment);
            }

            BitmapWorkerTask task = new BitmapWorkerTask(imageView);
            final AsyncBitmap asyncBitmap = new AsyncBitmap(mContext.getResources(), mDefaultBitmap, task);
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
            contentUri = FileProvider.getUriForFile(mContext, mContext.getResources().getString(R.string.file_provider), file);
        } else if (path.startsWith("content://")) {
            contentUri = Uri.parse(path);
        } else {
            file = new File(path);
            try {
                contentUri = FileProvider.getUriForFile(mContext, mContext.getResources().getString(R.string.file_provider), file);
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

    private void displayAttachedFile(ChatMessage message, ChatBubbleViewHolder holder) {
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
            LinphoneUtils.scanFile(message);
            holder.fileName.setText(LinphoneUtils.getNameFromFilePath(appData));
            if (LinphoneUtils.isExtensionImage(appData)) {
                holder.messageImage.setVisibility(View.VISIBLE);
                loadBitmap(appData, holder.messageImage);
                holder.messageImage.setTag(appData);
            } else {
                holder.openFileButton.setVisibility(View.VISIBLE);
                holder.openFileButton.setTag(appData);
                holder.openFileButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openFile((String) v.getTag());
                    }
                });
            }
        }
    }

    /*
     * Bitmap related classes and methods
     */

    private class BitmapWorkerTask extends AsyncTask<String, Void, Bitmap> {
        private static final int SIZE_SMALL = 500;
        private final WeakReference<ImageView> imageViewReference;
        public String path;

        public BitmapWorkerTask(ImageView imageView) {
            path = null;
            // Use a WeakReference to ensure the ImageView can be garbage collected
            imageViewReference = new WeakReference<ImageView>(imageView);
        }

        // Decode image in background.
        @Override
        protected Bitmap doInBackground(String... params) {
            path = params[0];
            Bitmap bm = null;
            Bitmap thumbnail = null;
            if (LinphoneUtils.isExtensionImage(path)) {
                if (path.startsWith("content")) {
                    try {
                        bm = MediaStore.Images.Media.getBitmap(mContext.getContentResolver(), Uri.parse(path));
                    } catch (FileNotFoundException e) {
                        Log.e(e);
                    } catch (IOException e) {
                        Log.e(e);
                    }
                } else {
                    bm = BitmapFactory.decodeFile(path);
                }

                // Rotate the bitmap if possible/needed, using EXIF data
                try {
                    Bitmap bm_tmp;
                    ExifInterface exif = new ExifInterface(path);
                    int pictureOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0);
                    Matrix matrix = new Matrix();
                    if (pictureOrientation == 6) {
                        matrix.postRotate(90);
                    } else if (pictureOrientation == 3) {
                        matrix.postRotate(180);
                    } else if (pictureOrientation == 8) {
                        matrix.postRotate(270);
                    }
                    bm_tmp = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), matrix, true);
                    if (bm_tmp != bm) {
                        bm.recycle();
                        bm = bm_tmp;
                    }
                } catch (Exception e) {
                    Log.e(e);
                }

                if (bm != null) {
                    thumbnail = ThumbnailUtils.extractThumbnail(bm, SIZE_SMALL, SIZE_SMALL);
                    bm.recycle();
                }
                return thumbnail;
            } else {
                return mDefaultBitmap;
            }
        }

        // Once complete, see if ImageView is still around and set bitmap.
        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (isCancelled()) {
                bitmap = null;
            }
            if (imageViewReference != null && bitmap != null) {
                final ImageView imageView = imageViewReference.get();
                final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);
                if (this == bitmapWorkerTask && imageView != null) {
                    imageView.setImageBitmap(bitmap);
                    imageView.setTag(path);
                    imageView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            openFile((String) v.getTag());
                        }
                    });
                }
            }
        }
    }

    class AsyncBitmap extends BitmapDrawable {
        private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

        public AsyncBitmap(Resources res, Bitmap bitmap, BitmapWorkerTask bitmapWorkerTask) {
            super(res, bitmap);
            bitmapWorkerTaskReference = new WeakReference<>(bitmapWorkerTask);
        }

        public BitmapWorkerTask getBitmapWorkerTask() {
            return bitmapWorkerTaskReference.get();
        }
    }

    private boolean cancelPotentialWork(String path, ImageView imageView) {
        final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

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

    private BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {
        if (imageView != null) {
            final Drawable drawable = imageView.getDrawable();
            if (drawable instanceof AsyncBitmap) {
                final AsyncBitmap asyncDrawable = (AsyncBitmap) drawable;
                return asyncDrawable.getBitmapWorkerTask();
            }
        }
        return null;
    }
}
