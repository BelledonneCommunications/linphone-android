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

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import org.linphone.core.Address;
import org.linphone.core.Factory;
import org.linphone.main.MainActivity;

public class ChatActivity extends MainActivity {
    private Address mDisplayRoomLocalAddress, mDisplayRoomPeerAddress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ChatRoomsFragment fragment = new ChatRoomsFragment();
        changeFragment(fragment, "Chat rooms", false);
        if (isTablet()) {
            fragment.displayFirstChat();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        mChatSelected.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(
                "DisplayedRoomLocalAddress",
                mDisplayRoomLocalAddress != null
                        ? mDisplayRoomLocalAddress.asStringUriOnly()
                        : null);
        outState.putSerializable(
                "DisplayedRoomPeerAddress",
                mDisplayRoomPeerAddress != null ? mDisplayRoomPeerAddress.asStringUriOnly() : null);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        String localAddr = savedInstanceState.getString("DisplayedRoomLocalAddress");
        String peerAddr = savedInstanceState.getString("DisplayedRoomPeerAddress");
        Address localAddress = null;
        Address peerAddress = null;
        if (localAddr != null) {
            localAddress = Factory.instance().createAddress(localAddr);
        }
        if (peerAddr != null) {
            peerAddress = Factory.instance().createAddress(peerAddr);
        }
        if (peerAddress != null) {
            showChatRoom(localAddress, peerAddress);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!isTablet() && keyCode == KeyEvent.KEYCODE_BACK) {
            if (popBackStack()) {
                mDisplayRoomLocalAddress = null;
                mDisplayRoomPeerAddress = null;
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void goBack() {
        if (!isTablet()) {
            if (popBackStack()) {
                mDisplayRoomLocalAddress = null;
                mDisplayRoomPeerAddress = null;
                return;
            }
        }
        super.goBack();
    }

    public void showChatRoom(Address localAddress, Address peerAddress, Bundle extras) {
        if (extras == null) {
            extras = new Bundle();
        }
        if (localAddress != null) {
            extras.putSerializable("LocalSipUri", localAddress.asStringUriOnly());
            mDisplayRoomLocalAddress = localAddress;
        }
        if (peerAddress != null) {
            extras.putSerializable("RemoteSipUri", peerAddress.asStringUriOnly());
            mDisplayRoomPeerAddress = peerAddress;
        }
        ChatMessagesFragment fragment = new ChatMessagesFragment();
        fragment.setArguments(extras);
        changeFragment(fragment, "Chat room", true);
    }

    public void showChatRoom(Address localAddress, Address peerAddress) {
        showChatRoom(localAddress, peerAddress, null);
    }
}
