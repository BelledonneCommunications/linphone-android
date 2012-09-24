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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.linphone.core.CallDirection;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCallLog;
import org.linphone.core.LinphoneCallLog.CallStatus;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.util.SparseArray;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.ExpandableListView.OnGroupClickListener;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * @author Sylvain Berfini
 */
public class HistoryFragment extends Fragment implements OnClickListener, OnChildClickListener, OnGroupClickListener {
	private Handler mHandler = new Handler();
	private ExpandableListView historyList;
	private LayoutInflater mInflater;
	private TextView allCalls, missedCalls, edit, ok, deleteAll, noCallHistory, noMissedCallHistory;
	private boolean onlyDisplayMissedCalls, isEditMode;
	private SparseArray<List<LinphoneCallLog>> mLogs; 
	
	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, 
        Bundle savedInstanceState) {
		mInflater = inflater;
        View view = inflater.inflate(R.layout.history, container, false);
        
        noCallHistory = (TextView) view.findViewById(R.id.noCallHistory);
        noMissedCallHistory = (TextView) view.findViewById(R.id.noMissedCallHistory);
        
        historyList = (ExpandableListView) view.findViewById(R.id.historyList);
        historyList.setOnChildClickListener(this);
        historyList.setOnGroupClickListener(this);
        
        deleteAll = (TextView) view.findViewById(R.id.deleteAll);
        deleteAll.setOnClickListener(this);
        deleteAll.setVisibility(View.INVISIBLE);
        
        allCalls = (TextView) view.findViewById(R.id.allCalls);
        allCalls.setOnClickListener(this);
        
        missedCalls = (TextView) view.findViewById(R.id.missedCalls);
        missedCalls.setOnClickListener(this);
        
        allCalls.setEnabled(false);
        onlyDisplayMissedCalls = false;
        
        edit = (TextView) view.findViewById(R.id.edit);
        edit.setOnClickListener(this);
        
        ok = (TextView) view.findViewById(R.id.ok);
        ok.setOnClickListener(this);
        
		return view;
    }
	
	private boolean hideHistoryListAndDisplayMessageIfEmpty() {
		if (mLogs.size() == 0) {
			if (onlyDisplayMissedCalls) {
				noMissedCallHistory.setVisibility(View.VISIBLE);
			} else {
				noCallHistory.setVisibility(View.VISIBLE);
			}
			historyList.setVisibility(View.GONE);
			return true;
		} else {
			noCallHistory.setVisibility(View.GONE);
			noMissedCallHistory.setVisibility(View.GONE);
			historyList.setVisibility(View.VISIBLE);
			return false;
		}
	}
	
	@Override
	public void onResume() {
		super.onResume();
		if (LinphoneActivity.isInstanciated())
			LinphoneActivity.instance().selectMenu(FragmentsAvailable.HISTORY);
		
		initLogsLists(Arrays.asList(LinphoneManager.getLc().getCallLogs()));
		if (!hideHistoryListAndDisplayMessageIfEmpty()) {
			historyList.setAdapter(new CallHistoryAdapter(getActivity().getApplicationContext()));
		}
        expandAllGroups();
	}
	
	private void initLogsLists(List<LinphoneCallLog> logs) {
		mLogs = new SparseArray<List<LinphoneCallLog>>(); 
		String[] keys = new String[logs.size()];
		for (LinphoneCallLog log : logs) {
			String groupBy = getCorrespondentDisplayName(log);
			int key = -1;
			for (int k = 0; k < keys.length; k++) {
				if (keys[k] == null || keys[k].equals(groupBy)) {
					key = k;
					keys[k] = groupBy;
					break;
				}
			}
			
			List<LinphoneCallLog> group = mLogs.get(key, new ArrayList<LinphoneCallLog>());
			group.add(log);
			if (group.size() == 1) {
				mLogs.append(key, group);
			}
		}
	}
	
	private void initMissedLogsLists(List<LinphoneCallLog> logs) {
		List<LinphoneCallLog> missedLogs = new ArrayList<LinphoneCallLog>();
		for (LinphoneCallLog log : logs) {
			if (log.getDirection() == CallDirection.Incoming && log.getStatus() == CallStatus.Missed) {
				missedLogs.add(log);
			}
		}
		initLogsLists(missedLogs);
	}
	
	private void expandAllGroups() {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				for (int groupToExpand = 0; groupToExpand < historyList.getExpandableListAdapter().getGroupCount(); groupToExpand++) {
					if (!historyList.isGroupExpanded(groupToExpand)) {
						historyList.expandGroup(groupToExpand);
					}
				}
			}
		});
	}
	
	private String getCorrespondentDisplayName(LinphoneCallLog log) {
		String displayName;
		LinphoneAddress address;
		if (log.getDirection() == CallDirection.Incoming) {
			address = log.getFrom();
		} else {
			address = log.getTo();
		}
		
		LinphoneUtils.findUriPictureOfContactAndSetDisplayName(address, getActivity().getContentResolver());
		displayName = address.getDisplayName(); 
		String sipUri = address.asStringUriOnly();
		if (displayName == null) {
			if (getResources().getBoolean(R.bool.only_display_username_if_unknown) && LinphoneUtils.isSipAddress(sipUri)) {
				displayName = LinphoneUtils.getUsernameFromAddress(sipUri);
			} else {
				displayName = sipUri;
			}
		}
		
		return displayName;
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		menu.add(0, v.getId(), 0, getString(R.string.delete));
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();
		
		if (id == R.id.allCalls) {
			allCalls.setEnabled(false);
			missedCalls.setEnabled(true);
			onlyDisplayMissedCalls = false;
			
			initLogsLists(Arrays.asList(LinphoneManager.getLc().getCallLogs()));
		} 
		else if (id == R.id.missedCalls) {
			allCalls.setEnabled(true);
			missedCalls.setEnabled(false);
			onlyDisplayMissedCalls = true;
			
			initMissedLogsLists(Arrays.asList(LinphoneManager.getLc().getCallLogs()));
		} 
		else if (id == R.id.ok) {
			edit.setVisibility(View.VISIBLE);
			ok.setVisibility(View.GONE);
			hideDeleteAllButton();
			isEditMode = false;
		} 
		else if (id == R.id.edit) {
			edit.setVisibility(View.GONE);
			ok.setVisibility(View.VISIBLE);
			showDeleteAllButton();
			isEditMode = true;
		}
		else if (id == R.id.deleteAll) {
			LinphoneManager.getLc().clearCallLogs();
			initLogsLists(new ArrayList<LinphoneCallLog>());
		}
		
		if (!hideHistoryListAndDisplayMessageIfEmpty()) {
			historyList.setAdapter(new CallHistoryAdapter(getActivity().getApplicationContext()));
		}
		expandAllGroups();
	}
	
	@Override
	public boolean onGroupClick(ExpandableListView parent, View v, int groupPosition, long id) {
		if (isEditMode) {
			for (LinphoneCallLog log : mLogs.get(groupPosition)) {
				LinphoneManager.getLc().removeCallLog(log);
			}
			
			initLogsLists(Arrays.asList(LinphoneManager.getLc().getCallLogs()));
			if (!hideHistoryListAndDisplayMessageIfEmpty()) {
				historyList.setAdapter(new CallHistoryAdapter(getActivity().getApplicationContext()));
			}
	        expandAllGroups();
		}
		return false;
	}

	@Override
	public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {
		LinphoneCallLog log = mLogs.get(groupPosition).get(childPosition);
		if (isEditMode) {
			LinphoneManager.getLc().removeCallLog(log);
			initLogsLists(Arrays.asList(LinphoneManager.getLc().getCallLogs()));
			if (!hideHistoryListAndDisplayMessageIfEmpty()) {
				historyList.setAdapter(new CallHistoryAdapter(getActivity().getApplicationContext()));
			}
	        expandAllGroups();
		} else {
			LinphoneAddress address;
			if (log.getDirection() == CallDirection.Incoming) {
				address = log.getFrom();
			} else {
				address = log.getTo();
			}
			LinphoneActivity.instance().setAddresGoToDialerAndCall(address.asStringUriOnly(), address.getDisplayName(), null);
		}
		return false;
	}
	
	private void hideDeleteAllButton() {
		if (deleteAll == null || deleteAll.getVisibility() != View.VISIBLE) {
			return;
		}
			
		if (LinphoneActivity.instance().isAnimationDisabled()) {
			deleteAll.setVisibility(View.INVISIBLE);
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
					deleteAll.setVisibility(View.INVISIBLE);
					animation.setAnimationListener(null);
				}
			});
			deleteAll.startAnimation(animation);
		}
	}
	
	private void showDeleteAllButton() {
		if (deleteAll == null || deleteAll.getVisibility() == View.VISIBLE) {
			return;
		}
		
		if (LinphoneActivity.instance().isAnimationDisabled()) {
			deleteAll.setVisibility(View.VISIBLE);
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
					deleteAll.setVisibility(View.VISIBLE);
					animation.setAnimationListener(null);
				}
			});
			deleteAll.startAnimation(animation);
		}
	}
	
	class CallHistoryAdapter extends BaseExpandableListAdapter {
		private Bitmap missedCall, outgoingCall, incomingCall;
		
		CallHistoryAdapter(Context aContext) {
			missedCall = BitmapFactory.decodeResource(getResources(), R.drawable.call_status_missed);
			
			if (!onlyDisplayMissedCalls) {
				outgoingCall = BitmapFactory.decodeResource(getResources(), R.drawable.call_status_outgoing);
				incomingCall = BitmapFactory.decodeResource(getResources(), R.drawable.call_status_incoming);
			}
		}
		
		@Override
		public Object getChild(int groupPosition, int childPosition) {
			return mLogs.get(groupPosition).get(childPosition);
		}
		
		@Override
		public long getChildId(int groupPosition, int childPosition) {
			return childPosition;
		}
		
		@Override
		public View getChildView(int groupPosition, int childPosition,
				boolean isLastChild, View convertView, ViewGroup parent) {
			View view = null;
			if (convertView != null) {
				view = convertView;
			} else {
				view = mInflater.inflate(R.layout.history_cell, parent,false);
			}
			
			final LinphoneCallLog log = (LinphoneCallLog) getChild(groupPosition, childPosition);
			final LinphoneAddress address;
			
			TextView dateAndTime = (TextView) view.findViewById(R.id.dateAndTime);
			ImageView detail = (ImageView) view.findViewById(R.id.detail);
			ImageView delete = (ImageView) view.findViewById(R.id.delete);
			ImageView callDirection = (ImageView) view.findViewById(R.id.icon);
			
			
			if (log.getDirection() == CallDirection.Incoming) {
				address = log.getFrom();
				if (log.getStatus() == CallStatus.Missed) {
					callDirection.setImageBitmap(missedCall);
				} else {
					callDirection.setImageBitmap(incomingCall);
				}
			} else {
				address = log.getTo();
				callDirection.setImageBitmap(outgoingCall);
			}
			
			LinphoneUtils.findUriPictureOfContactAndSetDisplayName(address, view.getContext().getContentResolver());
			String sipUri = address.asStringUriOnly();
			dateAndTime.setText(log.getStartDate() + " " + log.getCallDuration());
			view.setTag(sipUri);
			
			if (isEditMode) {
				delete.setVisibility(View.VISIBLE);
				detail.setVisibility(View.GONE);
			} else {
				delete.setVisibility(View.GONE);
				detail.setVisibility(View.VISIBLE);
				detail.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						if (LinphoneActivity.isInstanciated()) {
							LinphoneActivity.instance().displayHistoryDetail(address.asStringUriOnly(), log);
						}
					}
				});
			}

			return view;
		}
		
		@Override
		public int getChildrenCount(int groupPosition) {
			return mLogs.get(groupPosition).size();
		}
		
		@Override
		public Object getGroup(int groupPosition) {
			return mLogs.get(groupPosition);
		}
		
		@Override
		public int getGroupCount() {
			return mLogs.size();
		}
		
		@Override
		public long getGroupId(int groupPosition) {
			return groupPosition;
		}
		
		@Override
		public View getGroupView(int groupPosition, boolean isExpanded,
				View convertView, ViewGroup parent) {
			View view = null;
			if (convertView != null) {
				view = convertView;
			} else {
				view = mInflater.inflate(R.layout.history_group, parent,false);
			}
			
			LinphoneCallLog log = (LinphoneCallLog) getChild(groupPosition, 0);			
			LinphoneAddress address;
			
			TextView contact = (TextView) view.findViewById(R.id.sipUri);
			ImageView delete = (ImageView) view.findViewById(R.id.delete);
			
			if (log.getDirection() == CallDirection.Incoming) {
				address = log.getFrom();
			} else {
				address = log.getTo();
			}
			
			LinphoneUtils.findUriPictureOfContactAndSetDisplayName(address, view.getContext().getContentResolver());
			String displayName = getCorrespondentDisplayName(log);
			String sipUri = address.asStringUriOnly();
			contact.setText(displayName + " (" + getChildrenCount(groupPosition) + ")");
			view.setTag(sipUri);
			
			if (isEditMode) {
				delete.setVisibility(View.VISIBLE);
			} else {
				delete.setVisibility(View.GONE);
			}

			return view;
		}
		
		@Override
		public boolean hasStableIds() {
			return false;
		}
		
		@Override
		public boolean isChildSelectable(int groupPosition, int childPosition) {
			return true;
		}
	}
}
