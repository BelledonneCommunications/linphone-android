package org.linphone.chat;

/*
ChatMessagesFragment.java
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

import static android.content.Context.INPUT_METHOD_SERVICE;
import static org.linphone.fragments.FragmentsAvailable.CHAT;

import android.app.Activity;
import android.app.Dialog;
import android.app.Fragment;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.provider.MediaStore;
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
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.view.inputmethod.InputConnectionCompat;
import androidx.core.view.inputmethod.InputContentInfoCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.LinphoneService;
import org.linphone.R;
import org.linphone.call.CallManager;
import org.linphone.contacts.ContactAddress;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.ContactsUpdatedListener;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatRoom;
import org.linphone.core.ChatRoomCapabilities;
import org.linphone.core.ChatRoomListener;
import org.linphone.core.ChatRoomSecurityLevel;
import org.linphone.core.Content;
import org.linphone.core.Core;
import org.linphone.core.EventLog;
import org.linphone.core.Factory;
import org.linphone.core.LimeState;
import org.linphone.core.Participant;
import org.linphone.core.ParticipantDevice;
import org.linphone.core.Reason;
import org.linphone.core.tools.Log;
import org.linphone.settings.LinphonePreferences;
import org.linphone.utils.FileUtils;
import org.linphone.utils.LinphoneUtils;
import org.linphone.utils.SelectableHelper;
import org.linphone.views.RichEditText;

public class ChatMessagesFragment extends Fragment
        implements ChatRoomListener,
                ContactsUpdatedListener,
                ChatMessageViewHolderClickListener,
                SelectableHelper.DeleteListener,
                RichEditText.RichInputListener {
    private static final int ADD_PHOTO = 1337;
    private static final int MESSAGES_PER_PAGE = 20;
    private static final String INPUT_CONTENT_INFO_KEY = "COMMIT_CONTENT_INPUT_CONTENT_INFO";
    private static final String COMMIT_CONTENT_FLAGS_KEY = "COMMIT_CONTENT_FLAGS";

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private ImageView mBackButton, mCallButton, mBackToCallButton, mGroupInfosButton;
    private ImageView mAttachImageButton, mSendMessageButton;
    private TextView mRoomLabel, mParticipantsLabel, mSipUriLabel, mRemoteComposing;
    private RichEditText mMessageTextToSend;
    private LayoutInflater mInflater;
    private RecyclerView mChatEventsList;
    private LinearLayout mFilesUploadLayout;
    private SelectableHelper mSelectionHelper;
    private Context mContext;
    private ViewTreeObserver.OnGlobalLayoutListener mKeyboardListener;
    private Uri mImageToUploadUri;
    private ChatMessagesAdapter mEventsAdapter;
    private ChatMessagesOldAdapter mOldEventsAdapter;
    private String mLocalSipUri, mRemoteSipUri;
    private Address mLocalSipAddress, mRemoteSipAddress, mRemoteParticipantAddress;
    private ChatRoom mChatRoom;
    private ArrayList<LinphoneContact> mParticipants;
    private LinearLayoutManager layoutManager;
    private int mContextMenuMessagePosition;
    private ChatScrollListener mChatScrollListener;
    private LinearLayout mTopBar;
    private ImageView mChatRoomSecurityLevel;

    private InputContentInfoCompat mCurrentInputContentInfo;
    private int mCurrentFlags;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Retain the fragment across configuration changes
        setRetainInstance(true);

        if (getArguments() != null) {
            if (getArguments().getString("LocalSipUri") != null) {
                mLocalSipUri = getArguments().getString("LocalSipUri");
                mLocalSipAddress = LinphoneManager.getLc().createAddress(mLocalSipUri);
            }
            if (getArguments().getString("RemoteSipUri") != null) {
                mRemoteSipUri = getArguments().getString("RemoteSipUri");
                mRemoteSipAddress = LinphoneManager.getLc().createAddress(mRemoteSipUri);
            }
        }

        mContext = getActivity().getApplicationContext();
        mInflater = inflater;
        View view = inflater.inflate(R.layout.chat, container, false);

        mTopBar = view.findViewById(R.id.top_bar);

        mChatRoomSecurityLevel = view.findViewById(R.id.room_security_level);
        mChatRoomSecurityLevel.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        boolean oneParticipantOneDevice = false;
                        if (mChatRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt())) {
                            ParticipantDevice[] devices =
                                    mChatRoom.getParticipants()[0].getDevices();
                            if (devices.length == 1) {
                                oneParticipantOneDevice = true;
                            }
                        }

                        if (LinphonePreferences.instance().isLimeSecurityPopupEnabled()) {
                            showSecurityDialog(oneParticipantOneDevice);
                        } else {
                            if (oneParticipantOneDevice) {
                                ParticipantDevice device =
                                        mChatRoom.getParticipants()[0].getDevices()[0];
                                CallManager.getInstance().inviteAddress(device.getAddress(), true);
                            } else {
                                LinphoneActivity.instance()
                                        .goToContactDevicesInfos(mLocalSipUri, mRemoteSipUri);
                            }
                        }
                    }
                });

        mBackButton = view.findViewById(R.id.back);
        if (getResources().getBoolean(R.bool.isTablet)) {
            mBackButton.setVisibility(View.INVISIBLE);
        } else {
            mBackButton.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            LinphoneActivity.instance().goToChatList();
                        }
                    });
        }

        mCallButton = view.findViewById(R.id.start_call);
        mCallButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        LinphoneActivity.instance()
                                .setAddresGoToDialerAndCall(
                                        mRemoteParticipantAddress.asString(), null);
                    }
                });

        mBackToCallButton = view.findViewById(R.id.back_to_call);
        mBackToCallButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        LinphoneActivity.instance()
                                .resetClassicMenuLayoutAndGoBackToCallIfStillRunning();
                    }
                });

        mGroupInfosButton = view.findViewById(R.id.group_infos);
        mGroupInfosButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (mChatRoom == null) return;
                        ArrayList<ContactAddress> participants = new ArrayList<>();
                        for (Participant p : mChatRoom.getParticipants()) {
                            Address a = p.getAddress();
                            LinphoneContact c =
                                    ContactsManager.getInstance().findContactFromAddress(a);
                            if (c == null) {
                                c = new LinphoneContact();
                                String displayName = LinphoneUtils.getAddressDisplayName(a);
                                c.setFullName(displayName);
                            }
                            ContactAddress ca =
                                    new ContactAddress(
                                            c, a.asString(), "", c.isFriend(), p.isAdmin());
                            participants.add(ca);
                        }
                        LinphoneActivity.instance()
                                .goToChatGroupInfos(
                                        mRemoteSipAddress.asString(),
                                        participants,
                                        mChatRoom.getSubject(),
                                        mChatRoom.getMe() != null && mChatRoom.getMe().isAdmin(),
                                        false,
                                        null,
                                        mChatRoom.hasCapability(
                                                ChatRoomCapabilities.Encrypted.toInt()));
                    }
                });

        mRoomLabel = view.findViewById(R.id.subject);
        mParticipantsLabel = view.findViewById(R.id.participants);
        mSipUriLabel = view.findViewById(R.id.sipUri);

        mFilesUploadLayout = view.findViewById(R.id.file_upload_layout);

        mAttachImageButton = view.findViewById(R.id.send_picture);
        mAttachImageButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        LinphoneActivity.instance().checkAndRequestPermissionsToSendImage();
                        pickFile();
                    }
                });

        mSendMessageButton = view.findViewById(R.id.send_message);
        mSendMessageButton.setEnabled(false);
        mSendMessageButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        sendMessage();
                    }
                });

        mMessageTextToSend = view.findViewById(R.id.message);
        mMessageTextToSend.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence charSequence, int i, int i1, int i2) {}

                    @Override
                    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                        mSendMessageButton.setEnabled(
                                mMessageTextToSend.getText().length() > 0
                                        || mFilesUploadLayout.getChildCount() > 0);
                        if (mChatRoom != null && mMessageTextToSend.getText().length() > 0) {
                            if (!getResources().getBoolean(R.bool.allow_multiple_images_and_text)) {
                                mAttachImageButton.setEnabled(false);
                            }
                            mChatRoom.compose();
                        } else {
                            mAttachImageButton.setEnabled(true);
                        }
                    }

                    @Override
                    public void afterTextChanged(Editable editable) {}
                });
        mMessageTextToSend.clearFocus();
        mMessageTextToSend.setListener(this);

        mRemoteComposing = view.findViewById(R.id.remote_composing);

        mChatEventsList = view.findViewById(R.id.chat_message_list);
        mSelectionHelper = new SelectableHelper(view, this);
        layoutManager =
                new LinphoneLinearLayoutManager(mContext, LinearLayoutManager.VERTICAL, true);
        mChatEventsList.setLayoutManager(layoutManager);

        mChatScrollListener =
                new ChatScrollListener(layoutManager) {
                    @Override
                    public void onLoadMore(int totalItemsCount) {
                        loadMoreData(totalItemsCount);
                    }
                };
        mChatEventsList.addOnScrollListener(mChatScrollListener);

        if (getArguments() != null) {
            String fileSharedUri = getArguments().getString("fileSharedUri");
            if (fileSharedUri != null) {
                Log.i("[ChatMessages] Found shared file(s): " + fileSharedUri);
                if (fileSharedUri.contains(":")) {
                    String[] files = fileSharedUri.split(":");
                    for (String file : files) {
                        addFileIntoSharingArea(file);
                    }
                } else {
                    addFileIntoSharingArea(fileSharedUri);
                }
            }

            if (getArguments().getString("messageDraft") != null) {
                String sharedText = getArguments().getString("messageDraft");
                mMessageTextToSend.setText(sharedText);
                Log.i("[ChatMessages] Found shared text: " + sharedText);
            }
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
        ContactsManager.getInstance().addContactsListener(this);

        addVirtualKeyboardVisiblityListener();
        // Force hide keyboard
        getActivity()
                .getWindow()
                .setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        InputMethodManager inputMethodManager =
                (InputMethodManager) getActivity().getSystemService(INPUT_METHOD_SERVICE);
        if (getActivity().getCurrentFocus() != null) {
            inputMethodManager.hideSoftInputFromWindow(
                    getActivity().getCurrentFocus().getWindowToken(), 0);
        }

        initChatRoom();
        displayChatRoomHeader();
        displayChatRoomHistory();
        LinphoneManager.getInstance().setCurrentChatRoomAddress(mRemoteSipAddress);

        if (LinphoneManager.getInstance().hasLastCallSasBeenRejected()) {
            LinphoneManager.getInstance().lastCallSasRejected(false);
            LinphoneUtils.showTrustDeniedDialog(getActivity());
        }
    }

    public void changeDisplayedChat(String localSipUri, String remoteSipUri) {
        mLocalSipUri = localSipUri;
        mLocalSipAddress = LinphoneManager.getLc().createAddress(mLocalSipUri);
        mRemoteSipUri = remoteSipUri;
        mRemoteSipAddress = LinphoneManager.getLc().createAddress(mRemoteSipUri);

        initChatRoom();
        displayChatRoomHeader();
        displayChatRoomHistory();

        LinphoneManager.getInstance().setCurrentChatRoomAddress(mRemoteSipAddress);
    }

    @Override
    public void onPause() {
        ContactsManager.getInstance().removeContactsListener(this);
        removeVirtualKeyboardVisiblityListener();
        LinphoneManager.getInstance().setCurrentChatRoomAddress(null);
        if (mChatRoom != null) mChatRoom.removeListener(this);
        if (mChatEventsList.getAdapter() != null)
            ((ChatMessagesGenericAdapter) mChatEventsList.getAdapter()).clear();
        super.onPause();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (data != null) {
            if (requestCode == ADD_PHOTO && resultCode == Activity.RESULT_OK) {
                String fileToUploadPath = null;
                if (data.getData() != null) {
                    if (data.getData().toString().contains("com.android.contacts/contacts/")) {
                        if (FileUtils.getCVSPathFromLookupUri(data.getData().toString()) != null) {
                            fileToUploadPath =
                                    FileUtils.getCVSPathFromLookupUri(data.getData().toString())
                                            .toString();
                        } else {
                            // TODO Error
                            return;
                        }
                    } else {
                        fileToUploadPath =
                                FileUtils.getRealPathFromURI(getActivity(), data.getData());
                    }
                    if (fileToUploadPath == null) {
                        fileToUploadPath = data.getData().toString();
                    }
                } else if (mImageToUploadUri != null) {
                    fileToUploadPath = mImageToUploadUri.getPath();
                }

                if (fileToUploadPath.startsWith("content://")
                        || fileToUploadPath.startsWith("file://")) {
                    fileToUploadPath =
                            FileUtils.getFilePath(
                                    getActivity().getApplicationContext(),
                                    Uri.parse(fileToUploadPath));
                } else if (fileToUploadPath.contains("com.android.contacts/contacts/")) {
                    fileToUploadPath =
                            FileUtils.getCVSPathFromLookupUri(fileToUploadPath).toString();
                }

                if (FileUtils.isExtensionImage(fileToUploadPath)) {
                    addImageToPendingList(fileToUploadPath);
                } else {
                    addFileToPendingList(fileToUploadPath);
                }
            } else {
                super.onActivityResult(requestCode, resultCode, data);
            }
        } else {
            if (FileUtils.isExtensionImage(mImageToUploadUri.getPath())) {
                File file = new File(mImageToUploadUri.getPath());
                if (file.exists()) {
                    addImageToPendingList(mImageToUploadUri.getPath());
                }
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
                        // Delete downloaded file from incoming message that will be deleted
                        file.delete();
                    }
                }
            }
            eventLog.deleteFromDatabase();
        }
        if (mChatRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt())) {
            ((ChatMessagesGenericAdapter) mChatEventsList.getAdapter())
                    .refresh(mChatRoom.getHistoryMessageEvents(MESSAGES_PER_PAGE));
        } else {
            ((ChatMessagesGenericAdapter) mChatEventsList.getAdapter())
                    .refresh(mChatRoom.getHistoryEvents(MESSAGES_PER_PAGE));
        }
    }

    @Override
    public void onCreateContextMenu(
            ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        if (mContext.getResources().getBoolean(R.bool.use_new_chat_bubbles_layout)) {
            ChatMessageViewHolder holder = (ChatMessageViewHolder) v.getTag();
            mContextMenuMessagePosition = holder.getAdapterPosition();
        } else {
            ChatMessageOldViewHolder holder = (ChatMessageOldViewHolder) v.getTag();
            mContextMenuMessagePosition = holder.getAdapterPosition();
        }

        EventLog event =
                (EventLog)
                        ((ChatMessagesGenericAdapter) mChatEventsList.getAdapter())
                                .getItem(mContextMenuMessagePosition);
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

        if (!message.isOutgoing()
                || mChatRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt())) {
            // Do not show incoming messages IDMN state in 1 to 1 chat room as we don't receive IMDN
            // for them
            menu.removeItem(R.id.imdn_infos);
        }
        if (!message.hasTextContent()) {
            // Do not show copy text option if message doesn't have any text
            menu.removeItem(R.id.copy_text);
        }

        if (!message.isOutgoing()) {
            Address address = message.getFromAddress();
            LinphoneContact contact = ContactsManager.getInstance().findContactFromAddress(address);
            if (contact != null) {
                menu.removeItem(R.id.add_to_contacts);
            }
        } else {
            menu.removeItem(R.id.add_to_contacts);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        EventLog event =
                (EventLog)
                        ((ChatMessagesGenericAdapter) mChatEventsList.getAdapter())
                                .getItem(mContextMenuMessagePosition);

        if (event.getType() != EventLog.Type.ConferenceChatMessage) {
            return super.onContextItemSelected(item);
        }

        ChatMessage message = event.getChatMessage();
        String messageId = message.getMessageId();

        if (item.getItemId() == R.id.resend) {
            ((ChatMessagesGenericAdapter) mChatEventsList.getAdapter())
                    .removeItem(mContextMenuMessagePosition);
            message.resend();
            return true;
        }
        if (item.getItemId() == R.id.imdn_infos) {
            LinphoneActivity.instance()
                    .goToChatMessageImdnInfos(mLocalSipUri, mRemoteSipUri, messageId);
            return true;
        }
        if (item.getItemId() == R.id.copy_text) {
            if (message.hasTextContent()) {
                ClipboardManager clipboard =
                        (ClipboardManager)
                                getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Message", message.getTextContent());
                clipboard.setPrimaryClip(clip);
            }
            return true;
        }
        if (item.getItemId() == R.id.delete_message) {
            mChatRoom.deleteMessage(message);
            ((ChatMessagesGenericAdapter) mChatEventsList.getAdapter())
                    .removeItem(mContextMenuMessagePosition);
            return true;
        }
        if (item.getItemId() == R.id.add_to_contacts) {
            Address address = message.getFromAddress();
            if (address == null) return true;
            String uri = address.getUsername() + "@" + address.getDomain(); // Get a clean address
            if (address.getDisplayName() != null) {
                LinphoneActivity.instance()
                        .displayContactsForEdition(uri, address.getDisplayName());
            } else {
                LinphoneActivity.instance().displayContactsForEdition(uri);
            }
            return true;
        }
        return super.onContextItemSelected(item);
    }

    private void addFileIntoSharingArea(String fileSharedUri) {
        if (FileUtils.isExtensionImage(fileSharedUri)) {
            addImageToPendingList(fileSharedUri);
        } else {
            if (fileSharedUri.startsWith("content://") || fileSharedUri.startsWith("file://")) {
                fileSharedUri =
                        FileUtils.getFilePath(
                                getActivity().getApplicationContext(), Uri.parse(fileSharedUri));
            } else if (fileSharedUri.contains("com.android.contacts/contacts/")) {
                fileSharedUri = FileUtils.getCVSPathFromLookupUri(fileSharedUri).toString();
            }
            addFileToPendingList(fileSharedUri);
        }
    }

    private void loadMoreData(final int totalItemsCount) {
        mHandler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        int maxSize;
                        if (mChatRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt())) {
                            maxSize = mChatRoom.getHistorySize();
                        } else {
                            maxSize = mChatRoom.getHistoryEventsSize();
                        }
                        if (totalItemsCount < maxSize) {
                            int upperBound = totalItemsCount + MESSAGES_PER_PAGE;
                            if (upperBound > maxSize) {
                                upperBound = maxSize;
                            }
                            EventLog[] newLogs;
                            if (mChatRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt())) {
                                newLogs =
                                        mChatRoom.getHistoryRangeMessageEvents(
                                                totalItemsCount, upperBound);
                            } else {
                                newLogs =
                                        mChatRoom.getHistoryRangeEvents(
                                                totalItemsCount, upperBound);
                            }
                            ArrayList<EventLog> logsList = new ArrayList<>(Arrays.asList(newLogs));
                            ((ChatMessagesGenericAdapter) mChatEventsList.getAdapter())
                                    .addAllToHistory(logsList);
                        }
                    }
                });
    }

    /** Keyboard management */
    private void addVirtualKeyboardVisiblityListener() {
        mKeyboardListener =
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        Rect visibleArea = new Rect();
                        getActivity()
                                .getWindow()
                                .getDecorView()
                                .getWindowVisibleDisplayFrame(visibleArea);

                        int screenHeight =
                                getActivity().getWindow().getDecorView().getRootView().getHeight();
                        int heightDiff = screenHeight - (visibleArea.bottom - visibleArea.top);
                        if (heightDiff > screenHeight * 0.15) {
                            showKeyboardVisibleMode();
                        } else {
                            hideKeyboardVisibleMode();
                        }
                    }
                };
        getActivity()
                .getWindow()
                .getDecorView()
                .getViewTreeObserver()
                .addOnGlobalLayoutListener(mKeyboardListener);
    }

    private void removeVirtualKeyboardVisiblityListener() {
        getActivity()
                .getWindow()
                .getDecorView()
                .getViewTreeObserver()
                .removeOnGlobalLayoutListener(mKeyboardListener);
        hideKeyboardVisibleMode();
    }

    private void showKeyboardVisibleMode() {
        LinphoneActivity.instance().hideTabBar(true);
        LinphoneActivity.instance().hideStatusBar();
        mTopBar.setVisibility(View.GONE);
    }

    private void hideKeyboardVisibleMode() {
        LinphoneActivity.instance()
                .hideTabBar(
                        getResources().getBoolean(R.bool.hide_bottom_bar_on_second_level_views));
        LinphoneActivity.instance().showStatusBar();
        mTopBar.setVisibility(View.VISIBLE);
    }

    /** View initialization */
    private void setReadOnly() {
        mMessageTextToSend.setEnabled(false);
        mAttachImageButton.setEnabled(false);
        mSendMessageButton.setEnabled(false);
    }

    private void getContactsForParticipants() {
        mParticipants = new ArrayList<>();
        if (mChatRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt())) {
            LinphoneContact c =
                    ContactsManager.getInstance().findContactFromAddress(mRemoteParticipantAddress);
            if (c != null) {
                mParticipants.add(c);
            }
        } else {
            int index = 0;
            StringBuilder participantsLabel = new StringBuilder();
            for (Participant p : mChatRoom.getParticipants()) {
                LinphoneContact c =
                        ContactsManager.getInstance().findContactFromAddress(p.getAddress());
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

        if (mChatEventsList.getAdapter() != null) {
            ((ChatMessagesGenericAdapter) mChatEventsList.getAdapter()).setContacts(mParticipants);
        }
    }

    private void initChatRoom() {
        if (mChatRoom != null) {
            // Required on tablets
            mChatRoom.removeListener(this);
        }

        Core core = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (mRemoteSipAddress == null
                || mRemoteSipUri == null
                || mRemoteSipUri.length() == 0
                || core == null) {
            // TODO error
            return;
        }

        if (mLocalSipAddress != null) {
            mChatRoom = core.getChatRoom(mRemoteSipAddress, mLocalSipAddress);
        } else {
            mChatRoom = core.getChatRoomFromUri(mRemoteSipAddress.asStringUriOnly());
        }
        mChatRoom.addListener(this);
        mChatRoom.markAsRead();
        LinphoneManager.getInstance().updateUnreadCountForChatRoom(mChatRoom, 0);
        LinphoneActivity.instance().refreshMissedChatCountDisplay();

        mRemoteParticipantAddress = mRemoteSipAddress;
        if (mChatRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt())
                && mChatRoom.getParticipants().length > 0) {
            mRemoteParticipantAddress = mChatRoom.getParticipants()[0].getAddress();
        }

        getContactsForParticipants();

        mRemoteComposing.setVisibility(View.GONE);
    }

    private void displayChatRoomHeader() {
        Core core = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (core == null || mChatRoom == null) return;

        if (mChatRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt())) {
            mCallButton.setVisibility(View.VISIBLE);
            mGroupInfosButton.setVisibility(View.GONE);
            mParticipantsLabel.setVisibility(View.GONE);

            if (mContext.getResources().getBoolean(R.bool.show_sip_uri_in_chat)) {
                mSipUriLabel.setVisibility(View.VISIBLE);
            } else {
                mSipUriLabel.setVisibility(View.GONE);
                mRoomLabel.setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                mSipUriLabel.setVisibility(
                                        mSipUriLabel.getVisibility() == View.VISIBLE
                                                ? View.GONE
                                                : View.VISIBLE);
                            }
                        });
            }

            if (mParticipants.size() == 0) {
                // Contact not found
                String displayName = LinphoneUtils.getAddressDisplayName(mRemoteParticipantAddress);
                mRoomLabel.setText(displayName);
            } else {
                mRoomLabel.setText(mParticipants.get(0).getFullName());
            }
            mSipUriLabel.setText(mRemoteParticipantAddress.asStringUriOnly());
        } else {
            mCallButton.setVisibility(View.GONE);
            mGroupInfosButton.setVisibility(View.VISIBLE);
            mRoomLabel.setText(mChatRoom.getSubject());
            mParticipantsLabel.setVisibility(View.VISIBLE);
            mSipUriLabel.setVisibility(View.GONE);
        }

        mBackToCallButton.setVisibility(View.GONE);
        if (core.getCallsNb() > 0) {
            mBackToCallButton.setVisibility(View.VISIBLE);
        }

        if (mChatRoom.hasBeenLeft()) {
            setReadOnly();
        }

        updateSecurityLevelIcon();
    }

    private void updateSecurityLevelIcon() {
        if (!mChatRoom.hasCapability(ChatRoomCapabilities.Encrypted.toInt())) {
            mChatRoomSecurityLevel.setVisibility(View.GONE);
        } else {
            mChatRoomSecurityLevel.setVisibility(View.VISIBLE);
            ChatRoomSecurityLevel level = mChatRoom.getSecurityLevel();
            switch (level) {
                case Safe:
                    mChatRoomSecurityLevel.setImageResource(R.drawable.security_2_indicator);
                    break;
                case Encrypted:
                    mChatRoomSecurityLevel.setImageResource(R.drawable.security_1_indicator);
                    break;
                case ClearText:
                case Unsafe:
                    mChatRoomSecurityLevel.setImageResource(R.drawable.security_alert_indicator);
                    break;
            }
        }
    }

    private void displayChatRoomHistory() {
        if (mChatRoom == null) return;
        if (mChatRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt())) {
            mEventsAdapter =
                    new ChatMessagesAdapter(
                            this,
                            mSelectionHelper,
                            R.layout.chat_bubble,
                            mChatRoom.getHistoryMessageEvents(MESSAGES_PER_PAGE),
                            mParticipants,
                            this);
            mOldEventsAdapter =
                    new ChatMessagesOldAdapter(
                            this,
                            mSelectionHelper,
                            R.layout.chat_bubble_old,
                            mChatRoom.getHistoryMessageEvents(MESSAGES_PER_PAGE),
                            mParticipants,
                            this);
        } else {
            mEventsAdapter =
                    new ChatMessagesAdapter(
                            this,
                            mSelectionHelper,
                            R.layout.chat_bubble,
                            mChatRoom.getHistoryEvents(MESSAGES_PER_PAGE),
                            mParticipants,
                            this);
            mOldEventsAdapter =
                    new ChatMessagesOldAdapter(
                            this,
                            mSelectionHelper,
                            R.layout.chat_bubble_old,
                            mChatRoom.getHistoryEvents(MESSAGES_PER_PAGE),
                            mParticipants,
                            this);
        }
        if (mContext.getResources().getBoolean(R.bool.use_new_chat_bubbles_layout)) {
            mSelectionHelper.setAdapter(mEventsAdapter);
            mChatEventsList.setAdapter(mEventsAdapter);
        } else {
            mSelectionHelper.setAdapter(mOldEventsAdapter);
            mChatEventsList.setAdapter(mOldEventsAdapter);
        }
        scrollToBottom();
    }

    private void showSecurityDialog(boolean oneParticipantOneDevice) {
        final Dialog dialog =
                LinphoneActivity.instance().displayDialog(getString(R.string.lime_security_popup));
        Button delete = dialog.findViewById(R.id.dialog_delete_button);
        delete.setVisibility(View.GONE);
        Button ok = dialog.findViewById(R.id.dialog_ok_button);
        ok.setText(oneParticipantOneDevice ? getString(R.string.call) : getString(R.string.ok));
        ok.setVisibility(View.VISIBLE);
        Button cancel = dialog.findViewById(R.id.dialog_cancel_button);
        cancel.setText(getString(R.string.cancel));

        dialog.findViewById(R.id.dialog_do_not_ask_again_layout).setVisibility(View.VISIBLE);
        final CheckBox doNotAskAgain = dialog.findViewById(R.id.doNotAskAgain);
        dialog.findViewById(R.id.doNotAskAgainLabel)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                doNotAskAgain.setChecked(!doNotAskAgain.isChecked());
                            }
                        });

        ok.setTag(oneParticipantOneDevice);
        ok.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        boolean oneParticipantOneDevice = (boolean) view.getTag();
                        if (doNotAskAgain.isChecked()) {
                            LinphonePreferences.instance().enableLimeSecurityPopup(false);
                        }

                        if (oneParticipantOneDevice) {
                            ParticipantDevice device =
                                    mChatRoom.getParticipants()[0].getDevices()[0];
                            CallManager.getInstance().inviteAddress(device.getAddress(), true);
                        } else {
                            LinphoneActivity.instance()
                                    .goToContactDevicesInfos(mLocalSipUri, mRemoteSipUri);
                        }

                        dialog.dismiss();
                    }
                });

        cancel.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (doNotAskAgain.isChecked()) {
                            LinphonePreferences.instance().enableLimeSecurityPopup(false);
                        }
                        dialog.dismiss();
                    }
                });
        dialog.show();
    }

    private void scrollToBottom() {
        mChatEventsList.getLayoutManager().scrollToPosition(0);
    }

    @Override
    public void onItemClicked(int position) {
        if (mSelectionHelper.getAdapter().isEditionEnabled()) {
            mSelectionHelper.getAdapter().toggleSelection(position);
        }
    }

    /** File transfer related */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (mFilesUploadLayout != null) {
            String files[] = new String[mFilesUploadLayout.getChildCount()];
            for (int i = 0; i < mFilesUploadLayout.getChildCount(); i++) {
                View child = mFilesUploadLayout.getChildAt(i);
                String path = (String) child.getTag();
                files[i] = path;
            }
            outState.putStringArray("Files", files);
        }

        if (mCurrentInputContentInfo != null) {
            outState.putParcelable(
                    INPUT_CONTENT_INFO_KEY, (Parcelable) mCurrentInputContentInfo.unwrap());
            outState.putInt(COMMIT_CONTENT_FLAGS_KEY, mCurrentFlags);
        }
        mCurrentInputContentInfo = null;
        mCurrentFlags = 0;
        super.onSaveInstanceState(outState);
    }

    private void onRestoreInstanceState(Bundle savedInstanceState) {
        String files[] = savedInstanceState.getStringArray("Files");
        if (files != null && files.length > 0) {
            for (String file : files) {
                if (FileUtils.isExtensionImage(file)) {
                    addImageToPendingList(file);
                } else {
                    addFileToPendingList(file);
                }
            }
        }

        final InputContentInfoCompat previousInputContentInfo =
                InputContentInfoCompat.wrap(
                        savedInstanceState.getParcelable(INPUT_CONTENT_INFO_KEY));
        final int previousFlags = savedInstanceState.getInt(COMMIT_CONTENT_FLAGS_KEY);
        if (previousInputContentInfo != null) {
            onCommitContentInternal(previousInputContentInfo, previousFlags);
        }
    }

    private void pickFile() {
        List<Intent> cameraIntents = new ArrayList<>();
        Intent captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File file =
                new File(
                        FileUtils.getStorageDirectory(mContext),
                        getString(R.string.temp_photo_name_with_date)
                                .replace(
                                        "%s",
                                        String.valueOf(System.currentTimeMillis()) + ".jpeg"));
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

        Intent chooserIntent =
                Intent.createChooser(galleryIntent, getString(R.string.image_picker_title));
        chooserIntent.putExtra(
                Intent.EXTRA_INITIAL_INTENTS, cameraIntents.toArray(new Parcelable[] {}));

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
        remove.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        View pendingImage = (View) view.getTag();
                        mFilesUploadLayout.removeView(pendingImage);
                        mAttachImageButton.setEnabled(true);
                        mMessageTextToSend.setEnabled(true);
                        mSendMessageButton.setEnabled(
                                mMessageTextToSend.getText().length() > 0
                                        || mFilesUploadLayout.getChildCount() > 0);
                    }
                });

        mFilesUploadLayout.addView(pendingFile);

        if (!getResources().getBoolean(R.bool.allow_multiple_images_and_text)) {
            mAttachImageButton.setEnabled(false);
            mMessageTextToSend.setEnabled(false);
        }
        mSendMessageButton.setEnabled(true);
    }

    private void addImageToPendingList(String path) {
        if (path == null) {
            Log.e("Can't add image to pending list because it's path is null...");
            return;
        }

        View pendingImage =
                mInflater.inflate(R.layout.image_upload_cell, mFilesUploadLayout, false);
        pendingImage.setTag(path);

        ImageView image = pendingImage.findViewById(R.id.pendingImageForUpload);
        Glide.with(mContext).load(path).into(image);

        ImageView remove = pendingImage.findViewById(R.id.remove);
        remove.setTag(pendingImage);
        remove.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        View pendingImage = (View) view.getTag();
                        mFilesUploadLayout.removeView(pendingImage);
                        mAttachImageButton.setEnabled(true);
                        mMessageTextToSend.setEnabled(true);
                        mSendMessageButton.setEnabled(
                                mMessageTextToSend.getText().length() > 0
                                        || mFilesUploadLayout.getChildCount() > 0);
                    }
                });

        mFilesUploadLayout.addView(pendingImage);

        if (!getResources().getBoolean(R.bool.allow_multiple_images_and_text)) {
            mAttachImageButton.setEnabled(false);
            mMessageTextToSend.setEnabled(false);
        }
        mSendMessageButton.setEnabled(true);
    }

    /** Message sending */
    private void sendMessage() {
        ChatMessage msg = mChatRoom.createEmptyMessage();
        boolean isBasicChatRoom = mChatRoom.hasCapability(ChatRoomCapabilities.Basic.toInt());
        boolean sendMultipleImagesAsDifferentMessages =
                getResources().getBoolean(R.bool.send_multiple_images_as_different_messages);
        boolean sendImageAndTextAsDifferentMessages =
                getResources().getBoolean(R.bool.send_text_and_images_as_different_messages);

        String text = mMessageTextToSend.getText().toString();
        boolean hasText = text != null && text.length() > 0;

        int filesCount = mFilesUploadLayout.getChildCount();
        for (int i = 0; i < filesCount; i++) {
            String filePath = (String) mFilesUploadLayout.getChildAt(i).getTag();
            String fileName = filePath.substring(filePath.lastIndexOf("/") + 1);
            String extension = FileUtils.getExtensionFromFileName(fileName);
            Content content = Factory.instance().createContent();
            if (FileUtils.isExtensionImage(fileName)) {
                content.setType("image");
            } else {
                content.setType("file");
            }
            content.setSubtype(extension);
            content.setName(fileName);
            content.setFilePath(filePath); // Let the file body handler take care of the upload

            boolean split =
                    isBasicChatRoom; // Always split contents in basic chat rooms for compatibility
            if (hasText && sendImageAndTextAsDifferentMessages) {
                split = true;
            } else if (mFilesUploadLayout.getChildCount() > 1
                    && sendMultipleImagesAsDifferentMessages) {
                split = true;

                // Allow the last image to be sent with text if image and text at the same time OK
                if (hasText && i == filesCount - 1) {
                    split = false;
                }
            }

            if (split) {
                ChatMessage fileMessage = mChatRoom.createFileTransferMessage(content);
                fileMessage.send();
            } else {
                msg.addFileContent(content);
            }
        }

        if (hasText) {
            msg.addTextContent(text);
        }

        // Set listener not required here anymore, message will be added to messages list and
        // adapter will set the listener
        if (msg.getContents().length > 0) {
            msg.send();
        }

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
        ((ChatMessagesGenericAdapter) mChatEventsList.getAdapter()).addToHistory(event);
        scrollToBottom();
    }

    @Override
    public void onConferenceAddressGeneration(ChatRoom cr) {}

    @Override
    public void onParticipantRegistrationSubscriptionRequested(
            ChatRoom cr, Address participantAddr) {}

    @Override
    public void onParticipantRegistrationUnsubscriptionRequested(
            ChatRoom cr, Address participantAddr) {}

    @Override
    public void onUndecryptableMessageReceived(ChatRoom cr, ChatMessage msg) {
        final Address from = msg.getFromAddress();
        final LinphoneContact contact = ContactsManager.getInstance().findContactFromAddress(from);

        if (LinphoneActivity.instance().isOnBackground()) {
            if (!getResources().getBoolean(R.bool.disable_chat_message_notification)) {
                if (contact != null) {
                    LinphoneService.instance()
                            .getNotificationManager()
                            .displayMessageNotification(
                                    from.asStringUriOnly(),
                                    contact.getFullName(),
                                    contact.getThumbnailUri(),
                                    getString(R.string.message_cant_be_decrypted_notif),
                                    cr.getLocalAddress(),
                                    msg.getTime(),
                                    null,
                                    null);
                } else {
                    LinphoneService.instance()
                            .getNotificationManager()
                            .displayMessageNotification(
                                    from.asStringUriOnly(),
                                    from.getUsername(),
                                    null,
                                    getString(R.string.message_cant_be_decrypted_notif),
                                    cr.getLocalAddress(),
                                    msg.getTime(),
                                    null,
                                    null);
                }
            }
        } else if (LinphoneManager.getLc().limeEnabled() == LimeState.Mandatory) {
            final Dialog dialog =
                    LinphoneActivity.instance()
                            .displayDialog(
                                    getString(R.string.message_cant_be_decrypted)
                                            .replace(
                                                    "%s",
                                                    (contact != null)
                                                            ? contact.getFullName()
                                                            : from.getUsername()));
            Button delete = dialog.findViewById(R.id.dialog_delete_button);
            delete.setText(getString(R.string.call));
            Button cancel = dialog.findViewById(R.id.dialog_cancel_button);
            cancel.setText(getString(R.string.ok));
            delete.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            LinphoneManager.getInstance()
                                    .newOutgoingCall(
                                            from.asStringUriOnly(),
                                            (contact != null)
                                                    ? contact.getFullName()
                                                    : from.getUsername());
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
    }

    @Override
    public void onChatMessageReceived(ChatRoom cr, EventLog event) {
        cr.markAsRead();
        LinphoneManager.getInstance().updateUnreadCountForChatRoom(mChatRoom, 0);
        LinphoneActivity.instance().refreshMissedChatCountDisplay();

        ChatMessage msg = event.getChatMessage();
        if (msg.getErrorInfo() != null
                && msg.getErrorInfo().getReason() == Reason.UnsupportedContent) {
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

        ((ChatMessagesGenericAdapter) mChatEventsList.getAdapter()).addToHistory(event);
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
                LinphoneContact contact =
                        ContactsManager.getInstance().findContactFromAddress(remoteAddr);
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
            mRemoteComposing.setText(
                    getString(R.string.remote_composing_single).replace("%s", composing.get(0)));
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
            mRemoteComposing.setText(
                    getString(R.string.remote_composing_multiple)
                            .replace("%s", remotes.toString()));
        }
    }

    @Override
    public void onMessageReceived(ChatRoom cr, ChatMessage msg) {}

    @Override
    public void onConferenceJoined(ChatRoom cr, EventLog event) {
        // Currently flexisip doesn't send the participants list in the INVITE
        // So we have to refresh the display when information is available
        // In the meantime header will be chatroom-xxxxxxx
        if (mChatRoom == null) mChatRoom = cr;
        if (mChatRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt())
                && mChatRoom.getParticipants().length > 0) {
            mRemoteParticipantAddress = mChatRoom.getParticipants()[0].getAddress();
        }
        getContactsForParticipants();
        displayChatRoomHeader();

        if (mChatRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt())) return;
        ((ChatMessagesGenericAdapter) mChatEventsList.getAdapter()).addToHistory(event);
        scrollToBottom();
    }

    @Override
    public void onConferenceLeft(ChatRoom cr, EventLog event) {
        if (mChatRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt())) return;
        ((ChatMessagesGenericAdapter) mChatEventsList.getAdapter()).addToHistory(event);
        scrollToBottom();
    }

    @Override
    public void onParticipantAdminStatusChanged(ChatRoom cr, EventLog event) {
        if (mChatRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt())) return;
        ((ChatMessagesGenericAdapter) mChatEventsList.getAdapter()).addToHistory(event);
        scrollToBottom();
    }

    @Override
    public void onParticipantDeviceRemoved(ChatRoom cr, EventLog event) {}

    @Override
    public void onParticipantRemoved(ChatRoom cr, EventLog event) {
        if (mChatRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt())) return;
        getContactsForParticipants();
        ((ChatMessagesGenericAdapter) mChatEventsList.getAdapter()).addToHistory(event);
        scrollToBottom();
    }

    @Override
    public void onChatMessageShouldBeStored(ChatRoom cr, ChatMessage msg) {}

    @Override
    public void onParticipantDeviceAdded(ChatRoom cr, EventLog event) {}

    @Override
    public void onSecurityEvent(ChatRoom cr, EventLog eventLog) {
        updateSecurityLevelIcon();
        ((ChatMessagesGenericAdapter) mChatEventsList.getAdapter()).addToHistory(eventLog);
        scrollToBottom();
    }

    @Override
    public void onStateChanged(ChatRoom cr, ChatRoom.State newState) {
        if (mChatRoom.hasBeenLeft()) {
            setReadOnly();
        }
    }

    @Override
    public void onParticipantAdded(ChatRoom cr, EventLog event) {
        if (mChatRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt())) return;
        getContactsForParticipants();
        ((ChatMessagesGenericAdapter) mChatEventsList.getAdapter()).addToHistory(event);
        scrollToBottom();
    }

    @Override
    public void onSubjectChanged(ChatRoom cr, EventLog event) {
        if (mChatRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt())) return;
        mRoomLabel.setText(event.getSubject());
        ((ChatMessagesGenericAdapter) mChatEventsList.getAdapter()).addToHistory(event);
        scrollToBottom();
    }

    @Override
    public void onContactsUpdated() {
        getContactsForParticipants();
    }

    @Override
    public boolean onCommitContent(
            InputContentInfoCompat inputContentInfo,
            int flags,
            Bundle opts,
            String[] contentMimeTypes) {
        try {
            if (mCurrentInputContentInfo != null) {
                mCurrentInputContentInfo.releasePermission();
            }
        } catch (Exception e) {
            Log.e("[TimelineFragment] releasePermission failed : ", e);
        } finally {
            mCurrentInputContentInfo = null;
        }

        boolean supported = false;
        for (final String mimeType : contentMimeTypes) {
            if (inputContentInfo.getDescription().hasMimeType(mimeType)) {
                supported = true;
                break;
            }
        }
        if (!supported) {
            return false;
        }

        return onCommitContentInternal(inputContentInfo, flags);
    }

    private boolean onCommitContentInternal(InputContentInfoCompat inputContentInfo, int flags) {
        if ((flags & InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0) {
            try {
                inputContentInfo.requestPermission();
            } catch (Exception e) {
                Log.e("[TimelineFragment] requestPermission failed : ", e);
                return false;
            }
        }

        if (inputContentInfo.getContentUri() != null) {
            String contentUri = FileUtils.getFilePath(mContext, inputContentInfo.getContentUri());
            addImageToPendingList(contentUri);
        }

        mCurrentInputContentInfo = inputContentInfo;
        mCurrentFlags = flags;

        return true;
    }

    // This is a workaround to prevent a crash from happening while rotating the device
    private class LinphoneLinearLayoutManager extends LinearLayoutManager {
        LinphoneLinearLayoutManager(Context context, int orientation, boolean reverseLayout) {
            super(context, orientation, reverseLayout);
        }

        @Override
        public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
            try {
                super.onLayoutChildren(recycler, state);
            } catch (IndexOutOfBoundsException e) {
                Log.e("InvalidIndexOutOfBound Exception, probably while rotating the device");
            }
        }
    }
}
