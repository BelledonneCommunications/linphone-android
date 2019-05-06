package org.linphone.history;

/*
HistoryFragment.java
Copyright (C) 2017 Belledonne Communications, Grenoble, France

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

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.ContactsUpdatedListener;
import org.linphone.core.Address;
import org.linphone.core.Call;
import org.linphone.core.CallLog;
import org.linphone.core.Core;
import org.linphone.utils.SelectableHelper;
import org.linphone.views.LinphoneLinearLayoutManager;

public class HistoryFragment extends Fragment
        implements OnClickListener,
                OnItemClickListener,
                HistoryViewHolder.ClickListener,
                ContactsUpdatedListener,
                SelectableHelper.DeleteListener {
    private RecyclerView mHistoryList;
    private TextView mNoCallHistory, mNoMissedCallHistory;
    private ImageView mMissedCalls, mAllCalls;
    private View mAllCallsSelected, mMissedCallsSelected;
    private boolean mOnlyDisplayMissedCalls;
    private List<CallLog> mLogs;
    private HistoryAdapter mHistoryAdapter;
    private SelectableHelper mSelectionHelper;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.history, container, false);
        mSelectionHelper = new SelectableHelper(view, this);

        mNoCallHistory = view.findViewById(R.id.no_call_history);
        mNoMissedCallHistory = view.findViewById(R.id.no_missed_call_history);

        mHistoryList = view.findViewById(R.id.history_list);

        LinearLayoutManager layoutManager = new LinphoneLinearLayoutManager(getActivity());
        mHistoryList.setLayoutManager(layoutManager);
        // Divider between items
        DividerItemDecoration dividerItemDecoration =
                new DividerItemDecoration(
                        mHistoryList.getContext(), layoutManager.getOrientation());
        dividerItemDecoration.setDrawable(getResources().getDrawable(R.drawable.divider));
        mHistoryList.addItemDecoration(dividerItemDecoration);

        mAllCalls = view.findViewById(R.id.all_calls);
        mAllCalls.setOnClickListener(this);

        mAllCallsSelected = view.findViewById(R.id.all_calls_select);

        mMissedCalls = view.findViewById(R.id.missed_calls);
        mMissedCalls.setOnClickListener(this);

        mMissedCallsSelected = view.findViewById(R.id.missed_calls_select);

        mAllCalls.setEnabled(false);
        mOnlyDisplayMissedCalls = false;

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        ContactsManager.getInstance().addContactsListener(this);

        mLogs = Arrays.asList(LinphoneManager.getCore().getCallLogs());
        hideHistoryListAndDisplayMessageIfEmpty();
        mHistoryAdapter =
                new HistoryAdapter((HistoryActivity) getActivity(), mLogs, this, mSelectionHelper);
        mHistoryList.setAdapter(mHistoryAdapter);
        mSelectionHelper.setAdapter(mHistoryAdapter);
        mSelectionHelper.setDialogMessage(R.string.call_log_delete_dialog);
    }

    @Override
    public void onPause() {
        ContactsManager.getInstance().removeContactsListener(this);
        super.onPause();
    }

    @Override
    public void onContactsUpdated() {
        HistoryAdapter adapter = (HistoryAdapter) mHistoryList.getAdapter();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();

        if (id == R.id.all_calls) {
            mAllCalls.setEnabled(false);
            mAllCallsSelected.setVisibility(View.VISIBLE);
            mMissedCallsSelected.setVisibility(View.INVISIBLE);
            mMissedCalls.setEnabled(true);
            mOnlyDisplayMissedCalls = false;
            refresh();
        }
        if (id == R.id.missed_calls) {
            mAllCalls.setEnabled(true);
            mAllCallsSelected.setVisibility(View.INVISIBLE);
            mMissedCallsSelected.setVisibility(View.VISIBLE);
            mMissedCalls.setEnabled(false);
            mOnlyDisplayMissedCalls = true;
        }
        hideHistoryListAndDisplayMessageIfEmpty();
        mHistoryAdapter =
                new HistoryAdapter((HistoryActivity) getActivity(), mLogs, this, mSelectionHelper);
        mHistoryList.setAdapter(mHistoryAdapter);
        mSelectionHelper.setAdapter(mHistoryAdapter);
        mSelectionHelper.setDialogMessage(R.string.chat_room_delete_dialog);
    }

    @Override
    public void onItemClick(AdapterView<?> adapter, View view, int position, long id) {
        if (mHistoryAdapter.isEditionEnabled()) {
            CallLog log = mLogs.get(position);
            Core core = LinphoneManager.getCore();
            core.removeCallLog(log);
            mLogs = Arrays.asList(core.getCallLogs());
        }
    }

    @Override
    public void onDeleteSelection(Object[] objectsToDelete) {
        int size = mHistoryAdapter.getSelectedItemCount();
        for (int i = 0; i < size; i++) {
            CallLog log = (CallLog) objectsToDelete[i];
            LinphoneManager.getCore().removeCallLog(log);
            onResume();
        }
    }

    @Override
    public void onItemClicked(int position) {
        if (mHistoryAdapter.isEditionEnabled()) {
            mHistoryAdapter.toggleSelection(position);
        } else {
            CallLog log = mLogs.get(position);
            Address address;
            if (log.getDir() == Call.Dir.Incoming) {
                address = log.getFromAddress();
            } else {
                address = log.getToAddress();
            }
            if (address != null) {
                LinphoneManager.getCallManager().newOutgoingCall(address.asStringUriOnly(), null);
            }
        }
    }

    @Override
    public boolean onItemLongClicked(int position) {
        if (!mHistoryAdapter.isEditionEnabled()) {
            mSelectionHelper.enterEditionMode();
        }
        mHistoryAdapter.toggleSelection(position);
        return true;
    }

    private void refresh() {
        mLogs = Arrays.asList(LinphoneManager.getCore().getCallLogs());
    }

    public void displayFirstLog() {
        Address addr;
        if (mLogs != null && mLogs.size() > 0) {
            CallLog log = mLogs.get(0); // More recent one is 0
            if (log.getDir() == Call.Dir.Incoming) {
                addr = log.getFromAddress();
            } else {
                addr = log.getToAddress();
            }
            ((HistoryActivity) getActivity()).showHistoryDetails(addr);
        } else {
            ((HistoryActivity) getActivity()).showEmptyChildFragment();
        }
    }

    private void removeNotMissedCallsFromLogs() {
        if (mOnlyDisplayMissedCalls) {
            List<CallLog> missedCalls = new ArrayList<>();
            for (CallLog log : mLogs) {
                if (log.getStatus() == Call.Status.Missed) {
                    missedCalls.add(log);
                }
            }
            mLogs = missedCalls;
        }
    }

    private void hideHistoryListAndDisplayMessageIfEmpty() {
        removeNotMissedCallsFromLogs();
        mNoCallHistory.setVisibility(View.GONE);
        mNoMissedCallHistory.setVisibility(View.GONE);

        if (mLogs.isEmpty()) {
            if (mOnlyDisplayMissedCalls) {
                mNoMissedCallHistory.setVisibility(View.VISIBLE);
            } else {
                mNoCallHistory.setVisibility(View.VISIBLE);
            }
            mHistoryList.setVisibility(View.GONE);
        } else {
            mNoCallHistory.setVisibility(View.GONE);
            mNoMissedCallHistory.setVisibility(View.GONE);
            mHistoryList.setVisibility(View.VISIBLE);
        }
    }
}
