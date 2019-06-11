package org.linphone.chat;

/*
ChatActivity.java
Copyright (C) 2019 Belledonne Communications, Grenoble, France

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
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import java.util.ArrayList;
import org.linphone.R;
import org.linphone.activities.MainActivity;
import org.linphone.contacts.ContactAddress;
import org.linphone.core.Address;
import org.linphone.core.Factory;
import org.linphone.core.tools.Log;
import org.linphone.utils.FileUtils;

public class ChatActivity extends MainActivity {
    public static final String NAME = "Chat";

    private String mSharedText, mSharedFiles;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getIntent().putExtra("Activity", NAME);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onStart() {
        super.onStart();

        Fragment currentFragment = getFragmentManager().findFragmentById(R.id.fragmentContainer);
        if (currentFragment == null) {
            showChatRooms();

            if (getIntent() != null && getIntent().getExtras() != null) {
                handleIntentExtras(getIntent());
                // Remove the SIP Uri from the intent so a click on chat button will go back to list
                getIntent().removeExtra("RemoteSipUri");
            } else {
                if (isTablet()) {
                    showEmptyChildFragment();
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mChatSelected.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        getIntent().setAction("");
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("SharedText", mSharedText);
        outState.putString("SharedFiles", mSharedFiles);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mSharedText = savedInstanceState.getString("SharedText", null);
        mSharedFiles = savedInstanceState.getString("SharedFiles", null);
    }

    @Override
    public void goBack() {
        // 1 is for the empty fragment on tablets
        if (!isTablet() || getFragmentManager().getBackStackEntryCount() > 1) {
            if (popBackStack()) {
                return;
            }
        }
        super.goBack();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        // Clean fragments stack upon return
        while (getFragmentManager().getBackStackEntryCount() > 0) {
            getFragmentManager().popBackStackImmediate();
        }

        handleIntentExtras(intent);
    }

    private void handleIntentExtras(Intent intent) {
        if (intent == null) return;

        Fragment currentFragment = getFragmentManager().findFragmentById(R.id.fragmentContainer);
        if (currentFragment == null || !(currentFragment instanceof ChatRoomsFragment)) {
            showChatRooms();
        }

        String sharedText = null;
        String sharedFiles = null;

        String action = intent.getAction();
        String type = intent.getType();
        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if (("text/plain").equals(type) && intent.getStringExtra(Intent.EXTRA_TEXT) != null) {
                sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                Log.i("[Chat Activity] ACTION_SEND with text/plain data: " + sharedText);
            } else {
                Uri fileUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                sharedFiles = FileUtils.getFilePath(this, fileUri);
                Log.i("[Chat Activity] ACTION_SEND with file: " + sharedFiles);
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
            if (type.startsWith("image/")) {
                ArrayList<Uri> imageUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                StringBuilder filePaths = new StringBuilder();
                for (Uri uri : imageUris) {
                    filePaths.append(FileUtils.getFilePath(this, uri));
                    filePaths.append(":");
                }
                sharedFiles = filePaths.toString();
                Log.i("[Chat Activity] ACTION_SEND_MULTIPLE with files: " + sharedFiles);
            }
        } else {
            if (intent.getExtras() != null) {
                Bundle extras = intent.getExtras();
                handleRemoteSipUriInIntentExtras(extras);
            }
        }

        if (!getResources().getBoolean(R.bool.disable_chat_send_file)) {
            if (sharedText != null || sharedFiles != null) {
                mSharedText = sharedText;
                mSharedFiles = sharedFiles;
                Toast.makeText(this, R.string.toast_choose_chat_room_for_sharing, Toast.LENGTH_LONG)
                        .show();
                Log.i(
                        "[Chat Activity] Sharing arguments found: "
                                + mSharedText
                                + " / "
                                + mSharedFiles);
            }
        }
    }

    private void handleRemoteSipUriInIntentExtras(Bundle extras) {
        if (extras == null) return;

        if (extras.containsKey("RemoteSipUri")) {
            String remoteSipUri = extras.getString("RemoteSipUri", null);
            String localSipUri = extras.getString("LocalSipUri", null);

            Address localAddress = null;
            Address remoteAddress = null;
            if (localSipUri != null) {
                localAddress = Factory.instance().createAddress(localSipUri);
            }
            if (remoteSipUri != null) {
                remoteAddress = Factory.instance().createAddress(remoteSipUri);
            }
            // Don't make it a child on smartphones to have a working back button
            showChatRoom(localAddress, remoteAddress);
        }
    }

    private void showChatRooms() {
        ChatRoomsFragment fragment = new ChatRoomsFragment();
        changeFragment(fragment, "Chat rooms", false);
    }

    private void showChatRoom(Address localAddress, Address peerAddress, boolean isChild) {
        Bundle extras = new Bundle();
        if (localAddress != null) {
            extras.putSerializable("LocalSipUri", localAddress.asStringUriOnly());
        }
        if (peerAddress != null) {
            extras.putSerializable("RemoteSipUri", peerAddress.asStringUriOnly());
        }
        if (mSharedText != null) {
            extras.putString("SharedText", mSharedText);
            mSharedText = null;
        }
        if (mSharedFiles != null) {
            extras.putString("SharedFiles", mSharedFiles);
            mSharedFiles = null;
        }

        ChatMessagesFragment fragment = new ChatMessagesFragment();
        fragment.setArguments(extras);
        changeFragment(fragment, "Chat room", isChild);
    }

    public void showChatRoom(Address localAddress, Address peerAddress) {
        showChatRoom(localAddress, peerAddress, true);
    }

    public void showImdn(Address localAddress, Address peerAddress, String messageId) {
        Bundle extras = new Bundle();
        if (localAddress != null) {
            extras.putSerializable("LocalSipUri", localAddress.asStringUriOnly());
        }
        if (peerAddress != null) {
            extras.putSerializable("RemoteSipUri", peerAddress.asStringUriOnly());
        }
        extras.putString("MessageId", messageId);

        ImdnFragment fragment = new ImdnFragment();
        fragment.setArguments(extras);
        changeFragment(fragment, "Chat message IMDN", true);
    }

    public void showDevices(Address localAddress, Address peerAddress) {
        showDevices(localAddress, peerAddress, true);
    }

    private void showDevices(Address localAddress, Address peerAddress, boolean isChild) {
        Bundle extras = new Bundle();
        if (localAddress != null) {
            extras.putSerializable("LocalSipUri", localAddress.asStringUriOnly());
        }
        if (peerAddress != null) {
            extras.putSerializable("RemoteSipUri", peerAddress.asStringUriOnly());
        }

        DevicesFragment fragment = new DevicesFragment();
        fragment.setArguments(extras);
        changeFragment(fragment, "Chat room devices", isChild);
    }

    public void showChatRoomCreation(
            Address peerAddress,
            ArrayList<ContactAddress> participants,
            String subject,
            boolean encrypted,
            boolean isGroupChatRoom) {
        Bundle extras = new Bundle();
        if (peerAddress != null) {
            extras.putSerializable("RemoteSipUri", peerAddress.asStringUriOnly());
        }
        extras.putSerializable("Participants", participants);
        extras.putString("Subject", subject);
        extras.putBoolean("Encrypted", encrypted);
        extras.putBoolean("IsGroupChatRoom", isGroupChatRoom);

        ChatRoomCreationFragment fragment = new ChatRoomCreationFragment();
        fragment.setArguments(extras);
        changeFragment(fragment, "Chat room creation", true);
    }

    public void showChatRoomGroupInfo(
            Address peerAddress,
            ArrayList<ContactAddress> participants,
            String subject,
            boolean encrypted) {
        Bundle extras = new Bundle();
        if (peerAddress != null) {
            extras.putSerializable("RemoteSipUri", peerAddress.asStringUriOnly());
        }
        extras.putSerializable("Participants", participants);
        extras.putString("Subject", subject);
        extras.putBoolean("Encrypted", encrypted);

        GroupInfoFragment fragment = new GroupInfoFragment();
        fragment.setArguments(extras);
        changeFragment(fragment, "Chat room group info", true);
    }
}
