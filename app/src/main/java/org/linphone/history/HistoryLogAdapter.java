package org.linphone.history;

/*
HistoryLogAdapter.java
Copyright (C) 2019 Belledonne Communications, Grenoble, France

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
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import org.linphone.R;
import org.linphone.core.Call;
import org.linphone.core.CallLog;
import org.linphone.utils.LinphoneUtils;

class HistoryLogAdapter extends ArrayAdapter<CallLog> {
    private Context mContext;
    private final List<CallLog> mItems;
    private final int mResource;

    HistoryLogAdapter(@NonNull Context context, int resource, @NonNull List<CallLog> objects) {
        super(context, resource, objects);
        mContext = context;
        mResource = resource;
        mItems = objects;
    }

    @Nullable
    @Override
    public CallLog getItem(int position) {
        return mItems.get(position);
    }

    @Override
    public int getCount() {
        return mItems.size();
    }

    @SuppressLint("SimpleDateFormat")
    private String secondsToDisplayableString(int secs) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
        Calendar cal = Calendar.getInstance();
        cal.set(0, 0, 0, 0, 0, secs);
        return dateFormat.format(cal.getTime());
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        LayoutInflater inflater =
                (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        View rowView = inflater.inflate(mResource, parent, false);
        CallLog callLog = getItem(position);

        String callTime = secondsToDisplayableString(callLog.getDuration());
        String callDate = String.valueOf(callLog.getStartDate());
        String status;
        if (callLog.getDir() == Call.Dir.Outgoing) {
            status = mContext.getString(R.string.outgoing);
        } else {
            if (callLog.getStatus() == Call.Status.Missed) {
                status = mContext.getString(R.string.missed);
            } else {
                status = mContext.getString(R.string.incoming);
            }
        }

        TextView date = rowView.findViewById(R.id.date);
        TextView time = rowView.findViewById(R.id.time);
        ImageView callDirection = rowView.findViewById(R.id.direction);

        if (status.equals(mContext.getResources().getString(R.string.missed))) {
            callDirection.setImageResource(R.drawable.call_missed);
        } else if (status.equals(mContext.getResources().getString(R.string.incoming))) {
            callDirection.setImageResource(R.drawable.call_incoming);
        } else if (status.equals(mContext.getResources().getString(R.string.outgoing))) {
            callDirection.setImageResource(R.drawable.call_outgoing);
        }

        time.setText(callTime == null ? "" : callTime);
        Long longDate = Long.parseLong(callDate);
        date.setText(
                LinphoneUtils.timestampToHumanDate(
                        mContext,
                        longDate,
                        mContext.getString(R.string.history_detail_date_format)));

        return rowView;
    }
}
