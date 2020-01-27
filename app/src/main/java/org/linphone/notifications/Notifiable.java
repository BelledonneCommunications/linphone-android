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
package org.linphone.notifications;

import java.util.ArrayList;
import java.util.List;

public class Notifiable {
    private final int mNotificationId;
    private List<NotifiableMessage> mMessages;
    private boolean mIsGroup;
    private String mGroupTitle;
    private String mLocalIdentity;
    private String mMyself;
    private int mIconId;
    private int mTextId;

    public Notifiable(int id) {
        mNotificationId = id;
        mMessages = new ArrayList<>();
        mIsGroup = false;
        mIconId = 0;
        mTextId = 0;
    }

    public int getNotificationId() {
        return mNotificationId;
    }

    public void resetMessages() {
        mMessages = new ArrayList<>();
    }

    public void addMessage(NotifiableMessage notifMessage) {
        mMessages.add(notifMessage);
    }

    public List<NotifiableMessage> getMessages() {
        return mMessages;
    }

    public boolean isGroup() {
        return mIsGroup;
    }

    public void setIsGroup(boolean isGroup) {
        mIsGroup = isGroup;
    }

    public String getGroupTitle() {
        return mGroupTitle;
    }

    public void setGroupTitle(String title) {
        mGroupTitle = title;
    }

    public String getMyself() {
        return mMyself;
    }

    public void setMyself(String myself) {
        mMyself = myself;
    }

    public String getLocalIdentity() {
        return mLocalIdentity;
    }

    public void setLocalIdentity(String localIdentity) {
        mLocalIdentity = localIdentity;
    }

    public int getIconResourceId() {
        return mIconId;
    }

    public void setIconResourceId(int id) {
        mIconId = id;
    }

    public int getTextResourceId() {
        return mTextId;
    }

    public void setTextResourceId(int id) {
        mTextId = id;
    }

    public String toString() {
        return "Id: "
                + mNotificationId
                + ", local identity: "
                + mLocalIdentity
                + ", myself: "
                + mMyself
                + ", isGrouped: "
                + mIsGroup;
    }
}
