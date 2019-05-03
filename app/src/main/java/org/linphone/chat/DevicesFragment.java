/*
DevicesFragment.java
Copyright (C) 2010-2018  Belledonne Communications, Grenoble, France

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

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import java.util.Arrays;
import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.call.CallManager;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.ChatRoom;
import org.linphone.core.ChatRoomCapabilities;
import org.linphone.core.Core;
import org.linphone.core.ParticipantDevice;
import org.linphone.fragments.FragmentsAvailable;
import org.linphone.utils.LinphoneUtils;

public class DevicesFragment extends Fragment {
    private LayoutInflater mInflater;
    private ImageView mBackButton;
    private TextView mTitle;
    private ExpandableListView mExpandableList;
    private DevicesAdapter mAdapter;

    private String mLocalSipUri, mRoomUri;
    private Address mLocalSipAddr, mRoomAddr;
    private ChatRoom mRoom;
    private boolean mOnlyDisplayChilds;

    @Nullable
    @Override
    public View onCreateView(
            LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            mLocalSipUri = getArguments().getString("LocalSipUri");
            mLocalSipAddr = LinphoneManager.getLc().createAddress(mLocalSipUri);
            mRoomUri = getArguments().getString("RemoteSipUri");
            mRoomAddr = LinphoneManager.getLc().createAddress(mRoomUri);
        }

        mInflater = inflater;
        View view = mInflater.inflate(R.layout.chat_devices, container, false);

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
                        CallManager.getInstance().inviteAddress(device.getAddress(), true);
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
                            CallManager.getInstance().inviteAddress(device.getAddress(), true);
                            return true;
                        } else {
                            if (mAdapter.getChildrenCount(groupPosition) == 1) {
                                ParticipantDevice device =
                                        (ParticipantDevice) mAdapter.getChild(groupPosition, 0);
                                CallManager.getInstance().inviteAddress(device.getAddress(), true);
                                return true;
                            }
                        }
                        return false;
                    }
                });

        initChatRoom();

        mTitle = view.findViewById(R.id.title);
        initHeader();

        mBackButton = view.findViewById(R.id.back);
        mBackButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (LinphoneActivity.instance().isTablet()) {
                            LinphoneActivity.instance().goToChat(mLocalSipUri, mRoomUri, null);
                        } else {
                            LinphoneActivity.instance().onBackPressed();
                        }
                    }
                });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        if (LinphoneActivity.isInstanciated()) {
            LinphoneActivity.instance().selectMenu(FragmentsAvailable.CONTACT_DEVICES);
        }

        initValues();

        if (LinphoneManager.getInstance().hasLastCallSasBeenRejected()) {
            LinphoneManager.getInstance().lastCallSasRejected(false);
            LinphoneUtils.showTrustDeniedDialog(getActivity());
        }
    }

    private void initChatRoom() {
        Core core = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        mRoom = core.getChatRoom(mRoomAddr, mLocalSipAddr);
    }

    private void initHeader() {
        if (mRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt())) {
            Address remoteParticipantAddr = mRoomAddr;
            if (mRoom.getParticipants().length > 0) {
                remoteParticipantAddr = mRoom.getParticipants()[0].getAddress();
            }
            LinphoneContact c =
                    ContactsManager.getInstance().findContactFromAddress(remoteParticipantAddr);
            String displayName;
            if (c != null) {
                displayName = c.getFullName();
            } else {
                displayName = LinphoneUtils.getAddressDisplayName(remoteParticipantAddr);
            }
            mTitle.setText(getString(R.string.chat_room_devices).replace("%s", displayName));
        }
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
            mOnlyDisplayChilds = mRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt());
            mAdapter.updateListItems(Arrays.asList(mRoom.getParticipants()), mOnlyDisplayChilds);
        }
    }
}
