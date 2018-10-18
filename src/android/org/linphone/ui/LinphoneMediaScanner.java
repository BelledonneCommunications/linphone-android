package org.linphone.ui;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;

import org.linphone.mediastream.Log;

import java.io.File;

public class LinphoneMediaScanner implements MediaScannerConnection.MediaScannerConnectionClient {
    private MediaScannerConnection mMediaConnection;
    private boolean mIsConnected;
    private File mFileWaitingForScan;

    public LinphoneMediaScanner(Context context) {
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
            scanFile(mFileWaitingForScan);
            mFileWaitingForScan = null;
        }
    }

    public void scanFile(File file) {
        scanFile(file, null);
    }

    public void scanFile(File file, String mime) {
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
    }

    public void destroy() {
        Log.i("[MediaScanner] Disconnecting");
        mMediaConnection.disconnect();
        mIsConnected = false;
    }
}