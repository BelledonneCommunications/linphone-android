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
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.linphone.LinphoneManager;
import org.linphone.LinphoneService;
import org.linphone.LinphoneUtils;
import org.linphone.R;
import org.linphone.activities.LinphoneActivity;
import org.linphone.compatibility.Compatibility;
import org.linphone.contacts.ContactAddress;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.ContactsUpdatedListener;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatRoom;
import org.linphone.core.ChatRoomCapabilities;
import org.linphone.core.ChatRoomListener;
import org.linphone.core.Content;
import org.linphone.core.Core;
import org.linphone.core.EventLog;
import org.linphone.core.Factory;
import org.linphone.core.LimeState;
import org.linphone.core.Participant;
import org.linphone.core.Reason;
import org.linphone.mediastream.Log;
import org.linphone.ui.SelectableHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static android.content.Context.INPUT_METHOD_SERVICE;
import static org.linphone.fragments.FragmentsAvailable.CHAT;

public class GroupChatFragment extends Fragment implements ChatRoomListener, ContactsUpdatedListener, ChatBubbleViewHolder.ClickListener, SelectableHelper.DeleteListener {
    private static final int ADD_PHOTO = 1337;
    private static final int MESSAGES_PER_PAGE = 20;

    private Handler mHandler = new Handler(Looper.getMainLooper());
    private ImageView mBackButton, mCallButton, mBackToCallButton, mGroupInfosButton;
    private ImageView mAttachImageButton, mSendMessageButton;
    private TextView mRoomLabel, mParticipantsLabel, mRemoteComposing;
    private EditText mMessageTextToSend;
    private LayoutInflater mInflater;
    private RecyclerView mChatEventsList;
    private LinearLayout mFilesUploadLayout;
    private SelectableHelper mSelectionHelper;
    private Context mContext;
    private ViewTreeObserver.OnGlobalLayoutListener mKeyboardListener;
    private Uri mImageToUploadUri;
    private ChatEventsAdapter mEventsAdapter;
    private String mRemoteSipUri;
    private Address mRemoteSipAddress, mRemoteParticipantAddress;
    private ChatRoom mChatRoom;
    private ArrayList<LinphoneContact> mParticipants;
    private LinearLayoutManager layoutManager;
    private int mContextMenuMessagePosition;
    private ChatScrollListener mChatScrollListener;
    private LinearLayout mTopBar;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Retain the fragment across configuration changes
        setRetainInstance(true);

        if (getArguments() != null && getArguments().getString("SipUri") != null) {
            mRemoteSipUri = getArguments().getString("SipUri");
            mRemoteSipAddress = LinphoneManager.getLc().createAddress(mRemoteSipUri);
        }

        mContext = getActivity().getApplicationContext();
        mInflater = inflater;
        View view = inflater.inflate(R.layout.chat, container, false);

