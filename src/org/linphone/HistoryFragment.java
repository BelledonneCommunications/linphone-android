package org.linphone;
/*
HistoryFragment.java
Copyright (C) 2012  Belledonne Communications, Grenoble, France

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
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
*/
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import org.linphone.core.CallDirection;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCallLog;
import org.linphone.core.LinphoneCallLog.CallStatus;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
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

/**
 * @author Sylvain Berfini
 */
public class HistoryFragment extends Fragment implements OnClickListener, OnItemClickListener {
	private ListView historyList;
	private LayoutInflater mInflater;
	private TextView noCallHistory, noMissedCallHistory;
	private ImageView missedCalls, allCalls, edit, selectAll, deselectAll, delete;
	private RelativeLayout allCallsSelected, missedCallsSelected;
	private boolean onlyDisplayMissedCalls, isEditMode;
	private List<LinphoneCallLog> mLogs;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
							 Bundle savedInstanceState) {
		mInflater = inflater;
		View view = inflater.inflate(R.layout.history, container, false);

		noCallHistory = (TextView) view.findViewById(R.id.noCallHistory);
		noMissedCallHistory = (TextView) view.findViewById(R.id.noMissedCallHistory);

		historyList = (ListView) view.findViewById(R.id.historyList);
		historyList.setOnItemClickListener(this);

		delete = (ImageView) view.findViewById(R.id.delete);
		delete.setOnClickListener(this);
		delete.setVisibility(View.INVISIBLE);

		allCalls = (ImageView) view.findViewById(R.id.all_calls);
		allCalls.setOnClickListener(this);

		allCallsSelected = (RelativeLayout) view.findViewById(R.id.all_calls_select);
		allCallsSelected.setOnClickListener(this);

		missedCalls = (ImageView) view.findViewById(R.id.missed_calls);
		missedCalls.setOnClickListener(this);

		missedCallsSelected = (RelativeLayout) view.findViewById(R.id.missed_calls_select);
		missedCallsSelected.setOnClickListener(this);

		selectAll = (ImageView) view.findViewById(R.id.select_all);
		selectAll.setOnClickListener(this);

		deselectAll = (ImageView) view.findViewById(R.id.deselect_all);
		deselectAll.setOnClickListener(this);

		allCalls.setEnabled(false);
		onlyDisplayMissedCalls = false;

		edit = (ImageView) view.findViewById(R.id.edit);
		edit.setOnClickListener(this);

		return view;
	}

	public void refresh() {
		mLogs = Arrays.asList(LinphoneManager.getLc().getCallLogs());
	}

	private void selectAllList(boolean isSelectAll){
		int size = historyList.getAdapter().getCount();
		for(int i=0; i<size; i++) {
			historyList.setItemChecked(i,isSelectAll);
		}
	}

	private void removeCallLogs(){
		int size = historyList.getAdapter().getCount();
		for(int i=0; i<size; i++) {
			if(historyList.isItemChecked(i)){
				LinphoneCallLog log = mLogs.get(i);
				LinphoneManager.getLc().removeCallLog(log);
			}
		}
	}

	private void removeNotMissedCallsFromLogs() {
		if (onlyDisplayMissedCalls) {
			List<LinphoneCallLog> missedCalls = new ArrayList<LinphoneCallLog>();
			for (LinphoneCallLog log : mLogs) {
				if (log.getStatus() == CallStatus.Missed) {
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

		if (LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().selectMenu(FragmentsAvailable.HISTORY);
		}

		mLogs = Arrays.asList(LinphoneManager.getLc().getCallLogs());
		if (!hideHistoryListAndDisplayMessageIfEmpty()) {
			historyList.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
			historyList.setAdapter(new CallHistoryAdapter(getActivity()));
		}
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();

		if (id == R.id.select_all) {
			deselectAll.setVisibility(View.VISIBLE);
			selectAll.setVisibility(View.GONE);
			selectAllList(true);
			return;
		}
		if (id == R.id.deselect_all) {
			deselectAll.setVisibility(View.GONE);
			selectAll.setVisibility(View.VISIBLE);
			selectAllList(false);
			return;
		}

		if (id == R.id.delete) {
			final Dialog dialog = LinphoneActivity.instance().displayDialog(getString(R.string.delete_text));
			Button delete = (Button) dialog.findViewById(R.id.delete);
			Button cancel = (Button) dialog.findViewById(R.id.cancel);

			delete.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
					removeCallLogs();
					dialog.dismiss();
					quitEditMode();
				}
			});

			cancel.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
					dialog.dismiss();
					quitEditMode();
				}
			});
			dialog.show();
			return;
		}

		if (id == R.id.edit) {
			edit.setVisibility(View.GONE);
			selectAll.setVisibility(View.VISIBLE);
			delete.setVisibility(View.VISIBLE);
			isEditMode = true;
		}
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
			historyList.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
			historyList.setAdapter(new CallHistoryAdapter(getActivity().getApplicationContext()));
		}

		if(isEditMode){
			deselectAll.setVisibility(View.GONE);
			selectAll.setVisibility(View.VISIBLE);
		}

	}

	@Override
	public void onItemClick(AdapterView<?> adapter, View view, int position, long id) {
		if (isEditMode) {
			LinphoneCallLog log = mLogs.get(position);
			LinphoneManager.getLc().removeCallLog(log);
			mLogs = Arrays.asList(LinphoneManager.getLc().getCallLogs());
		} else {
			if (LinphoneActivity.isInstanciated()) {
				LinphoneCallLog log = mLogs.get(position);
				LinphoneAddress address;
				if (log.getDirection() == CallDirection.Incoming) {
					address = log.getFrom();
				} else {
					address = log.getTo();
				}
				LinphoneActivity.instance().setAddresGoToDialerAndCall(address.asStringUriOnly(), address.getDisplayName(), null);
			}
		}
	}

	private void hideDeleteAllButton() {
		if (delete == null || delete.getVisibility() != View.VISIBLE) {
			return;
		}

		if (LinphoneActivity.instance().isAnimationDisabled()) {
			delete.setVisibility(View.INVISIBLE);
		} else {
			Animation animation = AnimationUtils.loadAnimation(getActivity(), R.anim.slide_out_right_to_left);
			animation.setAnimationListener(new AnimationListener() {
				@Override
				public void onAnimationStart(Animation animation) {

				}

				@Override
				public void onAnimationRepeat(Animation animation) {

				}

				@Override
				public void onAnimationEnd(Animation animation) {
					delete.setVisibility(View.INVISIBLE);
					animation.setAnimationListener(null);
				}
			});
			delete.startAnimation(animation);
		}
	}

	private void showDeleteAllButton() {
		if (delete == null || delete.getVisibility() == View.VISIBLE) {
			return;
		}

		if (LinphoneActivity.instance().isAnimationDisabled()) {
			delete.setVisibility(View.VISIBLE);
		} else {
			Animation animation = AnimationUtils.loadAnimation(getActivity(), R.anim.slide_in_left_to_right);
			animation.setAnimationListener(new AnimationListener() {
				@Override
				public void onAnimationStart(Animation animation) {

				}

				@Override
				public void onAnimationRepeat(Animation animation) {

				}

				@Override
				public void onAnimationEnd(Animation animation) {
					delete.setVisibility(View.VISIBLE);
					animation.setAnimationListener(null);
				}
			});
			delete.startAnimation(animation);
		}
	}

	public void quitEditMode(){
		isEditMode = false;
		selectAll.setVisibility(View.GONE);
		deselectAll.setVisibility(View.GONE);
		delete.setVisibility(View.GONE);
		edit.setVisibility(View.VISIBLE);
		refresh();
		if (!hideHistoryListAndDisplayMessageIfEmpty()){
			historyList.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
			historyList.setAdapter(new CallHistoryAdapter(getActivity().getApplicationContext()));
		}
	}

	class CallHistoryAdapter extends  BaseAdapter {
		CallHistoryAdapter(Context aContext) {
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

		@SuppressLint("SimpleDateFormat")
		private String timestampToHumanDate(Calendar cal) {
			SimpleDateFormat dateFormat;
			if (isToday(cal)) {
				return getString(R.string.today);
			} else if (isYesterday(cal)) {
				return getString(R.string.yesterday);
			} else {
				dateFormat = new SimpleDateFormat(getResources().getString(R.string.history_date_format));
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

		public View getView(final int position, View convertView, ViewGroup parent) {
			View view = null;
			ViewHolder holder;
			if (convertView != null) {
				view = convertView;
				holder = (ViewHolder) view.getTag();
			} else {
				view = mInflater.inflate(R.layout.history_cell, parent,false);
				holder = new ViewHolder();
				holder.contact = (TextView) view.findViewById(R.id.sip_uri);
				holder.detail = (ImageView) view.findViewById(R.id.detail);
				holder.delete = (CheckBox) view.findViewById(R.id.delete);
				holder.callDirection = (ImageView) view.findViewById(R.id.icon);
				holder.contactPicture = (ImageView) view.findViewById(R.id.contact_picture);
			}

			final LinphoneCallLog log = mLogs.get(position);
			long timestamp = log.getTimestamp();
			final LinphoneAddress address;

			holder.contact.setSelected(true); // For automated horizontal scrolling of long texts

			LinearLayout separator = (LinearLayout) view.findViewById(R.id.separator);
			TextView separatorText = (TextView) view.findViewById(R.id.separator_text);
			Calendar logTime = Calendar.getInstance();
			logTime.setTimeInMillis(timestamp);
			separatorText.setText(timestampToHumanDate(logTime));

			if (position > 0) {
				LinphoneCallLog previousLog = mLogs.get(position-1);
				long previousTimestamp = previousLog.getTimestamp();
				Calendar previousLogTime = Calendar.getInstance();
				previousLogTime.setTimeInMillis(previousTimestamp);

				if (isSameDay(previousLogTime, logTime)) {
					separator.setVisibility(View.GONE);
				} else {
					separator.setVisibility(View.VISIBLE);
				}
			} else {
				separator.setVisibility(View.VISIBLE);
			}

			if (log.getDirection() == CallDirection.Incoming) {
				address = log.getFrom();
				if (log.getStatus() == CallStatus.Missed) {
					holder.callDirection.setImageResource(R.drawable.call_status_missed);
				} else {
					holder.callDirection.setImageResource(R.drawable.call_status_incoming);
				}
			} else {
				address = log.getTo();
				holder.callDirection.setImageResource(R.drawable.call_status_outgoing);
			}

			Contact c = ContactsManager.getInstance().findContactWithAddress(getActivity().getContentResolver(), address);
			String displayName = null;
			final String sipUri = address.asStringUriOnly();
			if(c != null){
				displayName = c.getName();
				LinphoneUtils.setImagePictureFromUri(view.getContext(),holder.contactPicture,c.getPhotoUri(),c.getThumbnailUri());
			} else {
				holder.contactPicture.setImageResource(R.drawable.avatar);
			}

			if (displayName == null) {
				if (getResources().getBoolean(R.bool.only_display_username_if_unknown) && LinphoneUtils.isSipAddress(sipUri)) {
					holder.contact.setText(address.getUserName());
				} else {
					holder.contact.setText(sipUri);
				}
			} else {
				if (getResources().getBoolean(R.bool.only_display_username_if_unknown) && LinphoneUtils.isSipAddress(address.getDisplayName())) {
					holder.contact.setText(displayName);
				} else {
					holder.contact.setText(sipUri);
				}
			}
			//view.setTag(sipUri);

			if (isEditMode) {
				holder.delete.setVisibility(View.VISIBLE);
				holder.delete.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
					@Override
					public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
						historyList.setItemChecked(position, b);
					}
				});
				holder.detail.setVisibility(View.GONE);
				if(historyList.isItemChecked(position)) {
					holder.delete.setChecked(true);
				} else {
					holder.delete.setChecked(false);
				}
			} else {
				holder.delete.setVisibility(View.GONE);
				holder.detail.setVisibility(View.VISIBLE);
				holder.detail.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						if (LinphoneActivity.isInstanciated()) {
							LinphoneActivity.instance().displayHistoryDetail(sipUri, log);
						}
					}
				});
			}
			view.setTag(holder);
			return view;
		}
	}

	static class ViewHolder {
		TextView contact;
		ImageView detail;
		CheckBox delete;
		ImageView callDirection;
		ImageView contactPicture;
	}
}