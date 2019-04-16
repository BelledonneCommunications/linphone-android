package org.linphone.history;

/*
HistoryAdapter.java
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
import androidx.annotation.NonNull;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import org.linphone.LinphoneActivity;
import org.linphone.R;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.Call;
import org.linphone.core.CallLog;
import org.linphone.utils.LinphoneUtils;
import org.linphone.utils.SelectableAdapter;
import org.linphone.utils.SelectableHelper;
import org.linphone.views.ContactAvatar;

public class HistoryAdapter extends SelectableAdapter<HistoryViewHolder> {
    private final List<CallLog> mLogs;
    private final Context mContext;
    private final HistoryViewHolder.ClickListener mClickListener;

    public HistoryAdapter(
            Context aContext,
            List<CallLog> logs,
            HistoryViewHolder.ClickListener listener,
            SelectableHelper helper) {
        super(helper);
        mLogs = logs;
        mContext = aContext;
        mClickListener = listener;
    }

    public Object getItem(int position) {
        return mLogs.get(position);
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v =
                LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.history_cell, parent, false);
        return new HistoryViewHolder(v, mClickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull final HistoryViewHolder holder, final int position) {
        final CallLog log = mLogs.get(position);
        long timestamp = log.getStartDate() * 1000;
        Address address;

        holder.contact.setSelected(true); // For automated horizontal scrolling of long texts
        Calendar logTime = Calendar.getInstance();
        logTime.setTimeInMillis(timestamp);
        holder.separatorText.setText(timestampToHumanDate(logTime));
        holder.select.setVisibility(isEditionEnabled() ? View.VISIBLE : View.GONE);
        holder.select.setChecked(isSelected(position));

        if (position > 0) {
            CallLog previousLog = mLogs.get(position - 1);
            long previousTimestamp = previousLog.getStartDate() * 1000;
            Calendar previousLogTime = Calendar.getInstance();
            previousLogTime.setTimeInMillis(previousTimestamp);

            if (isSameDay(previousLogTime, logTime)) {
                holder.separator.setVisibility(View.GONE);
            } else {
                holder.separator.setVisibility(View.VISIBLE);
            }
        } else {
            holder.separator.setVisibility(View.VISIBLE);
        }

        if (log.getDir() == Call.Dir.Incoming) {
            address = log.getFromAddress();
            if (log.getStatus() == Call.Status.Missed) {
                holder.callDirection.setImageResource(R.drawable.call_status_missed);
            } else {
                holder.callDirection.setImageResource(R.drawable.call_status_incoming);
            }
        } else {
            address = log.getToAddress();
            holder.callDirection.setImageResource(R.drawable.call_status_outgoing);
        }

        LinphoneContact c = ContactsManager.getInstance().findContactFromAddress(address);
        String displayName = null;
        final String sipUri = (address != null) ? address.asString() : "";

        if (c != null) {
            displayName = c.getFullName();
        }
        if (displayName == null) {
            holder.contact.setText(LinphoneUtils.getAddressDisplayName(sipUri));
        } else {
            holder.contact.setText(displayName);
        }

        if (c != null) {
            ContactAvatar.displayAvatar(c, holder.avatarLayout);
        } else {
            ContactAvatar.displayAvatar(holder.contact.getText().toString(), holder.avatarLayout);
        }

        holder.detail.setVisibility(isEditionEnabled() ? View.INVISIBLE : View.VISIBLE);
        holder.detail.setOnClickListener(
                !isEditionEnabled()
                        ? new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                if (LinphoneActivity.isInstanciated()) {
                                    LinphoneActivity.instance().displayHistoryDetail(sipUri, log);
                                }
                            }
                        }
                        : null);
    }

    @Override
    public int getItemCount() {
        return mLogs.size();
    }

    @SuppressLint("SimpleDateFormat")
    private String timestampToHumanDate(Calendar cal) {
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
