package org.linphone.notifications;

/*
Notifiable.java
Copyright (C) 2018  Belledonne Communications, Grenoble, France

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

import java.util.ArrayList;
import java.util.List;

public class Notifiable {
    private final int mNotificationId;
    private List<NotifiableMessage> mMessages;
    private boolean mIsGroup;
    private String mGroupTitle;
    private String mLocalIdentity;
    private String mMyself;
    private int iconId;
    private int textId;

    public Notifiable(int id) {
        mNotificationId = id;
        mMessages = new ArrayList<>();
        mIsGroup = false;
        iconId = 0;
        textId = 0;
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
        return iconId;
    }

    public void setIconResourceId(int id) {
        iconId = id;
    }

    public int getTextResourceId() {
        return textId;
    }

    public void setTextResourceId(int id) {
        textId = id;
    }
}
