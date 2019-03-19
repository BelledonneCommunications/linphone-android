/*
InfoGroupChatFragment.java
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

import static android.content.Context.INPUT_METHOD_SERVICE;

import android.app.Dialog;
import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.contacts.ContactAddress;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatRoom;
import org.linphone.core.ChatRoomListener;
import org.linphone.core.ChatRoomListenerStub;
import org.linphone.core.ChatRoomParams;
import org.linphone.core.Core;
import org.linphone.core.EventLog;
import org.linphone.core.Participant;
import org.linphone.core.tools.Log;
import org.linphone.fragments.FragmentsAvailable;
import org.linphone.utils.LinphoneUtils;

public class GroupInfoFragment extends Fragment implements ChatRoomListener {
    private ImageView mBackButton, mConfirmButton, mAddParticipantsButton;
    private RelativeLayout mAddParticipantsLayout;
    private Address mGroupChatRoomAddress;
    private EditText mSubjectField;

    private RecyclerView mParticipantsList;

    private LinearLayout mLeaveGroupButton;
    private RelativeLayout mWaitLayout;
    private GroupInfoAdapter mAdapter;
    private boolean mIsAlreadyCreatedGroup;
    private boolean mIsEditionEnabled;
    private ArrayList<ContactAddress> mParticipants;
    private String mSubject;
    private ChatRoom mChatRoom, mTempChatRoom;
    private Dialog mAdminStateChangedDialog;
    private ChatRoomListenerStub mChatRoomCreationListener;
    private Bundle mShareInfos;
    private Context mContext;
    private LinearLayoutManager layoutManager;
    private boolean mIsEncryptionEnabled;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.chat_infos, container, false);

        if (getArguments() == null || getArguments().isEmpty()) {
            return null;
        }
        mContext = getActivity().getApplicationContext();

        mParticipants =
                (ArrayList<ContactAddress>) getArguments().getSerializable("ContactAddress");

        mGroupChatRoomAddress = null;
        mChatRoom = null;

        String address = getArguments().getString("groupChatRoomAddress");
        if (address != null && address.length() > 0) {
            mGroupChatRoomAddress = LinphoneManager.getLc().createAddress(address);
        }
        mIsAlreadyCreatedGroup = mGroupChatRoomAddress != null;
        if (mIsAlreadyCreatedGroup) {
            mChatRoom = LinphoneManager.getLc().getChatRoom(mGroupChatRoomAddress);
        }
        if (mChatRoom == null) mIsAlreadyCreatedGroup = false;

        mIsEditionEnabled = getArguments().getBoolean("isEditionEnabled");
        mSubject = getArguments().getString("subject");

        if (mChatRoom != null && mChatRoom.hasBeenLeft()) {
            mIsEditionEnabled = false;
        }

        mIsEncryptionEnabled = getArguments().getBoolean("encryptionEnabled", false);

        mParticipantsList = view.findViewById(R.id.chat_room_participants);
        mAdapter = new GroupInfoAdapter(mParticipants, !mIsEditionEnabled, !mIsAlreadyCreatedGroup);
        mAdapter.setOnDeleteClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        ContactAddress ca = (ContactAddress) view.getTag();
                        mParticipants.remove(ca);
                        mAdapter.updateDataSet(mParticipants);
                        mParticipantsList.setAdapter(mAdapter);
                        mConfirmButton.setEnabled(
                                mSubjectField.getText().length() > 0 && mParticipants.size() > 0);
                    }
                });
        mParticipantsList.setAdapter(mAdapter);
        mAdapter.setChatRoom(mChatRoom);
        layoutManager = new LinearLayoutManager(mContext);
        mParticipantsList.setLayoutManager(layoutManager);

        // Divider between items
        DividerItemDecoration dividerItemDecoration =
                new DividerItemDecoration(
                        mParticipantsList.getContext(), layoutManager.getOrientation());
        dividerItemDecoration.setDrawable(mContext.getResources().getDrawable(R.drawable.divider));
        mParticipantsList.addItemDecoration(dividerItemDecoration);

        String fileSharedUri = getArguments().getString("fileSharedUri");
        String messageDraft = getArguments().getString("messageDraft");

        if (fileSharedUri != null || messageDraft != null) {
            Log.i("[GroupInfo] Forwarding arguments to group chat room");
            mShareInfos = new Bundle();
        }

        if (fileSharedUri != null) mShareInfos.putString("fileSharedUri", fileSharedUri);

        if (messageDraft != null) mShareInfos.putString("messageDraft", messageDraft);

        mBackButton = view.findViewById(R.id.back);
        mBackButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (mIsAlreadyCreatedGroup) {
                            if (LinphoneActivity.instance().isTablet()) {
                                LinphoneActivity.instance()
                                        .goToChat(
                                                mChatRoom.getLocalAddress().asStringUriOnly(),
                                                mGroupChatRoomAddress.asStringUriOnly(),
                                                mShareInfos);
                            } else {
                                getFragmentManager().popBackStack();
                            }
                        } else {
                            LinphoneActivity.instance()
                                    .goToChatCreator(
                                            null,
                                            mParticipants,
                                            null,
                                            true,
                                            mShareInfos,
                                            true,
                                            mIsEncryptionEnabled);
                        }
                    }
                });

        mConfirmButton = view.findViewById(R.id.confirm);

        mLeaveGroupButton = view.findViewById(R.id.leaveGroupLayout);
        mLeaveGroupButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        final Dialog dialog =
                                LinphoneActivity.instance()
                                        .displayDialog(getString(R.string.chat_room_leave_dialog));
                        Button delete = dialog.findViewById(R.id.dialog_delete_button);
                        delete.setText(getString(R.string.chat_room_leave_button));
                        Button cancel = dialog.findViewById(R.id.dialog_cancel_button);

                        delete.setOnClickListener(
                                new View.OnClickListener() {
                                    @Override
                                    public void onClick(View view) {
                                        if (mChatRoom != null) {
                                            mChatRoom.leave();
                                            LinphoneActivity.instance()
                                                    .goToChat(
                                                            mChatRoom
                                                                    .getLocalAddress()
                                                                    .asStringUriOnly(),
                                                            mGroupChatRoomAddress.asString(),
                                                            null);
                                        } else {
                                            Log.e(
                                                    "Can't leave, chatRoom for address "
                                                            + mGroupChatRoomAddress.asString()
                                                            + " is null...");
                                        }
                                        dialog.dismiss();
                                    }
                                });

                        cancel.setOnClickListener(
                                new View.OnClickListener() {
                                    @Override
                                    public void onClick(View view) {
                                        dialog.dismiss();
                                    }
                                });
                        dialog.show();
                    }
                });
        mLeaveGroupButton.setVisibility(
                mIsAlreadyCreatedGroup && mChatRoom.hasBeenLeft()
                        ? View.GONE
                        : mIsAlreadyCreatedGroup ? View.VISIBLE : View.GONE);

        mAddParticipantsLayout = view.findViewById(R.id.addParticipantsLayout);
        mAddParticipantsLayout.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (mIsEditionEnabled && mIsAlreadyCreatedGroup) {
                            LinphoneActivity.instance()
                                    .goToChatCreator(
                                            mGroupChatRoomAddress != null
                                                    ? mGroupChatRoomAddress.asString()
                                                    : null,
                                            mParticipants,
                                            mSubject,
                                            !mIsAlreadyCreatedGroup,
                                            null,
                                            true,
                                            mIsEncryptionEnabled);
                        }
                    }
                });
        mAddParticipantsButton = view.findViewById(R.id.addParticipants);
        mAddParticipantsButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (mIsEditionEnabled && mIsAlreadyCreatedGroup) {
                            LinphoneActivity.instance()
                                    .goToChatCreator(
                                            mGroupChatRoomAddress != null
                                                    ? mGroupChatRoomAddress.asString()
                                                    : null,
                                            mParticipants,
                                            mSubject,
                                            !mIsAlreadyCreatedGroup,
                                            null,
                                            true,
                                            mIsEncryptionEnabled);
                        }
                    }
                });
        mAddParticipantsButton.setVisibility(mIsAlreadyCreatedGroup ? View.VISIBLE : View.GONE);

        mSubjectField = view.findViewById(R.id.subjectField);
        mSubjectField.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence charSequence, int i, int i1, int i2) {}

                    @Override
                    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

                    @Override
                    public void afterTextChanged(Editable editable) {
                        mConfirmButton.setEnabled(
                                mSubjectField.getText().length() > 0 && mParticipants.size() > 0);
                    }
                });
        mSubjectField.setText(mSubject);

        mChatRoomCreationListener =
                new ChatRoomListenerStub() {
                    @Override
                    public void onStateChanged(ChatRoom cr, ChatRoom.State newState) {
                        if (newState == ChatRoom.State.Created) {
                            mWaitLayout.setVisibility(View.GONE);
                            // This will remove both the creation fragment and the group info
                            // fragment from the back stack
                            getFragmentManager().popBackStack();
                            getFragmentManager().popBackStack();
                            LinphoneActivity.instance()
                                    .goToChat(
                                            cr.getLocalAddress().asStringUriOnly(),
                                            cr.getPeerAddress().asStringUriOnly(),
                                            mShareInfos);
                        } else if (newState == ChatRoom.State.CreationFailed) {
                            mWaitLayout.setVisibility(View.GONE);
                            LinphoneActivity.instance().displayChatRoomError();
                            Log.e(
                                    "Group chat room for address "
                                            + cr.getPeerAddress()
                                            + " has failed !");
                        }
                    }
                };

        mConfirmButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (!mIsAlreadyCreatedGroup) {
                            mWaitLayout.setVisibility(View.VISIBLE);
                            Core core = LinphoneManager.getLc();

                            int i = 0;
                            Address[] participants = new Address[mParticipants.size()];
                            for (ContactAddress ca : mParticipants) {
                                participants[i] = ca.getAddress();
                                i++;
                            }

                            ChatRoomParams params = core.createDefaultChatRoomParams();
                            params.enableEncryption(mIsEncryptionEnabled);
                            params.enableGroup(true);

                            mTempChatRoom =
                                    core.createChatRoom(
                                            params,
                                            mSubjectField.getText().toString(),
                                            participants);
                            if (mTempChatRoom != null) {
                                mTempChatRoom.addListener(mChatRoomCreationListener);
                            } else {
                                Log.w("[Group Info Fragment] createChatRoom returned null...");
                                mWaitLayout.setVisibility(View.GONE);
                            }
                        } else {
                            // Subject
                            String newSubject = mSubjectField.getText().toString();
                            if (!newSubject.equals(mSubject)) {
                                mChatRoom.setSubject(newSubject);
                            }

                            // Participants removed
                            ArrayList<Participant> toRemove = new ArrayList<>();
                            for (Participant p : mChatRoom.getParticipants()) {
                                boolean found = false;
                                for (ContactAddress c : mParticipants) {
                                    if (c.getAddress().weakEqual(p.getAddress())) {
                                        found = true;
                                        break;
                                    }
                                }
                                if (!found) {
                                    toRemove.add(p);
                                }
                            }
                            Participant[] participantsToRemove = new Participant[toRemove.size()];
                            toRemove.toArray(participantsToRemove);
                            mChatRoom.removeParticipants(participantsToRemove);

                            // Participants added
                            ArrayList<Address> toAdd = new ArrayList<>();
                            for (ContactAddress c : mParticipants) {
                                boolean found = false;
                                for (Participant p : mChatRoom.getParticipants()) {
                                    if (p.getAddress().weakEqual(c.getAddress())) {
                                        // Admin rights
                                        if (c.isAdmin() != p.isAdmin()) {
                                            mChatRoom.setParticipantAdminStatus(p, c.isAdmin());
                                        }
                                        found = true;
                                        break;
                                    }
                                }
                                if (!found) {
                                    Address addr = c.getAddress();
                                    if (addr != null) {
                                        toAdd.add(addr);
                                    } else {
                                        // TODO error
                                    }
                                }
                            }
                            Address[] participantsToAdd = new Address[toAdd.size()];
                            toAdd.toArray(participantsToAdd);
                            mChatRoom.addParticipants(participantsToAdd);

                            LinphoneActivity.instance()
                                    .goToChat(
                                            mChatRoom.getLocalAddress().asStringUriOnly(),
                                            mGroupChatRoomAddress.asString(),
                                            null);
                        }
                    }
                });
        mConfirmButton.setEnabled(mSubjectField.getText().length() > 0 && mParticipants.size() > 0);

        if (!mIsEditionEnabled) {
            mSubjectField.setEnabled(false);
            mConfirmButton.setVisibility(View.INVISIBLE);
            mAddParticipantsButton.setVisibility(View.GONE);
        }

        mWaitLayout = view.findViewById(R.id.waitScreen);
        mWaitLayout.setVisibility(View.GONE);

        if (mChatRoom != null) {
            mChatRoom.addListener(this);
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        if (LinphoneActivity.isInstanciated()) {
            LinphoneActivity.instance().selectMenu(FragmentsAvailable.INFO_GROUP_CHAT);
        }

        InputMethodManager inputMethodManager =
                (InputMethodManager) getActivity().getSystemService(INPUT_METHOD_SERVICE);
        if (getActivity().getCurrentFocus() != null) {
            inputMethodManager.hideSoftInputFromWindow(
                    getActivity().getCurrentFocus().getWindowToken(), 0);
        }
    }

    @Override
    public void onPause() {
        if (mTempChatRoom != null) {
            mTempChatRoom.removeListener(mChatRoomCreationListener);
        }
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if (mChatRoom != null) {
            mChatRoom.removeListener(this);
        }
        super.onDestroy();
    }

    private void refreshParticipantsList() {
        if (mChatRoom == null) return;
        mParticipants = new ArrayList<>();
        for (Participant p : mChatRoom.getParticipants()) {
            Address a = p.getAddress();
            LinphoneContact c = ContactsManager.getInstance().findContactFromAddress(a);
            if (c == null) {
                c = new LinphoneContact();
                String displayName = LinphoneUtils.getAddressDisplayName(a);
                c.setFullName(displayName);
            }
            ContactAddress ca = new ContactAddress(c, a.asString(), "", c.isFriend(), p.isAdmin());
            mParticipants.add(ca);
        }

        mAdapter.updateDataSet(mParticipants);
        mAdapter.setChatRoom(mChatRoom);
    }

    private void refreshAdminRights() {
        mAdapter.setAdminFeaturesVisible(mIsEditionEnabled);
        mAdapter.setChatRoom(mChatRoom);
        mSubjectField.setEnabled(mIsEditionEnabled);
        mConfirmButton.setVisibility(mIsEditionEnabled ? View.VISIBLE : View.INVISIBLE);
        mAddParticipantsButton.setVisibility(mIsEditionEnabled ? View.VISIBLE : View.GONE);
    }

    private void displayMeAdminStatusUpdated() {
        if (mAdminStateChangedDialog != null) mAdminStateChangedDialog.dismiss();

        mAdminStateChangedDialog =
                LinphoneActivity.instance()
                        .displayDialog(
                                getString(
                                        mIsEditionEnabled
                                                ? R.string.chat_room_you_are_now_admin
                                                : R.string.chat_room_you_are_no_longer_admin));
        Button cancel = mAdminStateChangedDialog.findViewById(R.id.dialog_cancel_button);
        mAdminStateChangedDialog.findViewById(R.id.dialog_delete_button).setVisibility(View.GONE);
        cancel.setText(getString(R.string.ok));
        cancel.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        mAdminStateChangedDialog.dismiss();
                    }
                });

        mAdminStateChangedDialog.show();
    }

    @Override
    public void onParticipantAdminStatusChanged(ChatRoom cr, EventLog event_log) {
        if (mChatRoom.getMe().isAdmin() != mIsEditionEnabled) {
            // Either we weren't admin and we are now or the other way around
            mIsEditionEnabled = mChatRoom.getMe().isAdmin();
            displayMeAdminStatusUpdated();
            refreshAdminRights();
        }
        refreshParticipantsList();
    }

    @Override
    public void onSubjectChanged(ChatRoom cr, EventLog event_log) {
        mSubjectField.setText(event_log.getSubject());
    }

    @Override
    public void onConferenceJoined(ChatRoom cr, EventLog event_log) {}

    @Override
    public void onConferenceLeft(ChatRoom cr, EventLog event_log) {}

    @Override
    public void onParticipantAdded(ChatRoom cr, EventLog event_log) {
        refreshParticipantsList();
    }

    @Override
    public void onParticipantRemoved(ChatRoom cr, EventLog event_log) {
        refreshParticipantsList();
    }

    @Override
    public void onChatMessageShouldBeStored(ChatRoom cr, ChatMessage msg) {}

    @Override
    public void onIsComposingReceived(ChatRoom cr, Address remoteAddr, boolean isComposing) {}

    @Override
    public void onChatMessageSent(ChatRoom cr, EventLog event_log) {}

    @Override
    public void onConferenceAddressGeneration(ChatRoom cr) {}

    @Override
    public void onChatMessageReceived(ChatRoom cr, EventLog event_log) {}

    @Override
    public void onMessageReceived(ChatRoom cr, ChatMessage msg) {}

    @Override
    public void onParticipantDeviceRemoved(ChatRoom cr, EventLog event_log) {}

    @Override
    public void onParticipantDeviceAdded(ChatRoom cr, EventLog event_log) {}

    @Override
    public void onSecurityEvent(ChatRoom cr, EventLog eventLog) {
        refreshParticipantsList();
    }

    @Override
    public void onUndecryptableMessageReceived(ChatRoom cr, ChatMessage msg) {}

    @Override
    public void onStateChanged(ChatRoom cr, ChatRoom.State newState) {}

    @Override
    public void onParticipantRegistrationSubscriptionRequested(
            ChatRoom cr, Address participantAddr) {}

    @Override
    public void onParticipantRegistrationUnsubscriptionRequested(
            ChatRoom cr, Address participantAddr) {}
}
