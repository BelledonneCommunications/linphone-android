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
import android.widget.ExpandableListView;
import android.widget.ImageView;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.Address;
import org.linphone.core.ChatRoom;
import org.linphone.core.Core;
import org.linphone.core.Factory;
import org.linphone.core.Participant;
import org.linphone.core.ParticipantDevice;
import org.linphone.utils.LinphoneUtils;

public class DevicesFragment extends Fragment {
    private ExpandableListView mExpandableList;
    private DevicesAdapter mAdapter;

    private Address mLocalSipAddr, mRoomAddr;
    private ChatRoom mRoom;
    private boolean mOnlyDisplayChilds;

    @Nullable
    @Override
    public View onCreateView(
            LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            String localSipUri = getArguments().getString("LocalSipUri");
            mLocalSipAddr = Factory.instance().createAddress(localSipUri);
            String roomUri = getArguments().getString("RemoteSipUri");
            mRoomAddr = Factory.instance().createAddress(roomUri);
        }

        View view = inflater.inflate(R.layout.chat_devices, container, false);

        mOnlyDisplayChilds = false;

        mExpandableList = view.findViewById(R.id.devices_list);
        mExpandableList.setOnChildClickListener(
                new ExpandableListView.OnChildClickListener() {
                    @Override
                    public boolean onChildClick(
                            ExpandableListView expandableListView,
                            View view,
                            int groupPosition,
                            int childPosition,
                            long l) {
                        ParticipantDevice device =
                                (ParticipantDevice) mAdapter.getChild(groupPosition, childPosition);
                        LinphoneManager.getCallManager().inviteAddress(device.getAddress(), true);
                        return false;
                    }
                });
        mExpandableList.setOnGroupClickListener(
                new ExpandableListView.OnGroupClickListener() {
                    @Override
                    public boolean onGroupClick(
                            ExpandableListView expandableListView,
                            View view,
                            int groupPosition,
                            long l) {
                        if (mOnlyDisplayChilds) {
                            // in this case groups are childs, so call on click
                            ParticipantDevice device =
                                    (ParticipantDevice) mAdapter.getGroup(groupPosition);
                            LinphoneManager.getCallManager()
                                    .inviteAddress(device.getAddress(), true);
                            return true;
                        } else {
                            if (mAdapter.getChildrenCount(groupPosition) == 1) {
                                ParticipantDevice device =
                                        (ParticipantDevice) mAdapter.getChild(groupPosition, 0);
                                LinphoneManager.getCallManager()
                                        .inviteAddress(device.getAddress(), true);
                                return true;
                            }
                        }
                        return false;
                    }
                });

        initChatRoom();

        ImageView backButton = view.findViewById(R.id.back);
        backButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        ((ChatActivity) getActivity()).goBack();
                    }
                });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        initValues();

        if (LinphoneManager.getInstance().hasLastCallSasBeenRejected()) {
            LinphoneManager.getInstance().lastCallSasRejected(false);
            LinphoneUtils.showTrustDeniedDialog(getActivity());
        }
    }

    private void initChatRoom() {
        Core core = LinphoneManager.getCore();
        mRoom = core.getChatRoom(mRoomAddr, mLocalSipAddr);
    }

    private void initValues() {
        if (mAdapter == null) {
            mAdapter = new DevicesAdapter(getActivity());
            mExpandableList.setAdapter(mAdapter);
        }
        if (mRoom == null) {
            initChatRoom();
        }

        if (mRoom != null && mRoom.getNbParticipants() > 0) {
            ArrayList<Participant> participantLists = new ArrayList<>();
            // Group position 0 is reserved for ME participant & devices
            participantLists.add(mRoom.getMe());
            for (Participant participant : mRoom.getParticipants()) {
                participantLists.add(participant);
            }
            mAdapter.updateListItems(participantLists);
        }
    }
}
