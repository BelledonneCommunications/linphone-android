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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import org.linphone.call.CallHistoryAdapter;
import org.linphone.contacts.ContactsListAdapter;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.ContactsUpdatedListener;
import org.linphone.contacts.LinphoneContact;
import org.linphone.LinphoneManager;
import org.linphone.LinphoneUtils;
import org.linphone.R;
import org.linphone.activities.LinphoneActivity;
import org.linphone.core.Call;
import org.linphone.core.Address;
import org.linphone.core.CallLog;
import org.linphone.core.Call.Status;
import org.linphone.ui.SelectableHelper;

import android.annotation.SuppressLint;
import android.app.Dialog;
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
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class HistoryListFragment extends Fragment implements OnClickListener, OnItemClickListener, CallHistoryAdapter.ViewHolder.ClickListener  ,ContactsUpdatedListener,SelectableHelper.DeleteListener{
	private RecyclerView historyList;
	private LayoutInflater mInflater;
	private TextView noCallHistory, noMissedCallHistory;
	private ImageView missedCalls, allCalls, edit, selectAll, deselectAll, delete, cancel;
	private View allCallsSelected, missedCallsSelected;
	private LinearLayout editList, topBar;
	private boolean onlyDisplayMissedCalls, isEditMode;
	private List<CallLog> mLogs;
	private CallHistoryAdapter mhistoryAdapter;
	private LinearLayoutManager layoutManager;
	private Context mContext;
	private SelectableHelper mSelectionHelper;
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
							 Bundle savedInstanceState) {
		mInflater = inflater;
		View view = inflater.inflate(R.layout.history, container, false);
        mContext = getActivity().getApplicationContext();
		mSelectionHelper = new SelectableHelper(view, this);

		noCallHistory = (TextView) view.findViewById(R.id.no_call_history);
		noMissedCallHistory = (TextView) view.findViewById(R.id.no_missed_call_history);

		historyList = (RecyclerView) view.findViewById(R.id.history_list);



		layoutManager = new LinearLayoutManager(mContext);
		historyList.setLayoutManager(layoutManager);
		//Divider between items
		DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(historyList.getContext(),
				layoutManager.getOrientation());
		dividerItemDecoration.setDrawable(mContext.getResources().getDrawable(R.drawable.divider));
		historyList.addItemDecoration(dividerItemDecoration);






//		delete = (ImageView) view.findViewById(R.id.delete);
//		delete.setOnClickListener(this);

		editList = (LinearLayout) view.findViewById(R.id.edit_list);
		topBar = (LinearLayout) view.findViewById(R.id.top_bar);

//		cancel = (ImageView) view.findViewById(R.id.cancel);
//		cancel.setOnClickListener(this);

		allCalls = (ImageView) view.findViewById(R.id.all_calls);
		allCalls.setOnClickListener(this);

		allCallsSelected = view.findViewById(R.id.all_calls_select);

		missedCalls = (ImageView) view.findViewById(R.id.missed_calls);
		missedCalls.setOnClickListener(this);

		missedCallsSelected = view.findViewById(R.id.missed_calls_select);

//		selectAll = (ImageView) view.findViewById(R.id.select_all);
//		selectAll.setOnClickListener(this);
//
//		deselectAll = (ImageView) view.findViewById(R.id.deselect_all);
//		deselectAll.setOnClickListener(this);

		allCalls.setEnabled(false);
		onlyDisplayMissedCalls = false;

		edit = (ImageView) view.findViewById(R.id.edit);
//		edit.setOnClickListener(this);

		return view;
	}

	public void refresh() {
		mLogs = Arrays.asList(LinphoneManager.getLc().getCallLogs());
	}


	public void displayFirstLog(){
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
		if (onlyDisplayMissedCalls) {
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
			if (onlyDisplayMissedCalls) {
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
//			historyList.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
            mhistoryAdapter= new CallHistoryAdapter(getActivity().getApplicationContext(), mLogs, this, mSelectionHelper);
			historyList.setAdapter(mhistoryAdapter);
			mSelectionHelper.setAdapter(mhistoryAdapter);
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
		CallHistoryAdapter adapter = (CallHistoryAdapter)historyList.getAdapter();
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
			onlyDisplayMissedCalls = false;
			refresh();
		}
		if (id == R.id.missed_calls) {
			allCalls.setEnabled(true);
			allCallsSelected.setVisibility(View.INVISIBLE);
			missedCallsSelected.setVisibility(View.VISIBLE);
			missedCalls.setEnabled(false);
			onlyDisplayMissedCalls = true;
		}
		if (!hideHistoryListAndDisplayMessageIfEmpty()) {
//			historyList.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
            mhistoryAdapter = new CallHistoryAdapter(mContext, mLogs, this ,mSelectionHelper);
			historyList.setAdapter(mhistoryAdapter);
			mSelectionHelper.setAdapter(mhistoryAdapter);
			mSelectionHelper.setDialogMessage(R.string.chat_room_delete_dialog);
		}

	}

	@Override
	public void onItemClick(AdapterView<?> adapter, View view, int position, long id) {
		if (mhistoryAdapter.isEditionEnabled()) {
			CallLog log = mLogs.get(position);
			LinphoneManager.getLc().removeCallLog(log);
			mLogs = Arrays.asList(LinphoneManager.getLc().getCallLogs());
		}
	}



	@Override
	public void onDeleteSelection(Object[] objectsToDelete) {
		int size = mhistoryAdapter.getSelectedItemCount();
		for(int i=0; i<size; i++) {
			CallLog log = (CallLog) objectsToDelete[i];
			LinphoneManager.getLc().removeCallLog(log);
			onResume();
		}
	}

	@Override
	public void onItemClicked(int position) {
		if (mhistoryAdapter.isEditionEnabled()) {
			mhistoryAdapter.toggleSelection(position);
		}else{
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
		if (!mhistoryAdapter.isEditionEnabled()) {
			mSelectionHelper.enterEditionMode();
		}
		mhistoryAdapter.toggleSelection(position);
		return true;
	}
}