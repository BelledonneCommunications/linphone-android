/*
GroupChatFragment.java
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

import android.app.Fragment;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.linphone.LinphoneManager;
import org.linphone.LinphoneUtils;
import org.linphone.R;
import org.linphone.activities.LinphoneActivity;
import org.linphone.contacts.ContactAddress;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatRoom;
import org.linphone.core.ChatRoomListener;
import org.linphone.core.Content;
import org.linphone.core.Core;
import org.linphone.core.Participant;

import java.util.ArrayList;
import java.util.List;

public class GroupChatFragment extends Fragment implements ChatRoomListener {
	private ImageView mBackButton, mCallButton, mBackToCallButton, mGroupInfosButton, mEditButton;
	private ImageView mCancelEditButton, mSelectAllButton, mDeselectAllButton, mDeleteSelectionButton;
	private ImageView mAttachImageButton, mSendMessageButton;
	private TextView mRoomLabel, mRemoteComposing;
	private EditText mMessageTextToSend;
	private LayoutInflater mInflater;
	private Bitmap defaultContactAvatar;
	private ListView mChatEventsList;

	private ChatEventsAdapter mMessagesAdapter;
	private String mRemoteSipUri;
	private Address mRemoteSipAddress;
	private ChatRoom mChatRoom;
	private List<LinphoneContact> mParticipants;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Retain the fragment across configuration changes
		setRetainInstance(true);

		if (getArguments() != null && getArguments().getString("SipUri") != null) {
			mRemoteSipUri = getArguments().getString("SipUri");
			mRemoteSipAddress = LinphoneManager.getLc().createAddress(mRemoteSipUri);
		}

		defaultContactAvatar = BitmapFactory.decodeResource(getActivity().getResources(), R.drawable.chat_picture_over);

		mInflater = inflater;
		View view = inflater.inflate(R.layout.chat, container, false);

		mBackButton = view.findViewById(R.id.back);
		mBackButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				getFragmentManager().popBackStackImmediate();
			}
		});

		mCallButton = view.findViewById(R.id.start_call);
		mCallButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				LinphoneActivity.instance().setAddresGoToDialerAndCall(mRemoteSipUri, null, null);
			}
		});

		mBackToCallButton = view.findViewById(R.id.back_to_call);
		mBackToCallButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				LinphoneActivity.instance().resetClassicMenuLayoutAndGoBackToCallIfStillRunning();
			}
		});

		mGroupInfosButton = view.findViewById(R.id.group_infos);
		mGroupInfosButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (mChatRoom == null) return;
				ArrayList<ContactAddress> participants = new ArrayList<ContactAddress>();
				for (Participant p : mChatRoom.getParticipants()) {
					Address a = p.getAddress();
					LinphoneContact c = ContactsManager.getInstance().findContactFromAddress(a);
					if (c == null) {
						c = new LinphoneContact();
						String displayName = a.getDisplayName();
						if (displayName == null || displayName.isEmpty()) {
							c.setFullName(a.getUsername());
						} else {
							c.setFullName(displayName);
						}
					}
					ContactAddress ca = new ContactAddress(c, a.asString(), c.isFriend());
					participants.add(ca);
				}
				LinphoneActivity.instance().displayChatGroupInfos(participants, true);
			}
		});

		mEditButton = view.findViewById(R.id.edit);
		mEditButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {

			}
		});

		mCancelEditButton = view.findViewById(R.id.cancel);
		mCancelEditButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {

			}
		});

		mSelectAllButton = view.findViewById(R.id.select_all);
		mSelectAllButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {

			}
		});

		mDeselectAllButton = view.findViewById(R.id.deselect_all);
		mDeselectAllButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {

			}
		});

		mDeleteSelectionButton = view.findViewById(R.id.delete);
		mDeleteSelectionButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {

			}
		});

		mRoomLabel = view.findViewById(R.id.contact_name);

		mAttachImageButton = view.findViewById(R.id.send_picture);
		mAttachImageButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {

			}
		});

		mSendMessageButton = view.findViewById(R.id.send_message);
		mSendMessageButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {

			}
		});

		mMessageTextToSend = view.findViewById(R.id.message);
		mMessageTextToSend.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

			}

			@Override
			public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
				mSendMessageButton.setEnabled(mMessageTextToSend.getText().length() > 0);
				if (mChatRoom != null) {
					mChatRoom.compose();
				}
			}

			@Override
			public void afterTextChanged(Editable editable) {

			}
		});

		mRemoteComposing = view.findViewById(R.id.remote_composing);

		mChatEventsList = view.findViewById(R.id.chat_message_list);

		return view;
	}

	@Override
	public void onResume() {
		super.onResume();
		initChatRoom();
		displayChatRoomHeader();
		displayChatRoomHistory();
	}

	private void initChatRoom() {
		Core core = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (mRemoteSipAddress == null || mRemoteSipUri == null || mRemoteSipUri.length() == 0 || core == null) {
			LinphoneActivity.instance().goToDialerFragment();
			return;
		}

		mChatRoom = core.getChatRoom(mRemoteSipAddress);
		mChatRoom.setListener(this);
		mChatRoom.markAsRead();
		LinphoneActivity.instance().updateMissedChatCount();

		mParticipants = new ArrayList<>();
		if (mChatRoom.canHandleParticipants()) {
			for (Participant p : mChatRoom.getParticipants()) {
				LinphoneContact c = ContactsManager.getInstance().findContactFromAddress(p.getAddress());
				if (c != null) {
					mParticipants.add(c);
				}
			}
		} else {
			LinphoneContact c = ContactsManager.getInstance().findContactFromAddress(mRemoteSipAddress);
			if (c != null) {
				mParticipants.add(c);
			}
		}
	}

	private void displayChatRoomHeader() {
		Core core = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (core == null) return;

		mRemoteComposing.setVisibility(View.INVISIBLE);

		if (core.getCallByRemoteAddress(mRemoteSipUri) != null) {
			mBackToCallButton.setVisibility(View.VISIBLE);
		} else {
			mBackToCallButton.setVisibility(View.GONE);
			if (mChatRoom.canHandleParticipants()) {
				mCallButton.setVisibility(View.GONE);
				mGroupInfosButton.setVisibility(View.VISIBLE);
				mRoomLabel.setText(mChatRoom.getSubject());
			} else {
				mCallButton.setVisibility(View.VISIBLE);
				mGroupInfosButton.setVisibility(View.GONE);

				if (mParticipants.size() == 0) {
					// Contact not found
					String displayName = mRemoteSipAddress.getDisplayName();
					if (displayName == null || displayName.isEmpty()) {
						mRoomLabel.setText(mRemoteSipAddress.getUsername());
					} else {
						mRoomLabel.setText(displayName);
					}
				} else {
					mRoomLabel.setText(mParticipants.get(0).getFullName());
				}
			}
		}
	}

	private void displayChatRoomHistory() {
		mMessagesAdapter = new ChatEventsAdapter();
	}

	@Override
	public void onUndecryptableMessageReceived(ChatRoom cr, ChatMessage msg) {

	}

	@Override
	public void onMessageReceived(ChatRoom cr, ChatMessage msg) {
		cr.markAsRead();
		LinphoneActivity.instance().updateMissedChatCount();

		String externalBodyUrl = msg.getExternalBodyUrl();
		Content fileTransferContent = msg.getFileTransferInformation();
		if (externalBodyUrl != null || fileTransferContent != null) {
			LinphoneActivity.instance().checkAndRequestExternalStoragePermission();
		}
	}

	@Override
	public void onIsComposingReceived(ChatRoom cr, Address remoteAddr, boolean isComposing) {
		if (isComposing) {
			mRemoteComposing.setText(getString(R.string.remote_composing_2).replace("%s", remoteAddr.getDisplayName()));
		}
		mRemoteComposing.setVisibility(isComposing ? View.VISIBLE : View.GONE);
	}
}
