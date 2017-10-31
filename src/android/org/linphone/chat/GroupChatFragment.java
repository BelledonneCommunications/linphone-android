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

import android.app.Activity;
import android.app.Dialog;
import android.app.Fragment;
import android.content.CursorLoader;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.linphone.LinphoneManager;
import org.linphone.LinphoneService;
import org.linphone.LinphoneUtils;
import org.linphone.R;
import org.linphone.activities.LinphoneActivity;
import org.linphone.compatibility.Compatibility;
import org.linphone.contacts.ContactAddress;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.Buffer;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatMessageListener;
import org.linphone.core.ChatRoom;
import org.linphone.core.ChatRoomListener;
import org.linphone.core.Content;
import org.linphone.core.Core;
import org.linphone.core.EventLog;
import org.linphone.core.Friend;
import org.linphone.core.FriendList;
import org.linphone.core.Participant;
import org.linphone.receivers.ContactsUpdatedListener;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.linphone.fragments.FragmentsAvailable.CHAT;

public class GroupChatFragment extends Fragment implements ChatRoomListener, ContactsUpdatedListener {
	private static final int ADD_PHOTO = 1337;

	private ImageView mBackButton, mCallButton, mBackToCallButton, mGroupInfosButton, mEditButton;
	private ImageView mCancelEditButton, mSelectAllButton, mDeselectAllButton, mDeleteSelectionButton;
	private ImageView mAttachImageButton, mSendMessageButton;
	private TextView mRoomLabel, mParticipantsLabel, mRemoteComposing;
	private EditText mMessageTextToSend;
	private LayoutInflater mInflater;
	private ListView mChatEventsList;
	private LinearLayout mFilesUploadLayout;

	private ViewTreeObserver.OnGlobalLayoutListener mKeyboardListener;
	private Uri mImageToUploadUri;
	private ChatEventsAdapter mMessagesAdapter;
	private String mRemoteSipUri;
	private Address mRemoteSipAddress;
	private ChatRoom mChatRoom;
	private ArrayList<LinphoneContact> mParticipants;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Retain the fragment across configuration changes
		setRetainInstance(true);

		if (getArguments() != null && getArguments().getString("SipUri") != null) {
			mRemoteSipUri = getArguments().getString("SipUri");
			mRemoteSipAddress = LinphoneManager.getLc().createAddress(mRemoteSipUri);
		}

		mInflater = inflater;
		View view = inflater.inflate(R.layout.chat, container, false);

