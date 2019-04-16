package org.linphone.contacts;

/*
ContactDetailsFragment.java
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

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.Fragment;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TableLayout;
import android.widget.TextView;
import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.Address;
import org.linphone.core.ChatRoom;
import org.linphone.core.ChatRoomBackend;
import org.linphone.core.ChatRoomListenerStub;
import org.linphone.core.ChatRoomParams;
import org.linphone.core.Core;
import org.linphone.core.Factory;
import org.linphone.core.FriendCapability;
import org.linphone.core.PresenceBasicStatus;
import org.linphone.core.PresenceModel;
import org.linphone.core.ProxyConfig;
import org.linphone.core.tools.Log;
import org.linphone.fragments.FragmentsAvailable;
import org.linphone.settings.LinphonePreferences;
import org.linphone.utils.LinphoneUtils;
import org.linphone.views.ContactAvatar;

public class ContactDetailsFragment extends Fragment
        implements OnClickListener, ContactsUpdatedListener {
    private LinphoneContact mContact;
    private ImageView mEditContact, mDeleteContact, mBack;
    private TextView mOrganization;
    private RelativeLayout mWaitLayout;
    private LayoutInflater mInflater;
    private View mView;
    private boolean mDisplayChatAddressOnly = false;
    private ChatRoom mChatRoom;
    private ChatRoomListenerStub mChatRoomCreationListener;

    private final OnClickListener mDialListener =
            new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (LinphoneActivity.isInstanciated()) {
                        String tag = (String) v.getTag();
                        LinphoneActivity.instance()
                                .setAddresGoToDialerAndCall(tag, mContact.getFullName());
                    }
                }
            };

    private final OnClickListener mChatListener =
            new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (LinphoneActivity.isInstanciated()) {
                        String tag = (String) v.getTag();
                        Core lc = LinphoneManager.getLc();
                        Address participant = Factory.instance().createAddress(tag);
                        ProxyConfig defaultProxyConfig = lc.getDefaultProxyConfig();
                        boolean isSecured = v.getId() == R.id.contact_chat_secured;

                        if (defaultProxyConfig != null) {
                            ChatRoom room =
                                    lc.findOneToOneChatRoom(
                                            defaultProxyConfig.getContact(),
                                            participant,
                                            isSecured);
                            if (room != null) {
                                LinphoneActivity.instance()
                                        .goToChat(
                                                room.getLocalAddress().asStringUriOnly(),
                                                room.getPeerAddress().asStringUriOnly(),
                                                null);
                            } else {
                                if (defaultProxyConfig.getConferenceFactoryUri() != null
                                        && (isSecured
                                                || !LinphonePreferences.instance()
                                                        .useBasicChatRoomFor1To1())) {
                                    mWaitLayout.setVisibility(View.VISIBLE);

                                    ChatRoomParams params = lc.createDefaultChatRoomParams();
                                    params.enableEncryption(isSecured);
                                    params.enableGroup(false);
                                    // We don't want a basic chat room,
                                    // so if isSecured is false we have to set this manually
                                    params.setBackend(ChatRoomBackend.FlexisipChat);

                                    Address participants[] = new Address[1];
                                    participants[0] = participant;

                                    mChatRoom =
                                            lc.createChatRoom(
                                                    params,
                                                    getString(R.string.dummy_group_chat_subject),
                                                    participants);
                                    if (mChatRoom != null) {
                                        mChatRoom.addListener(mChatRoomCreationListener);
                                    } else {
                                        Log.w(
                                                "[Contact Details Fragment] createChatRoom returned null...");
                                        mWaitLayout.setVisibility(View.GONE);
                                    }
                                } else {
                                    room = lc.getChatRoom(participant);
                                    LinphoneActivity.instance()
                                            .goToChat(
                                                    room.getLocalAddress().asStringUriOnly(),
                                                    room.getPeerAddress().asStringUriOnly(),
                                                    null);
                                }
                            }
                        }
                    }
                }
            };

    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mContact = (LinphoneContact) getArguments().getSerializable("Contact");

        this.mInflater = inflater;
        mView = inflater.inflate(R.layout.contact, container, false);

        if (getArguments() != null) {
            mDisplayChatAddressOnly = getArguments().getBoolean("ChatAddressOnly");
        }

        mWaitLayout = mView.findViewById(R.id.waitScreen);
        mWaitLayout.setVisibility(View.GONE);

        mEditContact = mView.findViewById(R.id.editContact);
        mEditContact.setOnClickListener(this);

        mDeleteContact = mView.findViewById(R.id.deleteContact);
        mDeleteContact.setOnClickListener(this);

        mOrganization = mView.findViewById(R.id.contactOrganization);
        boolean isOrgVisible = getResources().getBoolean(R.bool.display_contact_organization);
        String org = mContact.getOrganization();
        if (org != null && !org.isEmpty() && isOrgVisible) {
            mOrganization.setText(org);
        } else {
            mOrganization.setVisibility(View.GONE);
        }

        mBack = mView.findViewById(R.id.back);
        if (getResources().getBoolean(R.bool.isTablet)) {
            mBack.setVisibility(View.INVISIBLE);
        } else {
            mBack.setOnClickListener(this);
        }

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
                                    "Group chat room for address "
                                            + cr.getPeerAddress()
                                            + " has failed !");
                        }
                    }
                };

        return mView;
    }

    public void changeDisplayedContact(LinphoneContact newContact) {
        mContact = newContact;
        displayContact(mInflater, mView);
    }

    @SuppressLint("InflateParams")
    private void displayContact(LayoutInflater inflater, View view) {
        ContactAvatar.displayAvatar(mContact, view.findViewById(R.id.avatar_layout));

        TextView contactName = view.findViewById(R.id.contact_name);
        contactName.setText(mContact.getFullName());
        mOrganization.setText(
                (mContact.getOrganization() != null) ? mContact.getOrganization() : "");

        TableLayout controls = view.findViewById(R.id.controls);
        controls.removeAllViews();
        for (LinphoneNumberOrAddress noa : mContact.getNumbersOrAddresses()) {
            boolean skip = false;
            View v = inflater.inflate(R.layout.contact_control_row, null);

            String value = noa.getValue();
            String displayednumberOrAddress =
                    LinphoneUtils.getDisplayableUsernameFromAddress(value);

            TextView label = v.findViewById(R.id.address_label);
            if (noa.isSIPAddress()) {
                label.setText(R.string.sip_address);
                skip |= getResources().getBoolean(R.bool.hide_contact_sip_addresses);
            } else {
                label.setText(R.string.phone_number);
                skip |= getResources().getBoolean(R.bool.hide_contact_phone_numbers);
            }

            TextView tv = v.findViewById(R.id.numeroOrAddress);
            tv.setText(displayednumberOrAddress);
            tv.setSelected(true);

            ProxyConfig lpc = LinphoneManager.getLc().getDefaultProxyConfig();
            if (lpc != null) {
                String username = lpc.normalizePhoneNumber(displayednumberOrAddress);
                if (username != null) {
                    value = LinphoneUtils.getFullAddressFromUsername(username);
                }
            }

            v.findViewById(R.id.friendLinphone).setVisibility(View.GONE);
            if (mContact.getFriend() != null) {
                PresenceModel pm = mContact.getFriend().getPresenceModelForUriOrTel(noa.getValue());
                if (pm != null && pm.getBasicStatus().equals(PresenceBasicStatus.Open)) {
                    v.findViewById(R.id.friendLinphone).setVisibility(View.VISIBLE);
                } else {
                    if (getResources()
                            .getBoolean(R.bool.hide_numbers_and_addresses_without_presence)) {
                        skip = true;
                    }
                }
            }

            v.findViewById(R.id.inviteFriend).setVisibility(View.GONE);
            if (!noa.isSIPAddress()
                    && v.findViewById(R.id.friendLinphone).getVisibility() == View.GONE
                    && !getResources().getBoolean(R.bool.hide_invite_contact)) {
                v.findViewById(R.id.inviteFriend).setVisibility(View.VISIBLE);
                v.findViewById(R.id.inviteFriend).setTag(noa.getNormalizedPhone());
                v.findViewById(R.id.inviteFriend)
                        .setOnClickListener(
                                new OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        String number = (String) v.getTag();
                                        Intent smsIntent = new Intent(Intent.ACTION_SENDTO);
                                        smsIntent.putExtra("address", number);
                                        smsIntent.setData(Uri.parse("smsto:" + number));
                                        String text =
                                                getString(R.string.invite_friend_text)
                                                        .replace(
                                                                "%s",
                                                                getString(R.string.download_link));
                                        smsIntent.putExtra("sms_body", text);
                                        startActivity(smsIntent);
                                    }
                                });
            }

            String contactAddress = mContact.getContactFromPresenceModelForUriOrTel(noa.getValue());
            if (!mDisplayChatAddressOnly) {
                v.findViewById(R.id.contact_call).setOnClickListener(mDialListener);
                if (contactAddress != null) {
                    v.findViewById(R.id.contact_call).setTag(contactAddress);
                } else {
                    v.findViewById(R.id.contact_call).setTag(value);
                }
            } else {
                v.findViewById(R.id.contact_call).setVisibility(View.GONE);
            }

            v.findViewById(R.id.contact_chat).setOnClickListener(mChatListener);
            v.findViewById(R.id.contact_chat_secured).setOnClickListener(mChatListener);
            if (contactAddress != null) {
                v.findViewById(R.id.contact_chat).setTag(contactAddress);
                v.findViewById(R.id.contact_chat_secured).setTag(contactAddress);
            } else {
                v.findViewById(R.id.contact_chat).setTag(value);
                v.findViewById(R.id.contact_chat_secured).setTag(value);
            }

            if (v.findViewById(R.id.friendLinphone).getVisibility() == View.VISIBLE
                    && mContact.hasPresenceModelForUriOrTelCapability(
                            noa.getValue(), FriendCapability.LimeX3Dh)) {
                v.findViewById(R.id.contact_chat_secured).setVisibility(View.VISIBLE);
            } else {
                v.findViewById(R.id.contact_chat_secured).setVisibility(View.GONE);
            }

            if (getResources().getBoolean(R.bool.disable_chat)) {
                v.findViewById(R.id.contact_chat).setVisibility(View.GONE);
                v.findViewById(R.id.contact_chat_secured).setVisibility(View.GONE);
            }

            if (!skip) {
                controls.addView(v);
            }
        }
    }

    @Override
    public void onContactsUpdated() {
        LinphoneContact contact =
                ContactsManager.getInstance().findContactFromAndroidId(mContact.getAndroidId());
        if (contact != null) {
            changeDisplayedContact(contact);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        ContactsManager.getInstance().addContactsListener(this);
        if (LinphoneActivity.isInstanciated()) {
            LinphoneActivity.instance().selectMenu(FragmentsAvailable.CONTACT_DETAIL);
        }
        displayContact(mInflater, mView);
    }

    @Override
    public void onPause() {
        if (mChatRoom != null) {
            mChatRoom.removeListener(mChatRoomCreationListener);
        }
        ContactsManager.getInstance().removeContactsListener(this);
        super.onPause();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();

        if (id == R.id.editContact) {
            ContactsManager.getInstance().editContact(getActivity(), mContact, null);
        } else if (id == R.id.deleteContact) {
            final Dialog dialog =
                    LinphoneActivity.instance().displayDialog(getString(R.string.delete_text));
            Button delete = dialog.findViewById(R.id.dialog_delete_button);
            Button cancel = dialog.findViewById(R.id.dialog_cancel_button);

            delete.setOnClickListener(
                    new OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            mContact.delete();
                            // To ensure removed contact won't appear in the contacts list anymore
                            ContactsManager.getInstance().fetchContactsAsync();
                            LinphoneActivity.instance().displayContacts(false);
                            dialog.dismiss();
                        }
                    });

            cancel.setOnClickListener(
                    new OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            dialog.dismiss();
                        }
                    });
            dialog.show();
        } else if (id == R.id.back) {
            getFragmentManager().popBackStackImmediate();
        }
    }
}
