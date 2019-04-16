package org.linphone.recording;

/*
RecordingsAdapter.java
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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import androidx.annotation.NonNull;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.linphone.R;
import org.linphone.utils.SelectableAdapter;
import org.linphone.utils.SelectableHelper;

public class RecordingsAdapter extends SelectableAdapter<RecordingViewHolder> {
    private final List<Recording> mRecordings;
    private final Context mContext;
    private final RecordingViewHolder.ClickListener mClickListener;

    public RecordingsAdapter(
            Context context,
            List<Recording> recordings,
            RecordingViewHolder.ClickListener listener,
            SelectableHelper helper) {
        super(helper);

        mRecordings = recordings;
        mContext = context;
        mClickListener = listener;
    }

    @Override
    public Object getItem(int position) {
        return mRecordings.get(position);
    }

    @NonNull
    @Override
    public RecordingViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View v =
                LayoutInflater.from(viewGroup.getContext())
                        .inflate(R.layout.recording_cell, viewGroup, false);
        return new RecordingViewHolder(v, mClickListener);
    }

    @SuppressLint("SimpleDateFormat")
    @Override
    public void onBindViewHolder(@NonNull final RecordingViewHolder viewHolder, int i) {
        final Recording record = mRecordings.get(i);

        viewHolder.name.setSelected(true); // For automated horizontal scrolling of long texts

        Calendar recordTime = Calendar.getInstance();
        recordTime.setTime(record.getRecordDate());
        viewHolder.separatorText.setText(DateToHumanDate(recordTime));
        viewHolder.select.setVisibility(isEditionEnabled() ? View.VISIBLE : View.GONE);
        viewHolder.select.setChecked(isSelected(i));

        if (i > 0) {
            Recording previousRecord = mRecordings.get(i - 1);
            Date previousRecordDate = previousRecord.getRecordDate();
            Calendar previousRecordTime = Calendar.getInstance();
            previousRecordTime.setTime(previousRecordDate);

            if (isSameDay(previousRecordTime, recordTime)) {
                viewHolder.separator.setVisibility(View.GONE);
            } else {
                viewHolder.separator.setVisibility(View.VISIBLE);
            }
        } else {
            viewHolder.separator.setVisibility(View.VISIBLE);
        }

        if (record.isPlaying()) {
            viewHolder.playButton.setImageResource(R.drawable.record_pause);
        } else {
            viewHolder.playButton.setImageResource(R.drawable.record_play);
        }
        viewHolder.playButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (record.isPaused()) {
                            record.play();
                            viewHolder.playButton.setImageResource(R.drawable.record_pause);
                        } else {
                            record.pause();
                            viewHolder.playButton.setImageResource(R.drawable.record_play);
                        }
                    }
                });

        viewHolder.name.setText(record.getName());
        viewHolder.date.setText(new SimpleDateFormat("HH:mm").format(record.getRecordDate()));

        int position = record.getCurrentPosition();
        viewHolder.currentPosition.setText(
                String.format(
                        Locale.getDefault(),
                        "%02d:%02d",
                        TimeUnit.MILLISECONDS.toMinutes(position),
                        TimeUnit.MILLISECONDS.toSeconds(position)
                                - TimeUnit.MINUTES.toSeconds(
                                        TimeUnit.MILLISECONDS.toMinutes(position))));

        int duration = record.getDuration();
        viewHolder.duration.setText(
                String.format(
                        Locale.getDefault(),
                        "%02d:%02d",
                        TimeUnit.MILLISECONDS.toMinutes(duration),
                        TimeUnit.MILLISECONDS.toSeconds(duration)
                                - TimeUnit.MINUTES.toSeconds(
                                        TimeUnit.MILLISECONDS.toMinutes(duration))));

        viewHolder.progressionBar.setMax(record.getDuration());
        viewHolder.progressionBar.setProgress(0);
        viewHolder.progressionBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (fromUser) {
                            int progressToSet =
                                    progress > 0 && progress < seekBar.getMax() ? progress : 0;

                            if (progress == seekBar.getMax()) {
                                if (record.isPlaying()) record.pause();
                            }

                            record.seek(progressToSet);
                            seekBar.setProgress(progressToSet);

                            int currentPosition = record.getCurrentPosition();
                            viewHolder.currentPosition.setText(
                                    String.format(
                                            Locale.getDefault(),
                                            "%02d:%02d",
                                            TimeUnit.MILLISECONDS.toMinutes(currentPosition),
                                            TimeUnit.MILLISECONDS.toSeconds(currentPosition)
                                                    - TimeUnit.MINUTES.toSeconds(
                                                            TimeUnit.MILLISECONDS.toMinutes(
                                                                    currentPosition))));
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {}

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {}
                });

        record.setRecordingListener(
                new RecordingListener() {
                    @Override
                    public void currentPositionChanged(int currentPosition) {
                        viewHolder.currentPosition.setText(
                                String.format(
                                        Locale.getDefault(),
                                        "%02d:%02d",
                                        TimeUnit.MILLISECONDS.toMinutes(currentPosition),
                                        TimeUnit.MILLISECONDS.toSeconds(currentPosition)
                                                - TimeUnit.MINUTES.toSeconds(
                                                        TimeUnit.MILLISECONDS.toMinutes(
                                                                currentPosition))));
                        viewHolder.progressionBar.setProgress(currentPosition);
                    }

                    @Override
                    public void endOfRecordReached() {
                        record.pause();
                        record.seek(0);
                        viewHolder.progressionBar.setProgress(0);
                        viewHolder.currentPosition.setText("00:00");
                        viewHolder.playButton.setImageResource(R.drawable.record_play);
                    }
                });
    }

    @Override
    public int getItemCount() {
        return mRecordings.size();
    }

    @SuppressLint("SimpleDateFormat")
    private String DateToHumanDate(Calendar cal) {
        SimpleDateFormat dateFormat;
        if (isToday(cal)) {
            return mContext.getString(R.string.today);
        } else if (isYesterday(cal)) {
            return mContext.getString(R.string.yesterday);
        } else {
            dateFormat =
                    new SimpleDateFormat(
                            mContext.getResources().getString(R.string.history_date_format));
        }

        return dateFormat.format(cal.getTime());
    }

    private boolean isSameDay(Calendar cal1, Calendar cal2) {
        if (cal1 == null || cal2 == null) {
            return false;
        }

        return (cal1.get(Calendar.ERA) == cal2.get(Calendar.ERA)
                && cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
                && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR));
    }

    private boolean isToday(Calendar cal) {
        return isSameDay(cal, Calendar.getInstance());
    }

    private boolean isYesterday(Calendar cal) {
        Calendar yesterday = Calendar.getInstance();
        yesterday.roll(Calendar.DAY_OF_MONTH, -1);
        return isSameDay(cal, yesterday);
    }
}
