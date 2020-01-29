/*
 * Copyright (c) 2010-2019 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.chat;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.Arrays;
import java.util.List;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.activities.MainActivity;
import org.linphone.call.views.LinphoneLinearLayoutManager;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.ContactsUpdatedListener;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatRoom;
import org.linphone.core.ChatRoomListenerStub;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.EventLog;
import org.linphone.core.ProxyConfig;
import org.linphone.utils.LinphoneUtils;
import org.linphone.utils.SelectableHelper;

public class ChatRoomsFragment extends Fragment
        implements ContactsUpdatedListener,
                ChatRoomViewHolder.ClickListener,
                SelectableHelper.DeleteListener {

    private RecyclerView mChatRoomsList;
    private ImageView mNewGroupDiscussionButton;
    private ImageView mBackToCallButton;
    private ChatRoomsAdapter mChatRoomsAdapter;
    private CoreListenerStub mListener;
    private RelativeLayout mWaitLayout;
    private int mChatRoomDeletionPendingCount;
    private ChatRoomListenerStub mChatRoomListener;
    private SelectableHelper mSelectionHelper;
    private TextView mNoChatHistory;

    @Override
    public View onCreateView(
            final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        View view = inflater.inflate(R.layout.chatlist, container, false);

        mChatRoomsList = view.findViewById(R.id.chatList);
        mWaitLayout = view.findViewById(R.id.waitScreen);
        ImageView newDiscussionButton = view.findViewById(R.id.new_discussion);
        mNewGroupDiscussionButton = view.findViewById(R.id.new_group_discussion);
        mBackToCallButton = view.findViewById(R.id.back_in_call);
        mNoChatHistory = view.findViewById(R.id.noChatHistory);

        ChatRoom[] rooms = LinphoneManager.getCore().getChatRooms();
        List<ChatRoom> mRooms = Arrays.asList(rooms);

        mSelectionHelper = new SelectableHelper(view, this);
        mChatRoomsAdapter =
                new ChatRoomsAdapter(
                        getActivity(), R.layout.chatlist_cell, mRooms, this, mSelectionHelper);

        mChatRoomsList.setAdapter(mChatRoomsAdapter);
        mSelectionHelper.setAdapter(mChatRoomsAdapter);
        mSelectionHelper.setDialogMessage(R.string.chat_room_delete_dialog);

        LinearLayoutManager layoutManager = new LinphoneLinearLayoutManager(getActivity());
        mChatRoomsList.setLayoutManager(layoutManager);

        DividerItemDecoration dividerItemDecoration =
                new DividerItemDecoration(
                        mChatRoomsList.getContext(), layoutManager.getOrientation());
        dividerItemDecoration.setDrawable(
                getActivity()
                        .getApplicationContext()
                        .getResources()
                        .getDrawable(R.drawable.divider));
        mChatRoomsList.addItemDecoration(dividerItemDecoration);

        mWaitLayout.setVisibility(View.GONE);

        newDiscussionButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ((ChatActivity) getActivity())
                                .showChatRoomCreation(null, null, null, false, false, false);
                    }
                });

        mNewGroupDiscussionButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ((ChatActivity) getActivity())
                                .showChatRoomCreation(null, null, null, false, true, false);
                    }
                });

        mBackToCallButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ((MainActivity) getActivity()).goBackToCall();
                    }
                });

        mListener =
                new CoreListenerStub() {
                    @Override
                    public void onMessageSent(Core core, ChatRoom room, ChatMessage message) {
                        refreshChatRoom(room);
                    }

                    @Override
                    public void onMessageReceived(Core core, ChatRoom room, ChatMessage message) {
                        refreshChatRoom(room);
                    }

                    @Override
                    public void onChatRoomSubjectChanged(Core core, ChatRoom room) {
                        refreshChatRoom(room);
                    }

                    @Override
                    public void onMessageReceivedUnableDecrypt(
                            Core core, ChatRoom room, ChatMessage message) {
                        refreshChatRoom(room);
                    }

                    @Override
                    public void onChatRoomEphemeralMessageDeleted(Core lc, ChatRoom cr) {
                        refreshChatRoom(cr);
                    }

                    @Override
                    public void onChatRoomRead(Core core, ChatRoom room) {
                        refreshChatRoom(room);
                    }

                    @Override
                    public void onChatRoomStateChanged(
                            Core core, ChatRoom room, ChatRoom.State state) {
                        if (state == ChatRoom.State.Created) {
                            refreshChatRoom(room);
                            scrollToTop();
                        }
                    }
                };

        mChatRoomListener =
                new ChatRoomListenerStub() {
                    @Override
                    public void onStateChanged(ChatRoom room, ChatRoom.State state) {
                        super.onStateChanged(room, state);
                        if (state == ChatRoom.State.Deleted
                                || state == ChatRoom.State.TerminationFailed) {
                            mChatRoomDeletionPendingCount -= 1;

                            if (state == ChatRoom.State.TerminationFailed) {
                                // TODO error message
                            }

                            if (mChatRoomDeletionPendingCount == 0) {
                                mWaitLayout.setVisibility(View.GONE);
                                refreshChatRoomsList();
                            }
                        }
                    }
                };

        return view;
    }

    @Override
    public void onItemClicked(int position) {
        if (mChatRoomsAdapter.isEditionEnabled()) {
            mChatRoomsAdapter.toggleSelection(position);
        } else {
            ChatRoom room = (ChatRoom) mChatRoomsAdapter.getItem(position);
            if (room != null) {
                ((ChatActivity) getActivity())
                        .showChatRoom(room.getLocalAddress(), room.getPeerAddress());
                refreshChatRoom(room);
            }
        }
    }

    @Override
    public boolean onItemLongClicked(int position) {
        if (!mChatRoomsAdapter.isEditionEnabled()) {
            mSelectionHelper.enterEditionMode();
        }
        mChatRoomsAdapter.toggleSelection(position);
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        ContactsManager.getInstance().addContactsListener(this);

        mBackToCallButton.setVisibility(View.INVISIBLE);
        Core core = LinphoneManager.getCore();
        if (core != null) {
            core.addListener(mListener);

            if (core.getCallsNb() > 0) {
                mBackToCallButton.setVisibility(View.VISIBLE);
            }
        }

        refreshChatRoomsList();

        ProxyConfig lpc = core.getDefaultProxyConfig();
        mNewGroupDiscussionButton.setVisibility(
                (lpc != null && lpc.getConferenceFactoryUri() != null) ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onPause() {
        Core core = LinphoneManager.getCore();
        if (core != null) {
            core.removeListener(mListener);
        }
        ContactsManager.getInstance().removeContactsListener(this);
        super.onPause();
    }

    @Override
    public void onDeleteSelection(Object[] objectsToDelete) {
        Core core = LinphoneManager.getCore();
        mChatRoomDeletionPendingCount = objectsToDelete.length;
        for (Object obj : objectsToDelete) {
            ChatRoom room = (ChatRoom) obj;
            room.addListener(mChatRoomListener);

            for (EventLog eventLog : room.getHistoryMessageEvents(0)) {
                LinphoneUtils.deleteFileContentIfExists(eventLog);
            }

            core.deleteChatRoom(room);
        }
        if (mChatRoomDeletionPendingCount > 0) {
            mWaitLayout.setVisibility(View.VISIBLE);
        }
        ((ChatActivity) getActivity()).displayMissedChats();

        if (getResources().getBoolean(R.bool.isTablet))
            ((ChatActivity) getActivity()).showEmptyChildFragment();
    }

    @Override
    public void onContactsUpdated() {
        ChatRoomsAdapter adapter = (ChatRoomsAdapter) mChatRoomsList.getAdapter();
        if (adapter != null) {
            adapter.refresh(true);
        }
    }

    private void scrollToTop() {
        mChatRoomsList.getLayoutManager().scrollToPosition(0);
    }

    private void refreshChatRoom(ChatRoom cr) {
        ChatRoomViewHolder holder = (ChatRoomViewHolder) cr.getUserData();
        if (holder != null) {
            int position = holder.getAdapterPosition();
            if (position == 0) {
                mChatRoomsAdapter.notifyItemChanged(0);
            } else {
                refreshChatRoomsList();
            }
        } else {
            refreshChatRoomsList();
        }
    }

    private void refreshChatRoomsList() {
        mChatRoomsAdapter.refresh();
        mNoChatHistory.setVisibility(
                mChatRoomsAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }
}
