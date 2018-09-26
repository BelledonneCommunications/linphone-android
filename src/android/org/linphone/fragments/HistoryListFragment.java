package org.linphone.fragments;

/*
HistoryListFragment.java
Copyright (C) 2017  Belledonne Communications, Grenoble, France

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
import android.content.Context;
import android.os.Bundle;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.TextView;

import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.activities.LinphoneActivity;
import org.linphone.call.CallHistoryAdapter;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.ContactsUpdatedListener;
import org.linphone.core.Address;
import org.linphone.core.Call;
import org.linphone.core.CallLog;
import org.linphone.ui.SelectableHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HistoryListFragment extends Fragment implements OnClickListener, OnItemClickListener, CallHistoryAdapter.ViewHolder.ClickListener, ContactsUpdatedListener, SelectableHelper.DeleteListener {
    private RecyclerView historyList;
    private TextView noCallHistory, noMissedCallHistory;
    private ImageView missedCalls, allCalls, edit;
    private View allCallsSelected, missedCallsSelected;
    private boolean mOnlyDisplayMissedCalls;
    private List<CallLog> mLogs;
    private CallHistoryAdapter mHistoryAdapter;
    private LinearLayoutManager mLayoutManager;
    private Context mContext;
    private SelectableHelper mSelectionHelper;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.history, container, false);
        mContext = getActivity().getApplicationContext();
        mSelectionHelper = new SelectableHelper(view, this);

        noCallHistory = view.findViewById(R.id.no_call_history);
        noMissedCallHistory = view.findViewById(R.id.no_missed_call_history);

        historyList = view.findViewById(R.id.history_list);

        mLayoutManager = new LinearLayoutManager(mContext);
        historyList.setLayoutManager(mLayoutManager);
        //Divider between items
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(historyList.getContext(),
                mLayoutManager.getOrientation());
        dividerItemDecoration.setDrawable(mContext.getResources().getDrawable(R.drawable.divider));
        historyList.addItemDecoration(dividerItemDecoration);

        allCalls = view.findViewById(R.id.all_calls);
        allCalls.setOnClickListener(this);

        allCallsSelected = view.findViewById(R.id.all_calls_select);

        missedCalls = view.findViewById(R.id.missed_calls);
        missedCalls.setOnClickListener(this);

        missedCallsSelected = view.findViewById(R.id.missed_calls_select);

        allCalls.setEnabled(false);
        mOnlyDisplayMissedCalls = false;

        edit = view.findViewById(R.id.edit);

        return view;
    }

    public void refresh() {
        mLogs = Arrays.asList(LinphoneManager.getLc().getCallLogs());
    }

    public void displayFirstLog() {
        if (mLogs != null && mLogs.size() > 0) {
            CallLog log = mLogs.get(0);
            String addr;
            if (log.getDir() == Call.Dir.Incoming) {
                addr = log.getFromAddress().asString();
            } else {
                addr = log.getToAddress().asString();
            }
            LinphoneActivity.instance().displayHistoryDetail(addr, log);
        } else {
            LinphoneActivity.instance().displayEmptyFragment();
        }
    }

    private void removeNotMissedCallsFromLogs() {
        if (mOnlyDisplayMissedCalls) {
            List<CallLog> missedCalls = new ArrayList<CallLog>();
            for (CallLog log : mLogs) {
                if (log.getStatus() == Call.Status.Missed) {
                    missedCalls.add(log);
                }
            }
            mLogs = missedCalls;
        }
    }

    private boolean hideHistoryListAndDisplayMessageIfEmpty() {
        removeNotMissedCallsFromLogs();
        if (mLogs.isEmpty()) {
            if (mOnlyDisplayMissedCalls) {
                noMissedCallHistory.setVisibility(View.VISIBLE);
            } else {
                noCallHistory.setVisibility(View.VISIBLE);
            }
            historyList.setVisibility(View.GONE);
            edit.setEnabled(false);
            return true;
        } else {
            noCallHistory.setVisibility(View.GONE);
            noMissedCallHistory.setVisibility(View.GONE);
            historyList.setVisibility(View.VISIBLE);
            edit.setEnabled(true);
            return false;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ContactsManager.addContactsListener(this);

        if (LinphoneActivity.isInstanciated()) {
            LinphoneActivity.instance().selectMenu(FragmentsAvailable.HISTORY_LIST);
            LinphoneActivity.instance().hideTabBar(false);
            LinphoneActivity.instance().displayMissedCalls(0);
        }

        mLogs = Arrays.asList(LinphoneManager.getLc().getCallLogs());
        if (!hideHistoryListAndDisplayMessageIfEmpty()) {
            mHistoryAdapter = new CallHistoryAdapter(getActivity().getApplicationContext(), mLogs, this, mSelectionHelper);
            historyList.setAdapter(mHistoryAdapter);
            mSelectionHelper.setAdapter(mHistoryAdapter);
            mSelectionHelper.setDialogMessage(R.string.chat_room_delete_dialog);
        }
    }

    @Override
    public void onPause() {
        ContactsManager.removeContactsListener(this);
        super.onPause();
    }

    @Override
    public void onContactsUpdated() {
        if (!LinphoneActivity.isInstanciated() || LinphoneActivity.instance().getCurrentFragment() != FragmentsAvailable.HISTORY_LIST)
            return;
        CallHistoryAdapter adapter = (CallHistoryAdapter) historyList.getAdapter();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();

        if (id == R.id.all_calls) {
            allCalls.setEnabled(false);
            allCallsSelected.setVisibility(View.VISIBLE);
            missedCallsSelected.setVisibility(View.INVISIBLE);
            missedCalls.setEnabled(true);
            mOnlyDisplayMissedCalls = false;
            refresh();
        }
        if (id == R.id.missed_calls) {
            allCalls.setEnabled(true);
            allCallsSelected.setVisibility(View.INVISIBLE);
            missedCallsSelected.setVisibility(View.VISIBLE);
            missedCalls.setEnabled(false);
            mOnlyDisplayMissedCalls = true;
        }
        if (!hideHistoryListAndDisplayMessageIfEmpty()) {
//			historyList.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
            mHistoryAdapter = new CallHistoryAdapter(mContext, mLogs, this, mSelectionHelper);
            historyList.setAdapter(mHistoryAdapter);
            mSelectionHelper.setAdapter(mHistoryAdapter);
            mSelectionHelper.setDialogMessage(R.string.chat_room_delete_dialog);
        }

    }

    @Override
    public void onItemClick(AdapterView<?> adapter, View view, int position, long id) {
        if (mHistoryAdapter.isEditionEnabled()) {
            CallLog log = mLogs.get(position);
            LinphoneManager.getLc().removeCallLog(log);
            mLogs = Arrays.asList(LinphoneManager.getLc().getCallLogs());
        }
    }

    @Override
    public void onDeleteSelection(Object[] objectsToDelete) {
        int size = mHistoryAdapter.getSelectedItemCount();
        for (int i = 0; i < size; i++) {
            CallLog log = (CallLog) objectsToDelete[i];
            LinphoneManager.getLc().removeCallLog(log);
            onResume();
        }
    }

    @Override
    public void onItemClicked(int position) {
        if (mHistoryAdapter.isEditionEnabled()) {
            mHistoryAdapter.toggleSelection(position);
        } else {
            if (LinphoneActivity.isInstanciated()) {
                CallLog log = mLogs.get(position);
                Address address;
                if (log.getDir() == Call.Dir.Incoming) {
                    address = log.getFromAddress();
                } else {
                    address = log.getToAddress();
                }
                LinphoneActivity.instance().setAddresGoToDialerAndCall(address.asStringUriOnly(), address.getDisplayName(), null);
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
}