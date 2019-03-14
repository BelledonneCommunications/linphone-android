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

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.linphone.R;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatMessageListenerStub;
import org.linphone.core.EventLog;
import org.linphone.utils.LinphoneUtils;
import org.linphone.utils.SelectableAdapter;
import org.linphone.utils.SelectableHelper;

public class ChatMessagesAdapter extends SelectableAdapter<ChatMessageViewHolder>
        implements ChatMessagesGenericAdapter {
    private static final int MAX_TIME_TO_GROUP_MESSAGES = 300; // 5 minutes

    private final Context mContext;
    private List<EventLog> mHistory;
    private List<LinphoneContact> mParticipants;
    private final int mItemResource;
    private final ChatMessagesFragment mFragment;

    private final List<ChatMessage> mTransientMessages;

    private final ChatMessageViewHolderClickListener mClickListener;
    private final ChatMessageListenerStub mListener;

    public ChatMessagesAdapter(
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
        mTransientMessages = new ArrayList<>();

        mListener =
                new ChatMessageListenerStub() {
                    @Override
                    public void onMsgStateChanged(ChatMessage message, ChatMessage.State state) {
                        ChatMessageViewHolder holder =
                                (ChatMessageViewHolder) message.getUserData();
                        if (holder != null) {
                            int position = holder.getAdapterPosition();
                            if (position >= 0) {
                                notifyItemChanged(position);
                            } else {
                                notifyDataSetChanged();
                            }
                        } else {
                            // Just in case, better to refresh the whole view than to miss
                            // an update
                            notifyDataSetChanged();
                        }
                        if (state == ChatMessage.State.Displayed) {
                            mTransientMessages.remove(message);
                        }
                    }
                };
    }

    @Override
    public ChatMessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(mItemResource, parent, false);
        ChatMessageViewHolder VH = new ChatMessageViewHolder(mContext, v, mClickListener);

        // Allows onLongClick ContextMenu on bubbles
        mFragment.registerForContextMenu(v);
        v.setTag(VH);
        return VH;
    }

    @Override
    public void onBindViewHolder(@NonNull ChatMessageViewHolder holder, int position) {
        if (position < 0) return;
        EventLog event = mHistory.get(position);

        holder.delete.setVisibility(View.GONE);
        holder.eventLayout.setVisibility(View.GONE);
        holder.securityEventLayout.setVisibility(View.GONE);
        holder.rightAnchor.setVisibility(View.GONE);
        holder.bubbleLayout.setVisibility(View.GONE);
        holder.sendInProgress.setVisibility(View.GONE);

        if (isEditionEnabled()) {
            holder.delete.setVisibility(View.VISIBLE);
            holder.delete.setChecked(isSelected(position));
            holder.delete.setTag(position);
        }

        if (event.getType() == EventLog.Type.ConferenceChatMessage) {
            ChatMessage message = event.getChatMessage();

            if ((message.isOutgoing() && message.getState() != ChatMessage.State.Displayed)
                    || (!message.isOutgoing() && message.isFileTransfer())) {
                if (!mTransientMessages.contains(message)) {
                    mTransientMessages.add(message);
                }
                // This only works if JAVA object is kept, hence the transient list
                message.setUserData(holder);
                message.addListener(mListener);
            }

            LinphoneContact contact = null;
            Address remoteSender = message.getFromAddress();
            if (!message.isOutgoing()) {
                for (LinphoneContact c : mParticipants) {
                    if (c != null && c.hasAddress(remoteSender.asStringUriOnly())) {
                        contact = c;
                        break;
                    }
                }
            }
            holder.bindMessage(message, contact);
            changeBackgroundDependingOnPreviousAndNextEvents(message, holder, position);
        } else { // Event is not chat message
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
                    holder.eventLayout.setVisibility(View.VISIBLE);
                    holder.eventMessage.setText(mContext.getString(R.string.conference_created));
                    break;
                case ConferenceTerminated:
                    holder.eventLayout.setVisibility(View.VISIBLE);
                    holder.eventMessage.setText(mContext.getString(R.string.conference_destroyed));
                    break;
                case ConferenceParticipantAdded:
                    holder.eventLayout.setVisibility(View.VISIBLE);
                    holder.eventMessage.setText(
                            mContext.getString(R.string.participant_added)
                                    .replace("%s", displayName));
                    break;
                case ConferenceParticipantRemoved:
                    holder.eventLayout.setVisibility(View.VISIBLE);
                    holder.eventMessage.setText(
                            mContext.getString(R.string.participant_removed)
                                    .replace("%s", displayName));
                    break;
                case ConferenceSubjectChanged:
                    holder.eventLayout.setVisibility(View.VISIBLE);
                    holder.eventMessage.setText(
                            mContext.getString(R.string.subject_changed)
                                    .replace("%s", event.getSubject()));
                    break;
                case ConferenceParticipantSetAdmin:
                    holder.eventLayout.setVisibility(View.VISIBLE);
                    holder.eventMessage.setText(
                            mContext.getString(R.string.admin_set).replace("%s", displayName));
                    break;
                case ConferenceParticipantUnsetAdmin:
                    holder.eventLayout.setVisibility(View.VISIBLE);
                    holder.eventMessage.setText(
                            mContext.getString(R.string.admin_unset).replace("%s", displayName));
                    break;
                case ConferenceParticipantDeviceAdded:
                    holder.eventLayout.setVisibility(View.VISIBLE);
                    holder.eventMessage.setText(
                            mContext.getString(R.string.device_added).replace("%s", displayName));
                    break;
                case ConferenceParticipantDeviceRemoved:
                    holder.eventLayout.setVisibility(View.VISIBLE);
                    holder.eventMessage.setText(
                            mContext.getString(R.string.device_removed).replace("%s", displayName));
                    break;
                case ConferenceSecurityEvent:
                    holder.securityEventLayout.setVisibility(View.VISIBLE);

                    switch (event.getSecurityEventType()) {
                        case EncryptionIdentityKeyChanged:
                            holder.securityEventMessage.setText(
                                    mContext.getString(R.string.lime_identity_key_changed)
                                            .replace("%s", displayName));
                            break;
                        case ManInTheMiddleDetected:
                            holder.securityEventMessage.setText(
                                    mContext.getString(R.string.man_in_the_middle_detected)
                                            .replace("%s", displayName));
                            break;
                        case SecurityLevelDowngraded:
                            holder.securityEventMessage.setText(
                                    mContext.getString(R.string.security_level_downgraded)
                                            .replace("%s", displayName));
                            break;
                        case ParticipantMaxDeviceCountExceeded:
                            holder.securityEventMessage.setText(
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
                    holder.eventLayout.setVisibility(View.VISIBLE);
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
        notifyItemChanged(1); // Update second to last item just in case for grouping purposes
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
        mTransientMessages.clear();
        mHistory.clear();
    }

    public Object getItem(int i) {
        return mHistory.get(i);
    }

    public void removeItem(int i) {
        mHistory.remove(i);
        notifyItemRemoved(i);
    }

    private void changeBackgroundDependingOnPreviousAndNextEvents(
            ChatMessage message, ChatMessageViewHolder holder, int position) {
        boolean hasPrevious = false, hasNext = false;

        // Do not forget history is reversed, so previous in order is next in list display and
        // chronology !
        if (position > 0
                && mContext.getResources()
                        .getBoolean(R.bool.lower_space_between_chat_bubbles_if_same_person)) {
            EventLog previousEvent = (EventLog) getItem(position - 1);
            if (previousEvent.getType() == EventLog.Type.ConferenceChatMessage) {
                ChatMessage previousMessage = previousEvent.getChatMessage();
                if (previousMessage.getFromAddress().weakEqual(message.getFromAddress())) {
                    if (previousMessage.getTime() - message.getTime()
                            < MAX_TIME_TO_GROUP_MESSAGES) {
                        hasPrevious = true;
                    }
                }
            }
        }
        if (position >= 0
                && position < mHistory.size() - 1
                && mContext.getResources()
                        .getBoolean(R.bool.lower_space_between_chat_bubbles_if_same_person)) {
            EventLog nextEvent = (EventLog) getItem(position + 1);
            if (nextEvent.getType() == EventLog.Type.ConferenceChatMessage) {
                ChatMessage nextMessage = nextEvent.getChatMessage();
                if (nextMessage.getFromAddress().weakEqual(message.getFromAddress())) {
                    if (message.getTime() - nextMessage.getTime() < MAX_TIME_TO_GROUP_MESSAGES) {
                        holder.timeText.setVisibility(View.GONE);
                        if (!message.isOutgoing()) {
                            holder.avatarLayout.setVisibility(View.INVISIBLE);
                        }
                        hasNext = true;
                    }
                }
            }
        }

        if (message.isOutgoing()) {
            if (hasNext && hasPrevious) {
                holder.background.setBackgroundResource(R.drawable.chat_bubble_outgoing_split_2);
            } else if (hasNext) {
                holder.background.setBackgroundResource(R.drawable.chat_bubble_outgoing_split_3);
            } else if (hasPrevious) {
                holder.background.setBackgroundResource(R.drawable.chat_bubble_outgoing_split_1);
            } else {
                holder.background.setBackgroundResource(R.drawable.chat_bubble_outgoing_full);
            }
        } else {
            if (hasNext && hasPrevious) {
                holder.background.setBackgroundResource(R.drawable.chat_bubble_incoming_split_2);
            } else if (hasNext) {
                holder.background.setBackgroundResource(R.drawable.chat_bubble_incoming_split_3);
            } else if (hasPrevious) {
                holder.background.setBackgroundResource(R.drawable.chat_bubble_incoming_split_1);
            } else {
                holder.background.setBackgroundResource(R.drawable.chat_bubble_incoming_full);
            }
        }
    }
}
