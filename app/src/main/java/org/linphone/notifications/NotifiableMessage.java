package org.linphone.notifications;

/*
NotifiableMessage.java
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

import android.graphics.Bitmap;
import android.net.Uri;

public class NotifiableMessage {
    private final String mMessage;
    private final String mSender;
    private final long mTime;
    private Bitmap mSenderBitmap;
    private final Uri mFilePath;
    private final String mFileMime;

    public NotifiableMessage(
            String message, String sender, long time, Uri filePath, String fileMime) {
        mMessage = message;
        mSender = sender;
        mTime = time;
        mFilePath = filePath;
        mFileMime = fileMime;
    }

    public String getMessage() {
        return mMessage;
    }

    public String getSender() {
        return mSender;
    }

    public long getTime() {
        return mTime;
    }

    public Bitmap getSenderBitmap() {
        return mSenderBitmap;
    }

    public void setSenderBitmap(Bitmap bm) {
        mSenderBitmap = bm;
    }

    public Uri getFilePath() {
        return mFilePath;
    }

    public String getFileMime() {
        return mFileMime;
    }
}
