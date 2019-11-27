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

import static android.content.Context.INPUT_METHOD_SERVICE;

import android.app.Dialog;
import android.app.Fragment;
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
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.call.views.LinphoneLinearLayoutManager;
import org.linphone.contacts.ContactAddress;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.ChatRoom;
import org.linphone.core.ChatRoomCapabilities;
import org.linphone.core.ChatRoomListenerStub;
import org.linphone.core.ChatRoomParams;
import org.linphone.core.Core;
import org.linphone.core.EventLog;
import org.linphone.core.Factory;
import org.linphone.core.Participant;
import org.linphone.core.tools.Log;
import org.linphone.utils.LinphoneUtils;

public class GroupInfoFragment extends Fragment {
    private ImageView mConfirmButton;
    private ImageView mAddParticipantsButton;
    private Address mGroupChatRoomAddress;
    private EditText mSubjectField;

    private RecyclerView mParticipantsList;

    private RelativeLayout mWaitLayout;
    private GroupInfoAdapter mAdapter;
    private boolean mIsAlreadyCreatedGroup;
    private boolean mIsEditionEnabled;
    private ArrayList<ContactAddress> mParticipants;
    private String mSubject;
    private ChatRoom mChatRoom, mTempChatRoom;
    private Dialog mAdminStateChangedDialog;
    private ChatRoomListenerStub mChatRoomCreationListener;
    private boolean mIsEncryptionEnabled;
    private ChatRoomListenerStub mListener;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.chat_infos, container, false);

        if (getArguments() == null || getArguments().isEmpty()) {
            return null;
        }

        mParticipants = (ArrayList<ContactAddress>) getArguments().getSerializable("Participants");
        mGroupChatRoomAddress = null;
        mChatRoom = null;

        String address = getArguments().getString("RemoteSipUri");
        if (address != null && address.length() > 0) {
            mGroupChatRoomAddress = Factory.instance().createAddress(address);
        }

        mIsAlreadyCreatedGroup = mGroupChatRoomAddress != null;
        if (mIsAlreadyCreatedGroup) {
            mChatRoom = LinphoneManager.getCore().getChatRoom(mGroupChatRoomAddress);
        }

        if (mChatRoom == null) {
            mIsAlreadyCreatedGroup = false;
            mIsEditionEnabled = true;
            mSubject = getArguments().getString("Subject", "");
            mIsEncryptionEnabled = getArguments().getBoolean("Encrypted", false);
        } else {
            mIsEditionEnabled =
                    mChatRoom.getMe() != null
                            && mChatRoom.getMe().isAdmin()
                            && !mChatRoom.hasBeenLeft();
            mSubject = mChatRoom.getSubject();
            mIsEncryptionEnabled = mChatRoom.hasCapability(ChatRoomCapabilities.Encrypted.toInt());
        }

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
        LinearLayoutManager layoutManager = new LinphoneLinearLayoutManager(getActivity());
        mParticipantsList.setLayoutManager(layoutManager);

        // Divider between items
        DividerItemDecoration dividerItemDecoration =
                new DividerItemDecoration(
                        mParticipantsList.getContext(), layoutManager.getOrientation());
        dividerItemDecoration.setDrawable(getResources().getDrawable(R.drawable.divider));
        mParticipantsList.addItemDecoration(dividerItemDecoration);

        ImageView backButton = view.findViewById(R.id.back);
        backButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (mIsAlreadyCreatedGroup) {
                            ((ChatActivity) getActivity()).goBack();
                        } else {
                            goBackToChatCreationFragment();
                        }
                    }
                });

        mConfirmButton = view.findViewById(R.id.confirm);
        mConfirmButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        applyChanges();
                    }
                });
        mConfirmButton.setEnabled(!mSubject.isEmpty() && mParticipants.size() > 0);

        LinearLayout leaveGroupButton = view.findViewById(R.id.leaveGroupLayout);
        leaveGroupButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        showLeaveGroupDialog();
                    }
                });
        leaveGroupButton.setVisibility(
                mIsAlreadyCreatedGroup && mChatRoom.hasBeenLeft()
                        ? View.GONE
                        : mIsAlreadyCreatedGroup ? View.VISIBLE : View.GONE);

        RelativeLayout addParticipantsLayout = view.findViewById(R.id.addParticipantsLayout);
        addParticipantsLayout.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (mIsEditionEnabled && mIsAlreadyCreatedGroup) {
                            goBackToChatCreationFragment();
                        }
                    }
                });
        mAddParticipantsButton = view.findViewById(R.id.addParticipants);
        mAddParticipantsButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (mIsEditionEnabled && mIsAlreadyCreatedGroup) {
                            goBackToChatCreationFragment();
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
                            // Pop the back stack twice so we don't have in stack Creation -> Group
                            // Behind the chat room, so a back press will take us back to the rooms
                            getFragmentManager().popBackStack();
                            getFragmentManager().popBackStack();
                            ((ChatActivity) getActivity())
                                    .showChatRoom(cr.getLocalAddress(), cr.getPeerAddress());
                        } else if (newState == ChatRoom.State.CreationFailed) {
                            mWaitLayout.setVisibility(View.GONE);
                            ((ChatActivity) getActivity()).displayChatRoomError();
                            Log.e(
                                    "[Group Info] Group chat room for address "
                                            + cr.getPeerAddress()
                                            + " has failed !");
                        }
                    }
                };

        if (!mIsEditionEnabled) {
            mSubjectField.setEnabled(false);
            mConfirmButton.setVisibility(View.INVISIBLE);
            mAddParticipantsButton.setVisibility(View.GONE);
        }

        mWaitLayout = view.findViewById(R.id.waitScreen);
        mWaitLayout.setVisibility(View.GONE);

        mListener =
                new ChatRoomListenerStub() {
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
                    public void onParticipantAdded(ChatRoom cr, EventLog eventLog) {
                        refreshParticipantsList();
                    }

                    @Override
                    public void onParticipantRemoved(ChatRoom cr, EventLog eventLog) {
                        refreshParticipantsList();
                    }
                };

        if (mChatRoom != null) {
            mChatRoom.addListener(mListener);
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

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
            mChatRoom.removeListener(mListener);
        }
        super.onDestroy();
    }

    private void goBackToChatCreationFragment() {
        ((ChatActivity) getActivity())
                .showChatRoomCreation(
                        mGroupChatRoomAddress,
                        mParticipants,
                        mSubject,
                        mIsEncryptionEnabled,
                        true,
                        !mIsAlreadyCreatedGroup);
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
            ContactAddress ca = new ContactAddress(c, a.asString(), "", p.isAdmin());
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
                ((ChatActivity) getActivity())
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

    private void showLeaveGroupDialog() {
        final Dialog dialog =
                ((ChatActivity) getActivity())
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
                            ((ChatActivity) getActivity())
                                    .showChatRoom(
                                            mChatRoom.getLocalAddress(),
                                            mChatRoom.getPeerAddress());
                        } else {
                            Log.e(
                                    "[Group Info] Can't leave, chatRoom for address "
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

    private void applyChanges() {
        if (!mIsAlreadyCreatedGroup) {
            mWaitLayout.setVisibility(View.VISIBLE);
            Core core = LinphoneManager.getCore();

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
                    core.createChatRoom(params, mSubjectField.getText().toString(), participants);
            if (mTempChatRoom != null) {
                mTempChatRoom.addListener(mChatRoomCreationListener);
            } else {
                Log.w("[Group Info] createChatRoom returned null...");
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
            if (!mChatRoom.addParticipants(participantsToAdd)) {
                // TODO error
            }
            // Pop back stack to go back to the Messages fragment
            getFragmentManager().popBackStack();
        }
    }
}
