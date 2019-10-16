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
package org.linphone.utils;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import java.io.File;
import org.linphone.core.tools.Log;

public class MediaScanner implements MediaScannerConnection.MediaScannerConnectionClient {
    private final MediaScannerConnection mMediaConnection;
    private boolean mIsConnected;
    private File mFileWaitingForScan;
    private MediaScannerListener mListener;

    public MediaScanner(Context context) {
        mIsConnected = false;
        mMediaConnection = new MediaScannerConnection(context, this);
        mMediaConnection.connect();
        mFileWaitingForScan = null;
    }

    @Override
    public void onMediaScannerConnected() {
        mIsConnected = true;
        Log.i("[MediaScanner] Connected");
        if (mFileWaitingForScan != null) {
            scanFile(mFileWaitingForScan, null);
            mFileWaitingForScan = null;
        }
    }

    public void scanFile(File file, MediaScannerListener listener) {
        scanFile(file, FileUtils.getMimeFromFile(file.getAbsolutePath()), listener);
    }

    private void scanFile(File file, String mime, MediaScannerListener listener) {
        mListener = listener;

        if (!mIsConnected) {
            Log.w("[MediaScanner] Not connected yet...");
            mFileWaitingForScan = file;
            return;
        }

        Log.i("[MediaScanner] Scanning file " + file.getAbsolutePath() + " with MIME " + mime);
        mMediaConnection.scanFile(file.getAbsolutePath(), mime);
    }

    @Override
    public void onScanCompleted(String path, Uri uri) {
        Log.i("[MediaScanner] Scan completed : " + path + " => " + uri);
        if (mListener != null) {
            mListener.onMediaScanned(path, uri);
        }
    }

    public void destroy() {
        Log.i("[MediaScanner] Disconnecting");
        mMediaConnection.disconnect();
        mIsConnected = false;
    }
}
