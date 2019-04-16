package org.linphone.recording;

/*
Recording.java
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

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import androidx.annotation.NonNull;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.linphone.LinphoneManager;
import org.linphone.core.Player;
import org.linphone.core.PlayerListener;
import org.linphone.core.tools.Log;

class Recording implements PlayerListener, Comparable<Recording> {
    public static final Pattern RECORD_PATTERN =
            Pattern.compile(".*/(.*)_(\\d{2}-\\d{2}-\\d{4}-\\d{2}-\\d{2}-\\d{2})\\..*");

    private final String mRecordPath;
    private String mName;
    private Date mRecordDate;
    private final Player mPlayer;
    private RecordingListener mListener;
    private final Handler mHandler;
    private Runnable mUpdateCurrentPositionTimer;

    @SuppressLint("SimpleDateFormat")
    public Recording(Context context, String recordPath) {
        this.mRecordPath = recordPath;

        Matcher m = RECORD_PATTERN.matcher(recordPath);
        if (m.matches()) {
            mName = m.group(1);

            try {
                mRecordDate = new SimpleDateFormat("dd-MM-yyyy-HH-mm-ss").parse(m.group(2));
            } catch (ParseException e) {
                Log.e(e);
            }
        }

        mHandler = new Handler(context.getMainLooper());
        mUpdateCurrentPositionTimer =
                new Runnable() {
                    @Override
                    public void run() {
                        if (mListener != null)
                            mListener.currentPositionChanged(getCurrentPosition());
                        if (isPlaying()) mHandler.postDelayed(mUpdateCurrentPositionTimer, 20);
                    }
                };

        mPlayer = LinphoneManager.getLc().createLocalPlayer(null, null, null);
        mPlayer.addListener(this);
    }

    public String getRecordPath() {
        return mRecordPath;
    }

    public String getName() {
        return mName;
    }

    public Date getRecordDate() {
        return mRecordDate;
    }

    public boolean isClosed() {
        return mPlayer.getState() == Player.State.Closed;
    }

    public void play() {
        if (isClosed()) {
            mPlayer.open(mRecordPath);
        }

        mPlayer.start();
        mHandler.post(mUpdateCurrentPositionTimer);
    }

    public boolean isPlaying() {
        return mPlayer.getState() == Player.State.Playing;
    }

    public void pause() {
        if (!isClosed()) {
            mPlayer.pause();
        }
    }

    public boolean isPaused() {
        return mPlayer.getState() == Player.State.Paused;
    }

    public void seek(int i) {
        if (!isClosed()) mPlayer.seek(i);
    }

    public int getCurrentPosition() {
        if (isClosed()) {
            mPlayer.open(mRecordPath);
        }

        return mPlayer.getCurrentPosition();
    }

    public int getDuration() {
        if (isClosed()) {
            mPlayer.open(mRecordPath);
        }

        return mPlayer.getDuration();
    }

    public void close() {
        mPlayer.removeListener(this);
        mPlayer.close();
    }

    public void setRecordingListener(RecordingListener listener) {
        this.mListener = listener;
    }

    @Override
    public void onEofReached(Player player) {
        if (mListener != null) mListener.endOfRecordReached();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Recording) {
            Recording r = (Recording) o;
            return mRecordPath.equals(r.getRecordPath());
        }
        return false;
    }

    @Override
    public int compareTo(@NonNull Recording o) {
        return -mRecordDate.compareTo(o.getRecordDate());
    }
}
