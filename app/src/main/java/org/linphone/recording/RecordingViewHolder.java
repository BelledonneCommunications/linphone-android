package org.linphone.recording;

/*
RecordingViewHolder.java
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

import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import org.linphone.R;

public class RecordingViewHolder extends RecyclerView.ViewHolder
        implements View.OnClickListener, View.OnLongClickListener {
    public final ImageView playButton;
    public final TextView name;
    public final TextView date;
    public final TextView currentPosition;
    public final TextView duration;
    public final SeekBar progressionBar;
    public final CheckBox select;
    public final LinearLayout separator;
    public final TextView separatorText;

    private final RecordingViewHolder.ClickListener mListener;

    public RecordingViewHolder(View view, RecordingViewHolder.ClickListener listener) {
        super(view);

        playButton = view.findViewById(R.id.record_play);
        name = view.findViewById(R.id.record_name);
        date = view.findViewById(R.id.record_date);
        currentPosition = view.findViewById(R.id.record_current_time);
        duration = view.findViewById(R.id.record_duration);
        progressionBar = view.findViewById(R.id.record_progression_bar);
        select = view.findViewById(R.id.delete);
        separator = view.findViewById(R.id.separator);
        separatorText = view.findViewById(R.id.separator_text);

        mListener = listener;
        view.setOnClickListener(this);
        view.setOnLongClickListener(this);
    }

    @Override
    public void onClick(View view) {
        if (mListener != null) {
            mListener.onItemClicked(getAdapterPosition());
        }
    }

    @Override
    public boolean onLongClick(View view) {
        if (mListener != null) {
            return mListener.onItemLongClicked(getAdapterPosition());
        }
        return false;
    }

    public interface ClickListener {
        void onItemClicked(int position);

        boolean onItemLongClicked(int position);
    }
}
