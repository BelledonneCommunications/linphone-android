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
import android.support.annotation.NonNull;

import org.linphone.LinphoneManager;
import org.linphone.core.Player;
import org.linphone.core.PlayerListener;
import org.linphone.mediastream.Log;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Recording implements PlayerListener, Comparable<Recording> {
    private String recordPath, name;
    private Date recordDate;
    private Player player;
    private RecordingListener listener;
    private Handler handler;
    private Runnable updateCurrentPositionTimer;

    public static final Pattern RECORD_PATTERN = Pattern.compile(".*/(.*)_(\\d{2}-\\d{2}-\\d{4}-\\d{2}-\\d{2}-\\d{2})\\..*");

    @SuppressLint("SimpleDateFormat")
    public Recording(Context context, String recordPath) {
        this.recordPath = recordPath;

        Matcher m = RECORD_PATTERN.matcher(recordPath);
        if (m.matches()) {
            name = m.group(1);

            try {
                recordDate = new SimpleDateFormat("dd-MM-yyyy-HH-mm-ss").parse(m.group(2));
            } catch (ParseException e) {
                Log.e(e);
            }
        }

        handler = new Handler(context.getMainLooper());
        updateCurrentPositionTimer = new Runnable() {
            @Override
            public void run() {
                if (listener != null) listener.currentPositionChanged(getCurrentPosition());
                if (isPlaying()) handler.postDelayed(updateCurrentPositionTimer, 20);
            }
        };

        player = LinphoneManager.getLc().createLocalPlayer(null, null, null);
        player.setListener(this);
    }

    public String getRecordPath() {
        return recordPath;
    }

    public String getName() {
        return name;
    }

    public Date getRecordDate() {
        return recordDate;
    }

    public boolean isClosed() {
        return player.getState() == Player.State.Closed;
    }

    public void play() {
        if (isClosed()) {
            player.open(recordPath);
        }

        player.start();
        handler.post(updateCurrentPositionTimer);
    }

    public boolean isPlaying() {
        return player.getState() == Player.State.Playing;
    }

    public void pause() {
        if (!isClosed()) {
            player.pause();
        }
    }

    public boolean isPaused() {
        return player.getState() == Player.State.Paused;
    }

    public void seek(int i) {
        if (!isClosed()) player.seek(i);
    }

    public int getCurrentPosition() {
        if (isClosed()) {
            player.open(recordPath);
        }

        return player.getCurrentPosition();
    }

    public int getDuration() {
        if (isClosed()) {
            player.open(recordPath);
        }

        return player.getDuration();
    }

    public void close() {
        player.close();
    }

    public void setRecordingListener(RecordingListener listener) {
        this.listener = listener;
    }

    @Override
    public void onEofReached(Player player) {
        if (listener != null) listener.endOfRecordReached();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Recording) {
            Recording r = (Recording) o;
            return recordPath.equals(r.getRecordPath());
        }
        return false;
    }

    @Override
    public int compareTo(@NonNull Recording o) {
        return -recordDate.compareTo(o.getRecordDate());
    }
}
