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

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.Address;
import org.linphone.core.ChatRoom;
import org.linphone.core.Factory;
import org.linphone.core.tools.Log;

public class EphemeralFragment extends Fragment {
    private ChatRoom mChatRoom;
    private long mCurrentValue;

    private LayoutInflater mInflater;
    private ViewGroup mContainer;
    private LinearLayout mItems;

    @Nullable
    @Override
    public View onCreateView(
            LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        View view = inflater.inflate(R.layout.chat_ephemeral, container, false);
        mInflater = inflater;
        mContainer = container;

        if (getArguments() == null || getArguments().isEmpty()) {
            return null;
        }

        String address = getArguments().getString("RemoteSipUri");
        Address peerAddress = null;
        mChatRoom = null;
        if (address != null && address.length() > 0) {
            peerAddress = Factory.instance().createAddress(address);
        }
        if (peerAddress != null) {
            mChatRoom = LinphoneManager.getCore().getChatRoom(peerAddress);
        }
        if (mChatRoom == null) {
            return null;
        }
        mCurrentValue = mChatRoom.ephemeralEnabled() ? mChatRoom.getEphemeralLifetime() : 0;
        Log.i(
                "[Ephemeral Messages] Current duration is ",
                mCurrentValue,
                ", ephemeral enabled? ",
                mChatRoom.ephemeralEnabled());

        view.findViewById(R.id.back)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                getFragmentManager().popBackStack();
                            }
                        });

        view.findViewById(R.id.valid)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                Log.i("[Ephemeral Messages] Selected value is ", mCurrentValue);
                                if (mCurrentValue > 0) {
                                    if (mChatRoom.getEphemeralLifetime() != mCurrentValue) {
                                        Log.i(
                                                "[Ephemeral Messages] Setting new lifetime for ephemeral messages to ",
                                                mCurrentValue);
                                        mChatRoom.setEphemeralLifetime(mCurrentValue);
                                    } else {
                                        Log.i(
                                                "[Ephemeral Messages] Configured lifetime for ephemeral messages was already ",
                                                mCurrentValue);
                                    }

                                    if (!mChatRoom.ephemeralEnabled()) {
                                        Log.i(
                                                "[Ephemeral Messages] Ephemeral messages were disabled, enable them");
                                        mChatRoom.enableEphemeral(true);
                                    }
                                } else if (mChatRoom.ephemeralEnabled()) {
                                    Log.i(
                                            "[Ephemeral Messages] Ephemeral messages were enabled, disable them");
                                    mChatRoom.enableEphemeral(false);
                                }

                                getFragmentManager().popBackStack();
                            }
                        });

        mItems = view.findViewById(R.id.items);

        computeItems();

        return view;
    }

    private View getView(int id, final long value) {
        View view = mInflater.inflate(R.layout.chat_ephemeral_item, mContainer, false);
        ((TextView) view.findViewById(R.id.text)).setText(id);
        ((TextView) view.findViewById(R.id.text))
                .setTextAppearance(
                        getActivity(),
                        mCurrentValue == value
                                ? R.style.chat_room_ephemeral_selected_item_font
                                : R.style.chat_room_ephemeral_item_font);
        view.findViewById(R.id.selected)
                .setVisibility(mCurrentValue == value ? View.VISIBLE : View.GONE);
        view.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mCurrentValue != value) {
                            mCurrentValue = value;
                            computeItems();
                        }
                    }
                });
        return view;
    }

    private void computeItems() {
        mItems.removeAllViews();
        mItems.addView(getView(R.string.chat_room_ephemeral_message_disabled, 0));
        mItems.addView(getView(R.string.chat_room_ephemeral_message_one_minute, 60));
        mItems.addView(getView(R.string.chat_room_ephemeral_message_one_hour, 3600));
        mItems.addView(getView(R.string.chat_room_ephemeral_message_one_day, 86400));
        mItems.addView(getView(R.string.chat_room_ephemeral_message_three_days, 259200));
        mItems.addView(getView(R.string.chat_room_ephemeral_message_one_week, 604800));
    }
}
