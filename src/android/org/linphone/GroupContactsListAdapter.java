package org.linphone;

/*
GroupContactsListAdapter.java
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

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

public class GroupContactsListAdapter extends BaseAdapter {
	private class ViewHolder {
		public TextView name;
		public TextView admin;
		public ImageView linphoneContact;
		public ImageView isAdmin;
		public ImageView photo;

		public ViewHolder(View view) {
			name = (TextView) view.findViewById(R.id.sip_uri);
			linphoneContact = (ImageView) view.findViewById(R.id.contact_linphone);
			isAdmin = (ImageView) view.findViewById(R.id.contact_is_select);
			admin = (TextView) view.findViewById(R.id.admin);
			photo = (ImageView) view.findViewById(R.id.contact_picture);
		}
	}

	public List<ContactAddress> getGroupContacts() {
		return groupContacts;
	}

	public void setGroupContacts(List<ContactAddress> groupContacts) {
		this.groupContacts = groupContacts;
	}

	private List<ContactAddress> groupContacts;
	private LayoutInflater mInflater;

	public GroupContactsListAdapter(LayoutInflater inf) {
		mInflater = inf;
	}

	@Override
	public int getCount() {
		return groupContacts.size();
	}

	@Override
	public Object getItem(int i) {
		return groupContacts.get(i);
	}

	@Override
	public long getItemId(int i) {
		return i;
	}

	@Override
	public View getView(int i, View convertView, ViewGroup parent) {
		View view;
		ViewHolder holder;

		if (convertView != null) {
			view = convertView;
			holder = (ViewHolder) view.getTag();
		} else {
			view = mInflater.inflate(R.layout.search_contact_cell, parent, false);
			holder = new ViewHolder(view);
			view.setTag(holder);
		}

		return view;
	}
}
