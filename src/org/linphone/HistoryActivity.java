/*
DialerActivity.java
Copyright (C) 2010  Belledonne Communications, Grenoble, France

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
package org.linphone;



import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.linphone.core.CallDirection;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCallLog;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.Log;

import android.app.ListActivity;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class HistoryActivity extends ListActivity {
	LayoutInflater mInflater;  
	@Override
	    public void onCreate(Bundle savedInstanceState) {
	        super.onCreate(savedInstanceState);
	        mInflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	    }
	
	  
	  @Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		TextView lFirstLineView = (TextView) v.findViewById(R.id.history_cell_first_line);
		TextView lSecondLineView = (TextView) v.findViewById(R.id.history_cell_second_line);
		ContactPicked parent = (ContactPicked) getParent();
		if (lSecondLineView.getVisibility() == View.GONE) {
			// no display name
			parent.setAddressAndGoToDialer(lFirstLineView.getText().toString(), null, null);
		} else {
			parent.setAddressAndGoToDialer(
					lSecondLineView.getText().toString(),
					lFirstLineView.getText().toString(),
					null);
		}
	}


	@Override
	protected void onResume() {
		super.onResume();
		 setListAdapter(new CallHistoryAdapter(this));
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the currently selected menu XML resource.
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.history_activity_menu, menu);
		return true;
	}
	
	// Fix the menu from crashing the activity
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.menu_clear_history) {
			LinphoneManager.getLc().clearCallLogs();
			setListAdapter(new CallHistoryAdapter(this));
		}
		else {
			Log.e("Unknown menu item [",item,"]");
		}

		return false;
	}
	
	class CallHistoryAdapter extends  BaseAdapter {
		final List<LinphoneCallLog> mLogs; 

		CallHistoryAdapter(Context aContext) {
			mLogs = Arrays.asList(LinphoneManager.getLc().getCallLogs());
		}
		public int getCount() {
			return mLogs.size();
		}

		public Object getItem(int position) {
			return position;
		}

		public long getItemId(int position) {
			
			return position;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			View lView=null;
			if (convertView !=null) {
				lView = convertView;
			} else {
				lView = mInflater.inflate(R.layout.history_cell, parent,false);
				
			}
			LinphoneCallLog lLog = mLogs.get(position);
			LinphoneAddress lAddress;
			TextView lFirstLineView = (TextView) lView.findViewById(R.id.history_cell_first_line);
			TextView lSecondLineView = (TextView) lView.findViewById(R.id.history_cell_second_line);
			ImageView lDirectionImageIn = (ImageView) lView.findViewById(R.id.history_cell_icon_in);
			ImageView lDirectionImageOut = (ImageView) lView.findViewById(R.id.history_cell_icon_out);
			ImageView lContactPicture = (ImageView) lView.findViewById(R.id.history_cell_icon_contact);
			
			if (lLog.getDirection() == CallDirection.Incoming) {
				lAddress = lLog.getFrom();
				lDirectionImageIn.setVisibility(View.VISIBLE);
				lDirectionImageOut.setVisibility(View.GONE);
				
			} else {
				lAddress = lLog.getTo();
				lDirectionImageIn.setVisibility(View.GONE);
				lDirectionImageOut.setVisibility(View.VISIBLE);
			}

			Uri uri = LinphoneUtils.findUriPictureOfContactAndSetDisplayName(lAddress, getContentResolver());
			LinphoneUtils.setImagePictureFromUri(lView.getContext(), lContactPicture, uri, R.drawable.unknown_person);

			LinphoneCore lc = LinphoneManager.getLc();
			LinphoneProxyConfig lProxyConfig = lc.getDefaultProxyConfig();
			boolean showOnlyUsername = getResources().getBoolean(R.bool.show_only_username_in_history);
			String lDetailedName=null;
			String lDisplayName = !showOnlyUsername ? lAddress.getDisplayName() : null; 
			if (showOnlyUsername || (lProxyConfig != null && lProxyConfig.getDomain().equals(lAddress.getDomain()))) {
				lDetailedName = lAddress.getUserName();
			} else {
				lDetailedName = lAddress.asStringUriOnly();
			}

			if (lDisplayName == null) {
				lFirstLineView.setText(lDetailedName);
				lSecondLineView.setVisibility(View.GONE);
			} else {
				lFirstLineView.setText(lDisplayName);
				lSecondLineView.setText(lDetailedName);
				lSecondLineView.setVisibility(View.VISIBLE);
			}

			return lView;
			
		}
		  
	  }
}