		mBackButton = view.findViewById(R.id.back);
		mBackButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				LinphoneActivity.instance().goToChatList();
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
				ArrayList<ContactAddress> participants = new ArrayList<>();
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
				LinphoneActivity.instance().goToChatGroupInfos(participants, mChatRoom.getSubject(), true, /*TODO*/ false);
			}
		});

		mEditButton = view.findViewById(R.id.edit);
		mEditButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				//TODO
			}
		});

		mCancelEditButton = view.findViewById(R.id.cancel);
		mCancelEditButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				//TODO
			}
		});

		mSelectAllButton = view.findViewById(R.id.select_all);
		mSelectAllButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				//TODO
			}
		});

		mDeselectAllButton = view.findViewById(R.id.deselect_all);
		mDeselectAllButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				//TODO
			}
		});

		mDeleteSelectionButton = view.findViewById(R.id.delete);
		mDeleteSelectionButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				//TODO
			}
		});

		mRoomLabel = view.findViewById(R.id.subject);
		mParticipantsLabel = view.findViewById(R.id.participants);

		mFilesUploadLayout = view.findViewById(R.id.file_upload_layout);

		mAttachImageButton = view.findViewById(R.id.send_picture);
		mAttachImageButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				LinphoneActivity.instance().checkAndRequestPermissionsToSendImage();
				pickImage();
			}
		});

		mSendMessageButton = view.findViewById(R.id.send_message);
		mSendMessageButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				sendMessage();
			}
		});

		mMessageTextToSend = view.findViewById(R.id.message);
		mMessageTextToSend.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }

			@Override
			public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
				mSendMessageButton.setEnabled(mMessageTextToSend.getText().length() > 0);
				if (mChatRoom != null) {
					mChatRoom.compose();
				}
			}

			@Override
			public void afterTextChanged(Editable editable) { }
		});

		mRemoteComposing = view.findViewById(R.id.remote_composing);

		mChatEventsList = view.findViewById(R.id.chat_message_list);

		return view;
	}

	@Override
	public void onResume() {
		super.onResume();

		if (LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().selectMenu(CHAT);
		}
		ContactsManager.addContactsListener(this);

		addVirtualKeyboardVisiblityListener();
		// Force hide keyboard
		getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

		initChatRoom();
		displayChatRoomHeader();
		displayChatRoomHistory();
	}

	@Override
	public void onPause() {
		ContactsManager.removeContactsListener(this);
		removeVirtualKeyboardVisiblityListener();
		super.onPause();
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (data != null) {
			if (requestCode == ADD_PHOTO && resultCode == Activity.RESULT_OK) {
				String fileToUploadPath = null;
				if (data != null && data.getData() != null) {
					if (data.getData().toString().contains("com.android.contacts/contacts/")) {
						if (getCVSPathFromLookupUri(data.getData().toString()) != null) {
							fileToUploadPath = getCVSPathFromLookupUri(data.getData().toString()).toString();
						} else {
							//TODO Error
							return;
						}
					} else {
						fileToUploadPath = getRealPathFromURI(data.getData());
					}
					fileToUploadPath = getRealPathFromURI(data.getData());
					if (fileToUploadPath == null) {
						fileToUploadPath = data.getData().toString();
					}
				} else if (mImageToUploadUri != null) {
					fileToUploadPath = mImageToUploadUri.getPath();
				}
				if (LinphoneUtils.isExtensionImage(fileToUploadPath)) {
					addImageToPendingList(fileToUploadPath);
				} else {
					if (fileToUploadPath.startsWith("content://")) {
						fileToUploadPath = LinphoneUtils.getFilePath(this.getActivity().getApplicationContext(), Uri.parse(fileToUploadPath));
					} else if (fileToUploadPath.contains("com.android.contacts/contacts/")) {
						fileToUploadPath = getCVSPathFromLookupUri(fileToUploadPath).toString();
					}
					addFileToPendingList(fileToUploadPath);
				}
			} else {
				super.onActivityResult(requestCode, resultCode, data);
			}
		} else {
			if (LinphoneUtils.isExtensionImage(mImageToUploadUri.getPath())) {
				addImageToPendingList(mImageToUploadUri.getPath());
			}
		}
	}

	private void addVirtualKeyboardVisiblityListener() {
		mKeyboardListener = new ViewTreeObserver.OnGlobalLayoutListener() {
			@Override
			public void onGlobalLayout() {
				Rect visibleArea = new Rect();
				getActivity().getWindow().getDecorView().getWindowVisibleDisplayFrame(visibleArea);

				int heightDiff = getActivity().getWindow().getDecorView().getRootView().getHeight() - (visibleArea.bottom - visibleArea.top);
				if (heightDiff > 200) {
					showKeyboardVisibleMode();
				} else {
					hideKeyboardVisibleMode();
				}
			}
		};
		getActivity().getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(mKeyboardListener);
	}

	private void removeVirtualKeyboardVisiblityListener() {
		Compatibility.removeGlobalLayoutListener(getActivity().getWindow().getDecorView().getViewTreeObserver(), mKeyboardListener);
	}

	public void showKeyboardVisibleMode() {
		LinphoneActivity.instance().hideTabBar(true);
	}

	public void hideKeyboardVisibleMode() {
		LinphoneActivity.instance().hideTabBar(false);
	}

	private String getRealPathFromURI(Uri contentUri) {
		String[] proj = {MediaStore.Images.Media.DATA};
		CursorLoader loader = new CursorLoader(getActivity(), contentUri, proj, null, null, null);
		Cursor cursor = loader.loadInBackground();
		if (cursor != null && cursor.moveToFirst()) {
			int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
			String result = cursor.getString(column_index);
			cursor.close();
			return result;
		}
		return null;
	}

	public Uri getCVSPathFromLookupUri(String content) {
		String contactId = LinphoneUtils.getNameFromFilePath(content);
		FriendList[] friendList = LinphoneManager.getLc().getFriendsLists();
		for (FriendList list : friendList) {
			for (Friend friend : list.getFriends()) {
				if (friend.getRefKey().toString().equals(contactId)) {
					String contactVcard = friend.getVcard().asVcard4String();
					Uri path = LinphoneUtils.createCvsFromString(contactVcard);
					return path;
				}
			}
		}
		return null;
	}

	private void getContactsForParticipants() {
		mParticipants = new ArrayList<>();
		if (mChatRoom.canHandleParticipants()) {
			int index = 0;
			StringBuilder participantsLabel = new StringBuilder();
			for (Participant p : mChatRoom.getParticipants()) {
				LinphoneContact c = ContactsManager.getInstance().findContactFromAddress(p.getAddress());
				if (c != null) {
					mParticipants.add(c);
					participantsLabel.append(c.getFullName());
				} else {
					String displayName = p.getAddress().getDisplayName();
					if (displayName != null && !displayName.isEmpty()) {
						participantsLabel.append(displayName);
					} else {
						participantsLabel.append(p.getAddress().getUsername());
					}
				}
				index++;
				if (index != mChatRoom.getNbParticipants())	participantsLabel.append(";");
			}
		} else {
			LinphoneContact c = ContactsManager.getInstance().findContactFromAddress(mRemoteSipAddress);
			if (c != null) {
				mParticipants.add(c);
			}
		}

		if (mMessagesAdapter != null) {
			mMessagesAdapter.setContacts(mParticipants);
		}
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

		getContactsForParticipants();
	}

	private void displayChatRoomHeader() {
		Core core = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (core == null || mChatRoom == null) return;

		mRemoteComposing.setVisibility(View.INVISIBLE);

		if (core.getCallsNb() > 0) {
			mBackToCallButton.setVisibility(View.VISIBLE);
		} else {
			mBackToCallButton.setVisibility(View.GONE);
			if (mChatRoom.canHandleParticipants()) {
				mCallButton.setVisibility(View.GONE);
				mGroupInfosButton.setVisibility(View.VISIBLE);
				mRoomLabel.setText(mChatRoom.getSubject());
				mParticipantsLabel.setVisibility(View.VISIBLE);
			} else {
				mCallButton.setVisibility(View.VISIBLE);
				mGroupInfosButton.setVisibility(View.GONE);
				mParticipantsLabel.setVisibility(View.GONE);

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
		if (mChatRoom == null) return;
		mMessagesAdapter = new ChatEventsAdapter(getActivity(), this, mInflater, mChatRoom.getHistoryEvents(0), mParticipants);
		mChatEventsList.setAdapter(mMessagesAdapter);
	}

	private void pickImage() {
		List<Intent> cameraIntents = new ArrayList<Intent>();
		Intent captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
		File file = new File(Environment.getExternalStorageDirectory(), getString(R.string.temp_photo_name_with_date).replace("%s", String.valueOf(System.currentTimeMillis())+".jpeg"));
		mImageToUploadUri = Uri.fromFile(file);
		captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, mImageToUploadUri);
		cameraIntents.add(captureIntent);

		Intent galleryIntent = new Intent();
		galleryIntent.setType("image/*");
		galleryIntent.setAction(Intent.ACTION_PICK);

		Intent fileIntent = new Intent();
		fileIntent.setType("*/*");
		fileIntent.setAction(Intent.ACTION_GET_CONTENT);
		cameraIntents.add(fileIntent);

		Intent chooserIntent = Intent.createChooser(galleryIntent, getString(R.string.image_picker_title));
		chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, cameraIntents.toArray(new Parcelable[]{}));

		startActivityForResult(chooserIntent, ADD_PHOTO);
	}

	private void addFileToPendingList(String path) {
		View pendingFile = mInflater.inflate(R.layout.file_upload_cell, mFilesUploadLayout, false);

		TextView text = pendingFile.findViewById(R.id.pendingFileForUpload);
		String extension = path.substring(path.lastIndexOf('.'));
		text.setText(extension);

		ImageView remove = pendingFile.findViewById(R.id.remove);
		remove.setTag(pendingFile);
		remove.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				View pendingImage = (View)view.getTag();
				mFilesUploadLayout.removeView(pendingImage);
			}
		});

		mFilesUploadLayout.addView(pendingFile);
	}

	private void addImageToPendingList(String path) {
		View pendingImage = mInflater.inflate(R.layout.image_upload_cell, mFilesUploadLayout, false);

		ImageView image = pendingImage.findViewById(R.id.pendingImageForUpload);
		Bitmap bm = BitmapFactory.decodeFile(path);
		if (bm == null) return;
		image.setImageBitmap(bm);

		ImageView remove = pendingImage.findViewById(R.id.remove);
		remove.setTag(pendingImage);
		remove.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				View pendingImage = (View)view.getTag();
				mFilesUploadLayout.removeView(pendingImage);
			}
		});

		mFilesUploadLayout.addView(pendingImage);
	}

	private void sendMessage() {
		String text = mMessageTextToSend.getText().toString();
		ChatMessage msg = mChatRoom.createMessage(text);
		msg.setListener(new ChatMessageListener() {
			@Override
			public void onFileTransferRecv(ChatMessage message, Content content, Buffer buffer) {

			}

			@Override
			public Buffer onFileTransferSend(ChatMessage message, Content content, int offset, int size) {
				return null;
			}

			@Override
			public void onFileTransferProgressIndication(ChatMessage message, Content content, int offset, int total) {
				ChatBubbleViewHolder holder = (ChatBubbleViewHolder) message.getUserData();
				if (holder != null && message.getMessageId().equals(holder.messageId)) {
					holder.fileTransferProgressBar.setProgress(offset * 100 / total);
				}
			}

			@Override
			public void onMsgStateChanged(ChatMessage message, ChatMessage.State state) {
				ChatBubbleViewHolder holder = (ChatBubbleViewHolder) message.getUserData();
				if (holder != null && message.getMessageId().equals(holder.messageId) && message.isOutgoing()) {
					if (state == ChatMessage.State.DeliveredToUser) {
						holder.imdmLayout.setVisibility(View.VISIBLE);
						holder.imdmIcon.setImageResource(R.drawable.message_delivered);
						holder.imdmLabel.setText(R.string.delivered);
						holder.imdmLabel.setTextColor(getResources().getColor(R.color.colorD));
					} else if (state == ChatMessage.State.Displayed) {
						holder.imdmLayout.setVisibility(View.VISIBLE);
						holder.imdmIcon.setImageResource(R.drawable.message_read);
						holder.imdmLabel.setText(R.string.displayed);
						holder.imdmLabel.setTextColor(getResources().getColor(R.color.colorK));
					} else if (state == ChatMessage.State.NotDelivered) {
						holder.imdmLayout.setVisibility(View.VISIBLE);
						holder.imdmIcon.setImageResource(R.drawable.message_undelivered);
						holder.imdmLabel.setText(R.string.resend);
						holder.imdmLabel.setTextColor(getResources().getColor(R.color.colorI));
					}
				}
			}
		});
		msg.send();

		mFilesUploadLayout.removeAllViews();
		mMessageTextToSend.setText("");
	}

	public void scrollToBottom() {
		if (((mChatEventsList.getLastVisiblePosition() >= (mMessagesAdapter.getCount() - 1)) && (mChatEventsList.getFirstVisiblePosition() <= (mMessagesAdapter.getCount() - 1)))) {
			mChatEventsList.setSelection(mMessagesAdapter.getCount() - 1);
		}
	}

	@Override
	public void onChatMessageSent(ChatRoom cr, EventLog event) {
		mMessagesAdapter.addToHistory(event);
	}

	@Override
	public void onUndecryptableMessageReceived(ChatRoom cr, ChatMessage msg) {
		final Address from = msg.getFromAddress();
		final LinphoneContact contact = ContactsManager.getInstance().findContactFromAddress(from);

		if (LinphoneActivity.instance().isOnBackground()) {
			if (!getResources().getBoolean(R.bool.disable_chat_message_notification)) {
				String to = msg.getToAddress().asString();
				if (contact != null) {
					LinphoneService.instance().removedNotification(to, from.asStringUriOnly(),
							contact.getFullName(), getString(R.string.message_cant_be_decrypted_notif));
				} else {
					LinphoneService.instance().removedNotification(to, from.asStringUriOnly(),
							from.getUsername(), getString(R.string.message_cant_be_decrypted_notif));
				}
			}
		} else if (LinphoneManager.getLc().limeEnabled() == Core.LimeState.Mandatory) {
			final Dialog dialog = LinphoneActivity.instance().displayDialog(
					getString(R.string.message_cant_be_decrypted)
							.replace("%s", (contact != null) ? contact.getFullName() : from.getUsername()));
			Button delete = dialog.findViewById(R.id.delete_button);
			delete.setText(getString(R.string.call));
			Button cancel = dialog.findViewById(R.id.cancel);
			cancel.setText(getString(R.string.ok));
			delete.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					LinphoneManager.getInstance().newOutgoingCall(from.asStringUriOnly()
							, (contact != null) ? contact.getFullName() : from.getUsername());
					dialog.dismiss();
				}
			});

			cancel.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					dialog.dismiss();
				}
			});
			dialog.show();
		}
	}

	@Override
	public void onChatMessageReceived(ChatRoom cr, EventLog event) {
		cr.markAsRead();
		LinphoneActivity.instance().updateMissedChatCount();

		ChatMessage msg = event.getChatMessage();
		String externalBodyUrl = msg.getExternalBodyUrl();
		Content fileTransferContent = msg.getFileTransferInformation();
		if (externalBodyUrl != null || fileTransferContent != null) {
			LinphoneActivity.instance().checkAndRequestExternalStoragePermission();
		}

		mMessagesAdapter.addToHistory(event);
	}

	@Override
	public void onIsComposingReceived(ChatRoom cr, Address remoteAddr, boolean isComposing) {
		if (cr.canHandleParticipants()) {
			ArrayList<String> composing = new ArrayList<>();
			for (Address a : cr.getComposingAddresses()) {
				boolean found = false;
				for (LinphoneContact c : mParticipants) {
					if (c.hasAddress(a.asStringUriOnly())) {
						composing.add(c.getFullName());
						found = true;
						break;
					}
				}
				if (!found) {
					String displayName = a.getDisplayName();
					if (displayName != null && !displayName.isEmpty()) {
						composing.add(displayName);
					} else {
						composing.add(a.getUsername());
					}
				}
			}

			if (composing.size() == 1) {
				mRemoteComposing.setText(getString(R.string.remote_composing_single).replace("%s", composing.get(0)));
				mRemoteComposing.setVisibility(View.VISIBLE);
			} else if (composing.size() > 2) {
				StringBuilder remotes = new StringBuilder();
				int i = 0;
				for (String remote : composing) {
					remotes.append(remote);
					i++;
					if (i != composing.size()) {
						remotes.append(", ");
					}
				}
				mRemoteComposing.setText(getString(R.string.remote_composing_multiple).replace("%s", remotes.toString()));
				mRemoteComposing.setVisibility(View.VISIBLE);
			} else {
				mRemoteComposing.setVisibility(View.GONE);
			}
		} else {
			if (isComposing) {
				String displayName;
				if (mParticipants.size() > 0) {
					displayName = mParticipants.get(0).getFullName();
				} else {
					displayName = remoteAddr.getDisplayName();
					if (displayName == null || displayName.isEmpty()) {
						displayName = remoteAddr.getUsername();
					}
				}
				mRemoteComposing.setText(getString(R.string.remote_composing_single).replace("%s", displayName));
				mRemoteComposing.setVisibility(View.VISIBLE);
			} else {
				mRemoteComposing.setVisibility(View.GONE);
			}
		}
	}

	@Override
	public void onMessageReceived(ChatRoom cr, ChatMessage msg) {

	}

	@Override
	public void onParticipantAdminStatusChanged(ChatRoom cr, EventLog event) {
		mMessagesAdapter.addToHistory(event);
	}

	@Override
	public void onParticipantDeviceRemoved(ChatRoom cr, EventLog event) {
		mMessagesAdapter.addToHistory(event);
	}

	@Override
	public void onParticipantRemoved(ChatRoom cr, EventLog event) {
		mMessagesAdapter.addToHistory(event);
	}

	@Override
	public void onParticipantDeviceAdded(ChatRoom cr, EventLog event) {
		mMessagesAdapter.addToHistory(event);
	}

	@Override
	public void onStateChanged(ChatRoom cr, ChatRoom.State newState) {

	}

	@Override
	public void onParticipantAdded(ChatRoom cr, EventLog event) {
		mMessagesAdapter.addToHistory(event);
	}

	@Override
	public void onSubjectChanged(ChatRoom cr, EventLog event) {
		mMessagesAdapter.addToHistory(event);
		mRoomLabel.setText(event.getSubject());
	}

	@Override
	public void onContactsUpdated() {
		getContactsForParticipants();
	}
}
