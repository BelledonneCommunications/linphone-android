package org.linphone.purchase;

/*
InAppPurchaseListFragment.java
Copyright (C) 2016  Belledonne Communications, Grenoble, France

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

import java.util.List;

import org.linphone.R;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class InAppPurchaseListFragment extends Fragment implements AdapterView.OnItemClickListener{
	private ListView inappList;
	private LayoutInflater mInflater;
	private List<Purchasable> mPurchasableItems;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
							 Bundle savedInstanceState) {
		mInflater = inflater;
		View view = inflater.inflate(R.layout.in_app_list, container, false);

		mPurchasableItems = InAppPurchaseActivity.instance().getPurchasedItems();
		inappList = (ListView) view.findViewById(R.id.inapp_list);

		if(mPurchasableItems != null){
			inappList.setAdapter(new InAppListAdapter());
			inappList.setOnItemClickListener(this);
		}
		return view;
	}

	class InAppListAdapter extends BaseAdapter {
		InAppListAdapter() {}

		public int getCount() {
			return mPurchasableItems.size();
		}

		public Object getItem(int position) {
			return mPurchasableItems.get(position);
		}

		public long getItemId(int position) {
			return position;
		}

		public View getView(final int position, View convertView, ViewGroup parent) {
			View view = null;
			if (convertView != null) {
				view = convertView;
			} else {
				view = mInflater.inflate(R.layout.in_app_purchase_item, parent, false);
			}

			final Purchasable item = mPurchasableItems.get(position);

			TextView itemTitle = (TextView) view.findViewById(R.id.purchase_title);
			TextView itemDesc = (TextView) view.findViewById(R.id.purchase_description);
			TextView itemPrice = (TextView) view.findViewById(R.id.purchase_price);

			itemTitle.setText(item.getTitle());
			itemDesc.setText(item.getDescription());
			itemPrice.setText(item.getPrice());

			view.setTag(item);
			return view;
		}
	}


	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		Purchasable item = (Purchasable) view.getTag();
		InAppPurchaseActivity.instance().displayPurchase(item);
	}
}
