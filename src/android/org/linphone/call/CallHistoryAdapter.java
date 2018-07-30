package org.linphone.call;

import android.annotation.SuppressLint;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.linphone.LinphoneUtils;
import org.linphone.R;
import org.linphone.activities.LinphoneActivity;
import org.linphone.contacts.ContactsListAdapter;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.Call;
import org.linphone.core.CallLog;
import org.linphone.ui.SelectableAdapter;
import org.linphone.ui.SelectableHelper;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;

public class CallHistoryAdapter extends SelectableAdapter<CallHistoryAdapter.ViewHolder> {
    public static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener,
            View.OnLongClickListener{
        public TextView contact;
        public ImageView detail;
        public CheckBox select;
        public ImageView callDirection;
        public ImageView contactPicture;
        public RelativeLayout CallContact;
        public RelativeLayout SelectContact;
        public LinearLayout separator;
        public TextView separatorText;
        private CallHistoryAdapter.ViewHolder.ClickListener listener;

        public ViewHolder(View view, CallHistoryAdapter.ViewHolder.ClickListener listener) {
            super(view);
            contact = (TextView) view.findViewById(R.id.sip_uri);
            detail = (ImageView) view.findViewById(R.id.detail);
            select = (CheckBox) view.findViewById(R.id.delete);
            callDirection = (ImageView) view.findViewById(R.id.icon);
            contactPicture = (ImageView) view.findViewById(R.id.contact_picture);
            CallContact = (RelativeLayout) view.findViewById(R.id.history_click);
            SelectContact = (RelativeLayout) view.findViewById(R.id.history_whole);
            separator = (LinearLayout) view.findViewById(R.id.separator);
            separatorText = (TextView) view.findViewById(R.id.separator_text);
            this.listener = listener;
            view.setOnClickListener(this);
            view.setOnLongClickListener(this);

        }

        @Override
        public void onClick(View view) {
            if (listener != null) {
                listener.onItemClicked(getAdapterPosition());
            }
        }

        @Override
        public boolean onLongClick(View view) {
            if (listener != null) {
                return listener.onItemLongClicked(getAdapterPosition());
            }
            return false;
        }

        public interface ClickListener {
            public void onItemClicked(int position);
            public boolean onItemLongClicked(int position);
        }

    }

    private List<CallLog> mLogs;
    private Context mContext;
    private CallHistoryAdapter.ViewHolder.ClickListener clickListener;

    public CallHistoryAdapter(Context aContext, List<CallLog> logs,CallHistoryAdapter.ViewHolder.ClickListener listener ,SelectableHelper helper) {
        super(helper);
        this.mLogs = logs;
        this.mContext = aContext;
        this.clickListener = listener;
    }

    public int getCount() {
        return mLogs.size();
    }

    public Object getItem(int position) {
        return mLogs.get(position);
    }


    public long getItemId(int position) {
        return position;
    }


    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.history_cell, parent, false);
        return new ViewHolder(v, clickListener);

    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, final int position) {
        final CallLog log= mLogs.get(position);
        long timestamp = log.getStartDate() * 1000;
        Address address;

        holder.contact.setSelected(true); // For automated horizontal scrolling of long texts
        Calendar logTime = Calendar.getInstance();
        logTime.setTimeInMillis(timestamp);
        holder.separatorText.setText(timestampToHumanDate(logTime));
        holder.select.setVisibility(isEditionEnabled() ? View.VISIBLE : View.GONE);
        holder.select.setChecked(isSelected(position));


        if (position > 0) {
            CallLog previousLog = mLogs.get(position-1);
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
            LinphoneUtils.setThumbnailPictureFromUri(LinphoneActivity.instance(), holder.contactPicture, c.getThumbnailUri());
        } else {
            holder.contactPicture.setImageBitmap(ContactsManager.getInstance().getDefaultAvatarBitmap());
        }

        if (displayName == null) {
            holder.contact.setText(LinphoneUtils.getAddressDisplayName(sipUri));
        } else {
            holder.contact.setText(displayName);
        }



        holder.detail.setVisibility(isEditionEnabled() ? View.INVISIBLE : View.VISIBLE);
        holder.detail.setOnClickListener(!isEditionEnabled() ?
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (LinphoneActivity.isInstanciated()) {
                            LinphoneActivity.instance().displayHistoryDetail(sipUri, log);
                        }
                    }
                }
                :
                null
        );
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
            dateFormat = new SimpleDateFormat(mContext.getResources().getString(R.string.history_date_format));
        }

        return dateFormat.format(cal.getTime());
    }

    private boolean isSameDay(Calendar cal1, Calendar cal2) {
        if (cal1 == null || cal2 == null) {
            return false;
        }

        return (cal1.get(Calendar.ERA) == cal2.get(Calendar.ERA) &&
                cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR));
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