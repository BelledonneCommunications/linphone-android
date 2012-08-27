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
import android.support.v4.app.Fragment;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

/**
 * @author Sylvain Berfini
 */
public class HistorySimpleFragment extends Fragment implements OnClickListener, OnItemClickListener {
	private ListView historyList;
	private LayoutInflater mInflater;
	private ImageView allCalls, missedCalls, edit, ok;
	private boolean onlyDisplayMissedCalls, isEditMode;
	private List<LinphoneCallLog> mLogs; 
	
	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, 
        Bundle savedInstanceState) {
		mInflater = inflater;
        View view = inflater.inflate(R.layout.history_simple, container, false);
        
        historyList = (ListView) view.findViewById(R.id.historyList);
        historyList.setOnItemClickListener(this);
        registerForContextMenu(historyList);
        
        allCalls = (ImageView) view.findViewById(R.id.allCalls);
        allCalls.setOnClickListener(this);
        missedCalls = (ImageView) view.findViewById(R.id.missedCalls);
        missedCalls.setOnClickListener(this);
        allCalls.setEnabled(false);
        onlyDisplayMissedCalls = false;
        edit = (ImageView) view.findViewById(R.id.edit);
        edit.setOnClickListener(this);
        ok = (ImageView) view.findViewById(R.id.ok);
        ok.setOnClickListener(this);
        
		return view;
    }
	
	@Override
	public void onResume() {
		super.onResume();
		if (LinphoneActivity.isInstanciated())
			LinphoneActivity.instance().selectMenu(FragmentsAvailable.HISTORY);
		
		mLogs = Arrays.asList(LinphoneManager.getLc().getCallLogs());
        historyList.setAdapter(new CallHistoryAdapter(getActivity().getApplicationContext()));
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		menu.add(0, v.getId(), 0, getString(R.string.delete));
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		LinphoneCallLog log = mLogs.get(info.position);
		LinphoneManager.getLc().removeCallLog(log);
		mLogs = Arrays.asList(LinphoneManager.getLc().getCallLogs());
        historyList.setAdapter(new CallHistoryAdapter(getActivity().getApplicationContext()));
		return true;
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();
		
		if (id == R.id.allCalls) {
			allCalls.setEnabled(false);
			missedCalls.setEnabled(true);
			onlyDisplayMissedCalls = false;
			
			mLogs = Arrays.asList(LinphoneManager.getLc().getCallLogs());
		} 
		else if (id == R.id.missedCalls) {
			allCalls.setEnabled(true);
			missedCalls.setEnabled(false);
			onlyDisplayMissedCalls = true;
		} 
		else if (id == R.id.ok) {
			edit.setVisibility(View.VISIBLE);
			ok.setVisibility(View.GONE);
			isEditMode = false;
		} 
		else if (id == R.id.edit) {
			edit.setVisibility(View.GONE);
			ok.setVisibility(View.VISIBLE);
			isEditMode = true;
		}
		
		historyList.setAdapter(new CallHistoryAdapter(getActivity().getApplicationContext()));
	}

	@Override
	public void onItemClick(AdapterView<?> adapter, View view, int position, long id) {
		if (isEditMode) {
			LinphoneCallLog log = mLogs.get(position);
			LinphoneManager.getLc().removeCallLog(log);
			mLogs = Arrays.asList(LinphoneManager.getLc().getCallLogs());
	        historyList.setAdapter(new CallHistoryAdapter(getActivity().getApplicationContext()));
		} else {
			LinphoneCallLog log = mLogs.get(position);
			LinphoneAddress address;
			if (log.getDirection() == CallDirection.Incoming) {
				address = log.getFrom();
			} else {
				address = log.getTo();
			}
			LinphoneActivity.instance().displayHistoryDetail(address.asStringUriOnly(), log);
		}
	}
	
	class CallHistoryAdapter extends  BaseAdapter {
		private Bitmap missedCall, outgoingCall, incomingCall;
		
		CallHistoryAdapter(Context aContext) {
			missedCall = BitmapFactory.decodeResource(getResources(), R.drawable.call_status_missed);
			
			if (onlyDisplayMissedCalls) {
				List<LinphoneCallLog> missedCalls = new ArrayList<LinphoneCallLog>();
				for (LinphoneCallLog log : mLogs) {
					if (log.getStatus() == CallStatus.Missed) {
						missedCalls.add(log);
					}
				}
				mLogs = missedCalls;
			} else {
				outgoingCall = BitmapFactory.decodeResource(getResources(), R.drawable.call_status_outgoing);
				incomingCall = BitmapFactory.decodeResource(getResources(), R.drawable.call_status_incoming);
			}
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

		public View getView(int position, View convertView, ViewGroup parent) {
			View view = null;
			if (convertView != null) {
				view = convertView;
			} else {
				view = mInflater.inflate(R.layout.history_cell_simple, parent,false);
			}
			
			LinphoneCallLog log = mLogs.get(position);
			LinphoneAddress address;
			
			TextView contact = (TextView) view.findViewById(R.id.sipUri);
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
			String displayName = address.getDisplayName(); 
			String sipUri = address.asStringUriOnly();

			if (displayName == null) {
				if (getResources().getBoolean(R.bool.only_display_username_if_unknown) && LinphoneUtils.isSipAddress(sipUri)) {
					contact.setText(LinphoneUtils.getUsernameFromAddress(sipUri));
				} else {
					contact.setText(sipUri);
				}
			} else {
				if (getResources().getBoolean(R.bool.only_display_username_if_unknown) && LinphoneUtils.isSipAddress(address.getDisplayName())) {
					contact.setText(LinphoneUtils.getUsernameFromAddress(address.getDisplayName()));
				} else {
					contact.setText(displayName);
				}
			}
			view.setTag(sipUri);
			
			if (isEditMode) {
				delete.setVisibility(View.VISIBLE);
				detail.setVisibility(View.GONE);
			} else {
				delete.setVisibility(View.GONE);
				detail.setVisibility(View.VISIBLE);
			}

			return view;
		}
		  
	  }
}