        mTopBar = view.findViewById(R.id.top_bar);

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
                LinphoneActivity.instance().setAddresGoToDialerAndCall(mRemoteParticipantAddress.asString(), null, null);
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
                        String displayName = LinphoneUtils.getAddressDisplayName(a);
                        c.setFullName(displayName);
                    }
                    ContactAddress ca = new ContactAddress(c, a.asString(), "", c.isFriend(), p.isAdmin());
                    participants.add(ca);
                }
                LinphoneActivity.instance().goToChatGroupInfos(mRemoteSipAddress.asString(), participants, mChatRoom.getSubject(), mChatRoom.getMe() != null ? mChatRoom.getMe().isAdmin() : false, false, null);
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
                pickFile();
            }
        });

        mSendMessageButton = view.findViewById(R.id.send_message);
        mSendMessageButton.setEnabled(false);
        mSendMessageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendMessage();
            }
        });

        mMessageTextToSend = view.findViewById(R.id.message);
        mMessageTextToSend.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                mSendMessageButton.setEnabled(mMessageTextToSend.getText().length() > 0 || mFilesUploadLayout.getChildCount() > 0);
                if (mChatRoom != null && mMessageTextToSend.getText().length() > 0) {
                    mAttachImageButton.setEnabled(false);
                    mChatRoom.compose();
                } else {
                    mAttachImageButton.setEnabled(true);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        mMessageTextToSend.clearFocus();

        mRemoteComposing = view.findViewById(R.id.remote_composing);

        mChatEventsList = view.findViewById(R.id.chat_message_list);
        mSelectionHelper = new SelectableHelper(view, this);
        layoutManager = new LinearLayoutManager(mContext, LinearLayoutManager.VERTICAL, true);
        mChatEventsList.setLayoutManager(layoutManager);

        mChatScrollListener = new ChatScrollListener(layoutManager) {
            @Override
            public void onLoadMore(int page, int totalItemsCount, RecyclerView view) {
                loadMoreData(totalItemsCount);
            }
        };
        mChatEventsList.addOnScrollListener(mChatScrollListener);

        if (getArguments() != null) {
            String fileSharedUri = getArguments().getString("fileSharedUri");
            if (fileSharedUri != null) {
                if (LinphoneUtils.isExtensionImage(fileSharedUri)) {
                    addImageToPendingList(fileSharedUri);
                } else {
                    if (fileSharedUri.startsWith("content://") || fileSharedUri.startsWith("file://")) {
                        fileSharedUri = LinphoneUtils.getFilePath(getActivity().getApplicationContext(), Uri.parse(fileSharedUri));
                    } else if (fileSharedUri.contains("com.android.contacts/contacts/")) {
                        fileSharedUri = LinphoneUtils.getCVSPathFromLookupUri(fileSharedUri).toString();
                    }
                    addFileToPendingList(fileSharedUri);
                }
            }

            if (getArguments().getString("messageDraft") != null)
                mMessageTextToSend.setText(getArguments().getString("messageDraft"));
        }

        if (savedInstanceState != null) {
            onRestoreInstanceState(savedInstanceState);
        }

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
        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        InputMethodManager inputMethodManager = (InputMethodManager) getActivity().getSystemService(INPUT_METHOD_SERVICE);
        if (getActivity().getCurrentFocus() != null) {
            inputMethodManager.hideSoftInputFromWindow(getActivity().getCurrentFocus().getWindowToken(), 0);
        }

        initChatRoom();
        displayChatRoomHeader();
        displayChatRoomHistory();
        LinphoneManager.getInstance().setCurrentChatRoomAddress(mRemoteSipAddress);
    }

    public void changeDisplayedChat(String sipUri) {
        mRemoteSipUri = sipUri;
        mRemoteSipAddress = LinphoneManager.getLc().createAddress(mRemoteSipUri);

        initChatRoom();
        displayChatRoomHeader();
        displayChatRoomHistory();

        LinphoneManager.getInstance().setCurrentChatRoomAddress(mRemoteSipAddress);
    }

    @Override
    public void onPause() {
        ContactsManager.removeContactsListener(this);
        removeVirtualKeyboardVisiblityListener();
        LinphoneManager.getInstance().setCurrentChatRoomAddress(null);
        if (mChatRoom != null) mChatRoom.removeListener(this);
        if (mEventsAdapter != null) mEventsAdapter.clear();
        super.onPause();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (data != null) {
            if (requestCode == ADD_PHOTO && resultCode == Activity.RESULT_OK) {
                String fileToUploadPath = null;
                if (data != null && data.getData() != null) {
                    if (data.getData().toString().contains("com.android.contacts/contacts/")) {
                        if (LinphoneUtils.getCVSPathFromLookupUri(data.getData().toString()) != null) {
                            fileToUploadPath = LinphoneUtils.getCVSPathFromLookupUri(data.getData().toString()).toString();
                        } else {
                            //TODO Error
                            return;
                        }
                    } else {
                        fileToUploadPath = LinphoneUtils.getRealPathFromURI(getActivity(), data.getData());
                    }
                    if (fileToUploadPath == null) {
                        fileToUploadPath = data.getData().toString();
                    }
                } else if (mImageToUploadUri != null) {
                    fileToUploadPath = mImageToUploadUri.getPath();
                }

                if (LinphoneUtils.isExtensionImage(fileToUploadPath)) {
                    addImageToPendingList(fileToUploadPath);
                } else {
                    if (fileToUploadPath.startsWith("content://") || fileToUploadPath.startsWith("file://")) {
                        fileToUploadPath = LinphoneUtils.getFilePath(getActivity().getApplicationContext(), Uri.parse(fileToUploadPath));
                    } else if (fileToUploadPath.contains("com.android.contacts/contacts/")) {
                        fileToUploadPath = LinphoneUtils.getCVSPathFromLookupUri(fileToUploadPath).toString();
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

    @Override
    public void onDeleteSelection(Object[] objectsToDelete) {
        for (Object obj : objectsToDelete) {
            EventLog eventLog = (EventLog) obj;
            if (eventLog.getType() == EventLog.Type.ConferenceChatMessage) {
                ChatMessage message = eventLog.getChatMessage();
                if (message.getAppdata() != null && !message.isOutgoing()) {
                    File file = new File(message.getAppdata());
                    if (file.exists()) {
                        file.delete(); // Delete downloaded file from incoming message that will be deleted
                    }
                }
            }
            eventLog.deleteFromDatabase();
        }
        if (mChatRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt())) {
            mEventsAdapter.refresh(mChatRoom.getHistoryMessageEvents(MESSAGES_PER_PAGE));
        } else {
            mEventsAdapter.refresh(mChatRoom.getHistoryEvents(MESSAGES_PER_PAGE));
        }
    }


    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        ChatBubbleViewHolder holder = (ChatBubbleViewHolder) v.getTag();
        mContextMenuMessagePosition = holder.getAdapterPosition();

        EventLog event = (EventLog) mEventsAdapter.getItem(mContextMenuMessagePosition);
        if (event.getType() != EventLog.Type.ConferenceChatMessage) {
            return;
        }

        MenuInflater inflater = getActivity().getMenuInflater();
        ChatMessage message = event.getChatMessage();
        if (message.getState() == ChatMessage.State.NotDelivered) {
            inflater.inflate(R.menu.chat_bubble_menu_with_resend, menu);
        } else {
            inflater.inflate(R.menu.chat_bubble_menu, menu);
        }

        if (!message.isOutgoing() || mChatRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt())) {
            // Do not show incoming messages IDMN state in 1 to 1 chat room as we don't receive IMDN for them
            menu.removeItem(R.id.imdn_infos);
        }
        if (!message.hasTextContent()) {
            // Do not show copy text option if message doesn't have any text
            menu.removeItem(R.id.copy_text);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {

        EventLog event = (EventLog) mEventsAdapter.getItem(mContextMenuMessagePosition);

        if (event.getType() != EventLog.Type.ConferenceChatMessage) {
            return super.onContextItemSelected(item);
        }

        ChatMessage message = event.getChatMessage();
        String messageId = message.getMessageId();

        if (item.getItemId() == R.id.resend) {
            mEventsAdapter.removeItem(mContextMenuMessagePosition);
            message.resend();
            return true;
        }
        if (item.getItemId() == R.id.imdn_infos) {
            LinphoneActivity.instance().goToChatMessageImdnInfos(getRemoteSipUri(), messageId);
            return true;
        }
        if (item.getItemId() == R.id.copy_text) {
            if (message.hasTextContent()) {
                ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Message", message.getTextContent());
                clipboard.setPrimaryClip(clip);
            }
            return true;
        }
        if (item.getItemId() == R.id.delete_message) {
            mChatRoom.deleteMessage(message);
            mEventsAdapter.removeItem(mContextMenuMessagePosition);
            return true;
        }
        return super.onContextItemSelected(item);
    }

    private void loadMoreData(final int totalItemsCount) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                int maxSize = mChatRoom.getHistoryEventsSize();
                if (totalItemsCount < maxSize) {
                    int upperBound = totalItemsCount + MESSAGES_PER_PAGE;
                    if (upperBound > maxSize) {
                        upperBound = maxSize;
                    }
                    EventLog[] newLogs = mChatRoom.getHistoryRangeEvents(totalItemsCount, upperBound);
                    ArrayList<EventLog> logsList = new ArrayList<>(Arrays.asList(newLogs));
                    mEventsAdapter.addAllToHistory(logsList);
                }
            }
        });
    }

    /**
     * Keyboard management
     */

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
        LinphoneActivity.instance().hideStatusBar();
        mTopBar.setVisibility(View.GONE);
    }

    public void hideKeyboardVisibleMode() {
        LinphoneActivity.instance().hideTabBar(false);
        LinphoneActivity.instance().showStatusBar();
        mTopBar.setVisibility(View.VISIBLE);
    }

    /**
     * View initialization
     */

    private void setReadOnly() {
        mMessageTextToSend.setEnabled(false);
        mAttachImageButton.setEnabled(false);
        mSendMessageButton.setEnabled(false);
    }

    private void getContactsForParticipants() {
        mParticipants = new ArrayList<>();
        if (mChatRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt())) {
            LinphoneContact c = ContactsManager.getInstance().findContactFromAddress(mRemoteParticipantAddress);
            if (c != null) {
                mParticipants.add(c);
            }
        } else {
            int index = 0;
            StringBuilder participantsLabel = new StringBuilder();
            for (Participant p : mChatRoom.getParticipants()) {
                LinphoneContact c = ContactsManager.getInstance().findContactFromAddress(p.getAddress());
                if (c != null) {
                    mParticipants.add(c);
                    participantsLabel.append(c.getFullName());
                } else {
                    String displayName = LinphoneUtils.getAddressDisplayName(p.getAddress());
                    participantsLabel.append(displayName);
                }
                index++;
                if (index != mChatRoom.getNbParticipants()) participantsLabel.append(", ");
            }
            mParticipantsLabel.setText(participantsLabel.toString());
        }

        if (mEventsAdapter != null) {
            mEventsAdapter.setContacts(mParticipants);
        }
    }

    private void initChatRoom() {
        Core core = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (mRemoteSipAddress == null || mRemoteSipUri == null || mRemoteSipUri.length() == 0 || core == null) {
            //TODO error
            return;
        }
        Address proxyConfigContact = (core.getDefaultProxyConfig() != null) ? core.getDefaultProxyConfig().getContact() : null;
        if (proxyConfigContact != null) {
            mChatRoom = core.findOneToOneChatRoom(proxyConfigContact, mRemoteSipAddress);
        }
        if (mChatRoom == null) {
            mChatRoom = core.getChatRoomFromUri(mRemoteSipAddress.asStringUriOnly());
        }
        mChatRoom.addListener(this);
        mChatRoom.markAsRead();
        LinphoneManager.getInstance().updateUnreadCountForChatRoom(mChatRoom, 0);
        LinphoneActivity.instance().refreshMissedChatCountDisplay();

        mRemoteParticipantAddress = mRemoteSipAddress;
        if (mChatRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt()) && mChatRoom.getParticipants().length > 0) {
            mRemoteParticipantAddress = mChatRoom.getParticipants()[0].getAddress();
        }

        getContactsForParticipants();

        mRemoteComposing.setVisibility(View.GONE);
    }

    private void displayChatRoomHeader() {
        Core core = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (core == null || mChatRoom == null) return;

        if (core.getCallsNb() > 0) {
            mBackToCallButton.setVisibility(View.VISIBLE);
        } else {
            mBackToCallButton.setVisibility(View.GONE);
            if (mChatRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt())) {
                mCallButton.setVisibility(View.VISIBLE);
                mGroupInfosButton.setVisibility(View.GONE);
                mParticipantsLabel.setVisibility(View.GONE);

                if (mParticipants.size() == 0) {
                    // Contact not found
                    String displayName = LinphoneUtils.getAddressDisplayName(mRemoteParticipantAddress);
                    mRoomLabel.setText(displayName);
                } else {
                    mRoomLabel.setText(mParticipants.get(0).getFullName());
                }
            } else {
                mCallButton.setVisibility(View.GONE);
                mGroupInfosButton.setVisibility(View.VISIBLE);
                mRoomLabel.setText(mChatRoom.getSubject());
                mParticipantsLabel.setVisibility(View.VISIBLE);
            }
        }

        if (mChatRoom.hasBeenLeft()) {
            setReadOnly();
        }
    }

    private void displayChatRoomHistory() {

        if (mChatRoom == null) return;
        if (mChatRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt())) {
            mEventsAdapter = new ChatEventsAdapter(this, mSelectionHelper, R.layout.chat_bubble, mChatRoom.getHistoryMessageEvents(MESSAGES_PER_PAGE), mParticipants, this);
        } else {
            mEventsAdapter = new ChatEventsAdapter(this, mSelectionHelper, R.layout.chat_bubble, mChatRoom.getHistoryEvents(MESSAGES_PER_PAGE), mParticipants, this);
        }
        mSelectionHelper.setAdapter(mEventsAdapter);
        mChatEventsList.setAdapter(mEventsAdapter);
        scrollToBottom();
    }

    public void scrollToBottom() {
        mChatEventsList.getLayoutManager().scrollToPosition(0);
    }

    public String getRemoteSipUri() {
        return mRemoteSipUri;
    }

    @Override
    public void onItemClicked(int position) {
        if (mEventsAdapter.isEditionEnabled()) {
            mEventsAdapter.toggleSelection(position);
        }
    }

    /**
     * File transfer related
     */

    @Override
    public void onSaveInstanceState(Bundle outState) {
        String files[] = new String[mFilesUploadLayout.getChildCount()];
        for (int i = 0; i < mFilesUploadLayout.getChildCount(); i++) {
            View child = mFilesUploadLayout.getChildAt(i);
            String path = (String) child.getTag();
            files[i] = path;
        }
        outState.putStringArray("Files", files);
        super.onSaveInstanceState(outState);
    }

    public void onRestoreInstanceState(Bundle savedInstanceState) {
        String files[] = savedInstanceState.getStringArray("Files");
        if (files.length > 0) {
            for (String file : files) {
                if (LinphoneUtils.isExtensionImage(file)) {
                    addImageToPendingList(file);
                } else {
                    addFileToPendingList(file);
                }
            }
        }
    }

    private void pickFile() {
        List<Intent> cameraIntents = new ArrayList<>();
        Intent captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File file = new File(LinphoneUtils.getStorageDirectory(mContext), getString(R.string.temp_photo_name_with_date).replace("%s", String.valueOf(System.currentTimeMillis()) + ".jpeg"));
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

        LinphoneActivity.instance().disableGoToCall();
        startActivityForResult(chooserIntent, ADD_PHOTO);
    }

    private void addFileToPendingList(String path) {
        if (path == null) {
            Log.e("Can't add file to pending list because it's path is null...");
            return;
        }

        View pendingFile = mInflater.inflate(R.layout.file_upload_cell, mFilesUploadLayout, false);
        pendingFile.setTag(path);

        TextView text = pendingFile.findViewById(R.id.pendingFileForUpload);
        String extension = path.substring(path.lastIndexOf('.'));
        text.setText(extension);

        ImageView remove = pendingFile.findViewById(R.id.remove);
        remove.setTag(pendingFile);
        remove.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                View pendingImage = (View) view.getTag();
                mFilesUploadLayout.removeView(pendingImage);
                mAttachImageButton.setEnabled(true);
                mMessageTextToSend.setEnabled(true);
                mSendMessageButton.setEnabled(mMessageTextToSend.getText().length() > 0 || mFilesUploadLayout.getChildCount() > 0);
            }
        });

        mFilesUploadLayout.addView(pendingFile);

        mAttachImageButton.setEnabled(false); // For now limit file per message to 1
        mMessageTextToSend.setEnabled(false); // For now forbid to send both text and picture at the same time
        mSendMessageButton.setEnabled(true);
    }

    private void addImageToPendingList(String path) {
        if (path == null) {
            Log.e("Can't add image to pending list because it's path is null...");
            return;
        }

        View pendingImage = mInflater.inflate(R.layout.image_upload_cell, mFilesUploadLayout, false);
        pendingImage.setTag(path);

        ImageView image = pendingImage.findViewById(R.id.pendingImageForUpload);
        Bitmap bm = BitmapFactory.decodeFile(path);
        if (bm == null) return;
        image.setImageBitmap(bm);

        ImageView remove = pendingImage.findViewById(R.id.remove);
        remove.setTag(pendingImage);
        remove.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                View pendingImage = (View) view.getTag();
                mFilesUploadLayout.removeView(pendingImage);
                mAttachImageButton.setEnabled(true);
                mMessageTextToSend.setEnabled(true);
                mSendMessageButton.setEnabled(mMessageTextToSend.getText().length() > 0 || mFilesUploadLayout.getChildCount() > 0);
            }
        });

        mFilesUploadLayout.addView(pendingImage);

        mAttachImageButton.setEnabled(false); // For now limit file per message to 1
        mMessageTextToSend.setEnabled(false); // For now forbid to send both text and picture at the same time
        mSendMessageButton.setEnabled(true);
    }

    /**
     * Message sending
     */

    private void sendMessage() {
        String text = mMessageTextToSend.getText().toString();

        ChatMessage msg;
        //TODO: rework when we'll send multiple files at once
        if (mFilesUploadLayout.getChildCount() > 0) {
            String filePath = (String) mFilesUploadLayout.getChildAt(0).getTag();
            String fileName = filePath.substring(filePath.lastIndexOf("/") + 1);
            String extension = LinphoneUtils.getExtensionFromFileName(fileName);
            Content content = Factory.instance().createContent();
            if (LinphoneUtils.isExtensionImage(fileName)) {
                content.setType("image");
            } else {
                content.setType("file");
            }
            content.setSubtype(extension);
            content.setName(fileName);
            msg = mChatRoom.createFileTransferMessage(content);
            msg.setFileTransferFilepath(filePath); // Let the file body handler take care of the upload
            msg.setAppdata(filePath);

            if (text != null && text.length() > 0) {
                msg.addTextContent(text);
            }
        } else {
            msg = mChatRoom.createMessage(text);
        }
        // Set listener not required here anymore, message will be added to messages list and adapter will set the listener
        msg.send();

        mFilesUploadLayout.removeAllViews();
        mAttachImageButton.setEnabled(true);
        mMessageTextToSend.setEnabled(true);
        mMessageTextToSend.setText("");
    }

    /*
     * Chat room callbacks
     */

    @Override
    public void onChatMessageSent(ChatRoom cr, EventLog event) {
        mEventsAdapter.addToHistory(event);
        scrollToBottom();
    }

    @Override
    public void onConferenceAddressGeneration(ChatRoom cr) {

    }

    @Override
    public void onParticipantDeviceFetchRequested(ChatRoom cr, Address addr) {

    }

    @Override
    public void onParticipantRegistrationSubscriptionRequested(ChatRoom cr, Address participantAddr) {
    }

    @Override
    public void onParticipantRegistrationUnsubscriptionRequested(ChatRoom cr, Address participantAddr) {
    }

    @Override
    public void onUndecryptableMessageReceived(ChatRoom cr, ChatMessage msg) {
        final Address from = msg.getFromAddress();
        final LinphoneContact contact = ContactsManager.getInstance().findContactFromAddress(from);

        if (LinphoneActivity.instance().isOnBackground()) {
            if (!getResources().getBoolean(R.bool.disable_chat_message_notification)) {
                if (contact != null) {
                    LinphoneService.instance().displayMessageNotification(from.asStringUriOnly(),
                            contact.getFullName(), contact.getThumbnailUri(), getString(R.string.message_cant_be_decrypted_notif));
                } else {
                    LinphoneService.instance().displayMessageNotification(from.asStringUriOnly(),
                            from.getUsername(), null, getString(R.string.message_cant_be_decrypted_notif));
                }
            }
        } else if (LinphoneManager.getLc().limeEnabled() == LimeState.Mandatory) {
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
        LinphoneManager.getInstance().updateUnreadCountForChatRoom(mChatRoom, 0);
        LinphoneActivity.instance().refreshMissedChatCountDisplay();

        ChatMessage msg = event.getChatMessage();
        if (msg.getErrorInfo() != null && msg.getErrorInfo().getReason() == Reason.UnsupportedContent) {
            Log.w("Message received but content is unsupported, do not display it");
            return;
        }

        if (!msg.hasTextContent() && msg.getFileTransferInformation() == null) {
            Log.w("Message has no text or file transfer information to display, ignoring it...");
            return;
        }

        String externalBodyUrl = msg.getExternalBodyUrl();
        Content fileTransferContent = msg.getFileTransferInformation();
        if (externalBodyUrl != null || fileTransferContent != null) {
            LinphoneActivity.instance().checkAndRequestExternalStoragePermission();
        }

        mEventsAdapter.addToHistory(event);
        scrollToBottom();
    }

    @Override
    public void onIsComposingReceived(ChatRoom cr, Address remoteAddr, boolean isComposing) {
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
                LinphoneContact contact = ContactsManager.getInstance().findContactFromAddress(remoteAddr);
                String displayName;
                if (contact != null) {
                    if (contact.getFullName() != null) {
                        displayName = contact.getFullName();
                    } else {
                        displayName = LinphoneUtils.getAddressDisplayName(remoteAddr);
                    }
                } else {
                    displayName = LinphoneUtils.getAddressDisplayName(a);
                }
                composing.add(displayName);
            }
        }

        mRemoteComposing.setVisibility(View.VISIBLE);
        if (composing.size() == 0) {
            mRemoteComposing.setVisibility(View.GONE);
        } else if (composing.size() == 1) {
            mRemoteComposing.setText(getString(R.string.remote_composing_single).replace("%s", composing.get(0)));
        } else {
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
        }
    }

    @Override
    public void onMessageReceived(ChatRoom cr, ChatMessage msg) {

    }

    @Override
    public void onConferenceJoined(ChatRoom cr, EventLog event) {
        // Currently flexisip doesn't send the participants list in the INVITE
        // So we have to refresh the display when information is available
        // In the meantime header will be chatroom-xxxxxxx
        if (mChatRoom == null) mChatRoom = cr;
        if (mChatRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt()) && mChatRoom.getParticipants().length > 0) {
            mRemoteParticipantAddress = mChatRoom.getParticipants()[0].getAddress();
        }
        getContactsForParticipants();
        displayChatRoomHeader();

        mEventsAdapter.addToHistory(event);
        scrollToBottom();
    }

    @Override
    public void onConferenceLeft(ChatRoom cr, EventLog event) {
        mEventsAdapter.addToHistory(event);
        scrollToBottom();
    }

    @Override
    public void onParticipantAdminStatusChanged(ChatRoom cr, EventLog event) {
        mEventsAdapter.addToHistory(event);
        scrollToBottom();
    }

    @Override
    public void onParticipantDeviceRemoved(ChatRoom cr, EventLog event) {

    }

    @Override
    public void onParticipantRemoved(ChatRoom cr, EventLog event) {
        getContactsForParticipants();
        mEventsAdapter.addToHistory(event);
        scrollToBottom();
    }

    @Override
    public void onChatMessageShouldBeStored(ChatRoom cr, ChatMessage msg) {

    }

    @Override
    public void onParticipantsCapabilitiesChecked(ChatRoom cr, Address deviceAddr, Address[] participantsAddr) {

    }

    @Override
    public void onParticipantDeviceAdded(ChatRoom cr, EventLog event) {

    }

    @Override
    public void onStateChanged(ChatRoom cr, ChatRoom.State newState) {
        if (mChatRoom.hasBeenLeft()) {
            setReadOnly();
        }
    }

    @Override
    public void onParticipantAdded(ChatRoom cr, EventLog event) {
        getContactsForParticipants();
        mEventsAdapter.addToHistory(event);
        scrollToBottom();
    }

    @Override
    public void onSubjectChanged(ChatRoom cr, EventLog event) {
        mRoomLabel.setText(event.getSubject());
        mEventsAdapter.addToHistory(event);
        scrollToBottom();
    }

    @Override
    public void onContactsUpdated() {
        getContactsForParticipants();
    }
}
