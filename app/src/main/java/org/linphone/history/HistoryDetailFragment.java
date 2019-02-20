package org.linphone.history;

/*
HistoryDetailFragment.java
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

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.ChatRoom;
import org.linphone.core.ChatRoomListenerStub;
import org.linphone.core.Core;
import org.linphone.core.Factory;
import org.linphone.core.ProxyConfig;
import org.linphone.core.tools.Log;
import org.linphone.fragments.FragmentsAvailable;
import org.linphone.settings.LinphonePreferences;
import org.linphone.utils.ImageUtils;
import org.linphone.utils.LinphoneUtils;

public class HistoryDetailFragment extends Fragment implements OnClickListener {
    private ImageView mDialBack, mChat, mAddToContacts, mGoToContact, mBack;
    private View mView;
    private ImageView mContactPicture, mCallDirection;
    private TextView mContactName, mContactAddress, mTime, mDate;
    private String mSipUri, mDisplayName;
    private RelativeLayout mWaitLayout;
    private LinphoneContact mContact;
    private ChatRoom mChatRoom;
    private ChatRoomListenerStub mChatRoomCreationListener;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mSipUri = getArguments().getString("SipUri");
        mDisplayName = getArguments().getString("DisplayName");
        String status = getArguments().getString("Call.Status");
        String callTime = getArguments().getString("CallTime");
        String callDate = getArguments().getString("CallDate");

        mView = inflater.inflate(R.layout.history_detail, container, false);

        mWaitLayout = mView.findViewById(R.id.waitScreen);
        mWaitLayout.setVisibility(View.GONE);

        mDialBack = mView.findViewById(R.id.call);
        mDialBack.setOnClickListener(this);

        mBack = mView.findViewById(R.id.back);
        if (getResources().getBoolean(R.bool.isTablet)) {
            mBack.setVisibility(View.INVISIBLE);
        } else {
            mBack.setOnClickListener(this);
        }

        mChat = mView.findViewById(R.id.chat);
        mChat.setOnClickListener(this);
        if (getResources().getBoolean(R.bool.disable_chat))
            mView.findViewById(R.id.chat).setVisibility(View.GONE);

        mAddToContacts = mView.findViewById(R.id.add_contact);
        mAddToContacts.setOnClickListener(this);

        mGoToContact = mView.findViewById(R.id.goto_contact);
        mGoToContact.setOnClickListener(this);

        mContactPicture = mView.findViewById(R.id.contact_picture);

        mContactName = mView.findViewById(R.id.contact_name);
        mContactAddress = mView.findViewById(R.id.contact_address);

        mCallDirection = mView.findViewById(R.id.direction);

        mTime = mView.findViewById(R.id.time);
        mDate = mView.findViewById(R.id.date);

        displayHistory(status, callTime, callDate);

        mChatRoomCreationListener =
                new ChatRoomListenerStub() {
                    @Override
                    public void onStateChanged(ChatRoom cr, ChatRoom.State newState) {
                        if (newState == ChatRoom.State.Created) {
                            mWaitLayout.setVisibility(View.GONE);
                            LinphoneActivity.instance()
                                    .goToChat(
                                            cr.getLocalAddress().asStringUriOnly(),
                                            cr.getPeerAddress().asStringUriOnly(),
                                            null);
                        } else if (newState == ChatRoom.State.CreationFailed) {
                            mWaitLayout.setVisibility(View.GONE);
                            LinphoneActivity.instance().displayChatRoomError();
                            Log.e(
                                    "Group mChat room for address "
                                            + cr.getPeerAddress()
                                            + " has failed !");
                        }
                    }
                };

        return mView;
    }

    @Override
    public void onPause() {
        if (mChatRoom != null) {
            mChatRoom.removeListener(mChatRoomCreationListener);
        }
        super.onPause();
    }

    private void displayHistory(String status, String callTime, String callDate) {
        if (status.equals(getResources().getString(R.string.missed))) {
            mCallDirection.setImageResource(R.drawable.call_missed);
        } else if (status.equals(getResources().getString(R.string.incoming))) {
            mCallDirection.setImageResource(R.drawable.call_incoming);
        } else if (status.equals(getResources().getString(R.string.outgoing))) {
            mCallDirection.setImageResource(R.drawable.call_outgoing);
        }

        mTime.setText(callTime == null ? "" : callTime);
        Long longDate = Long.parseLong(callDate);
        mDate.setText(
                LinphoneUtils.timestampToHumanDate(
                        getActivity(), longDate, getString(R.string.history_detail_date_format)));

        Address lAddress = Factory.instance().createAddress(mSipUri);

        if (lAddress != null) {
            mContactAddress.setText(LinphoneUtils.getDisplayableAddress(lAddress));
            mContact = ContactsManager.getInstance().findContactFromAddress(lAddress);
            if (mContact != null) {
                mContactName.setText(mContact.getFullName());
                ImageUtils.setImagePictureFromUri(
                        mView.getContext(),
                        mContactPicture,
                        mContact.getPhotoUri(),
                        mContact.getThumbnailUri());
                mAddToContacts.setVisibility(View.GONE);
                mGoToContact.setVisibility(View.VISIBLE);
            } else {
                mContactName.setText(
                        mDisplayName == null
                                ? LinphoneUtils.getAddressDisplayName(mSipUri)
                                : mDisplayName);
                mContactPicture.setImageBitmap(
                        ContactsManager.getInstance().getDefaultAvatarBitmap());
                mAddToContacts.setVisibility(View.VISIBLE);
                mGoToContact.setVisibility(View.GONE);
            }
        } else {
            mContactAddress.setText(mSipUri);
            mContactName.setText(
                    mDisplayName == null
                            ? LinphoneUtils.getAddressDisplayName(mSipUri)
                            : mDisplayName);
        }
    }

    public void changeDisplayedHistory(
            String sipUri, String displayName, String status, String callTime, String callDate) {
        if (displayName == null) {
            displayName = LinphoneUtils.getUsernameFromAddress(sipUri);
        }

        mSipUri = sipUri;
        mDisplayName = displayName;
        displayHistory(status, callTime, callDate);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (LinphoneActivity.isInstanciated()) {
            LinphoneActivity.instance().selectMenu(FragmentsAvailable.HISTORY_DETAIL);
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();

        if (id == R.id.back) {
            getFragmentManager().popBackStackImmediate();
        }
        if (id == R.id.call) {
            LinphoneActivity.instance().setAddresGoToDialerAndCall(mSipUri, mDisplayName);
        } else if (id == R.id.chat) {
            Core lc = LinphoneManager.getLc();
            Address participant = Factory.instance().createAddress(mSipUri);
            ChatRoom room =
                    lc.findOneToOneChatRoom(
                            lc.getDefaultProxyConfig().getContact(), participant, false);
            if (room != null) {
                LinphoneActivity.instance()
                        .goToChat(
                                room.getLocalAddress().asStringUriOnly(),
                                room.getPeerAddress().asStringUriOnly(),
                                null);
            } else {
                ProxyConfig lpc = lc.getDefaultProxyConfig();
                if (lpc != null
                        && lpc.getConferenceFactoryUri() != null
                        && !LinphonePreferences.instance().useBasicChatRoomFor1To1()) {
                    mWaitLayout.setVisibility(View.VISIBLE);
                    mChatRoom =
                            lc.createClientGroupChatRoom(
                                    getString(R.string.dummy_group_chat_subject), true);
                    mChatRoom.addListener(mChatRoomCreationListener);
                    Address participants[] = new Address[1];
                    participants[0] = participant;
                    mChatRoom.addParticipants(participants);
                } else {
                    room = lc.getChatRoom(participant);
                    LinphoneActivity.instance()
                            .goToChat(
                                    room.getLocalAddress().asStringUriOnly(),
                                    room.getPeerAddress().asStringUriOnly(),
                                    null);
                }
            }
        } else if (id == R.id.add_contact) {
            Address addr = Factory.instance().createAddress(mSipUri);
            if (addr != null) {
                String address =
                        "sip:" + addr.getUsername() + "@" + addr.getDomain(); // Clean gruu param
                if (addr.getDisplayName() != null) {
                    LinphoneActivity.instance()
                            .displayContactsForEdition(address, addr.getDisplayName());
                } else {
                    LinphoneActivity.instance().displayContactsForEdition(address);
                }
            }
        } else if (id == R.id.goto_contact) {
            LinphoneActivity.instance().displayContact(mContact, false);
        }
    }
}
