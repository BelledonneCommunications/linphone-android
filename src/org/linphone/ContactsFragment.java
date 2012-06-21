package org.linphone;
/*
ContactsFragment.java
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
import java.io.InputStream;

import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.v4.app.Fragment;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AlphabetIndexer;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SectionIndexer;
import android.widget.TextView;

/**
 * @author Sylvain Berfini
 */
public class ContactsFragment extends Fragment implements OnClickListener, OnItemClickListener {
	private LayoutInflater mInflater;
	private ListView contactsList;
	private ImageView allContacts, linphoneContacts, newContact;
	private boolean onlyDisplayLinphoneCalls;
	private int lastKnownPosition;
	private Cursor cursor;
	
	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, 
        Bundle savedInstanceState) {
		mInflater = inflater;
        View view = inflater.inflate(R.layout.contacts_list, container, false);
        
        contactsList = (ListView) view.findViewById(R.id.contactsList);
        contactsList.setOnItemClickListener(this);
        
        allContacts = (ImageView) view.findViewById(R.id.allContacts);
        allContacts.setOnClickListener(this);
        linphoneContacts = (ImageView) view.findViewById(R.id.linphoneContacts);
        linphoneContacts.setOnClickListener(this);
        allContacts.setEnabled(false);
        onlyDisplayLinphoneCalls = false;
        newContact = (ImageView) view.findViewById(R.id.newContact);
        newContact.setOnClickListener(this);
        newContact.setEnabled(!LinphoneActivity.instance().isInCallLayout());
        
		return view;
    }

	@Override
	public void onClick(View v) {
		int id = v.getId();
		
		if (id == R.id.allContacts) {
			allContacts.setEnabled(false);
			linphoneContacts.setEnabled(true);
			onlyDisplayLinphoneCalls = false;
		} 
		else if (id == R.id.linphoneContacts) {
			allContacts.setEnabled(true);
			linphoneContacts.setEnabled(false);
			onlyDisplayLinphoneCalls = true;
		} 
		else if (id == R.id.newContact) {
			Intent intent = ContactHelper.prepareAddContactIntent(null, null);
			startActivity(intent);
		} 
	}

	@Override
	public void onItemClick(AdapterView<?> adapter, View view, int position, long id) {
		lastKnownPosition = contactsList.getFirstVisiblePosition();
		LinphoneActivity.instance().displayContact((Contact) adapter.getItemAtPosition(position));
	}
	
	@Override
	public void onResume() {
		super.onResume();
		if (LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().selectMenu(FragmentsAvailable.CONTACTS);
		}

		cursor = getActivity().getContentResolver().query(ContactsContract.Contacts.CONTENT_URI, null, ContactsContract.Contacts.DISPLAY_NAME + " IS NOT NULL", null, ContactsContract.Contacts.DISPLAY_NAME + " ASC");
		if (contactsList.getAdapter() == null) {
			contactsList.setAdapter(new ContactsListAdapter());
			contactsList.setFastScrollEnabled(true);
		}
		
		contactsList.setSelectionFromTop(lastKnownPosition, 0);
	}
	
	class ContactsListAdapter extends BaseAdapter implements SectionIndexer {
		private AlphabetIndexer indexer;
		private int margin;
		private Bitmap bitmapUnknown;
		
		ContactsListAdapter() {
			indexer = new AlphabetIndexer(cursor, cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME), " ABCDEFGHIJKLMNOPQRSTUVWXYZ");
			margin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics());
			bitmapUnknown = BitmapFactory.decodeResource(getResources(), R.drawable.unknown_small);
		}
		
		public int getCount() {
			return cursor.getCount();
		}

		public Object getItem(int position) {
			cursor.moveToFirst();
			boolean success = cursor.move(position);
			if (!success)
				return null;
			
			String id = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID));
	    	String name = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
	    	Uri person = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, Long.parseLong(id));
	        Uri photo = Uri.withAppendedPath(person, ContactsContract.Contacts.Photo.CONTENT_DIRECTORY);
	        
	        InputStream input = ContactsContract.Contacts.openContactPhotoInputStream(getActivity().getContentResolver(), person);
	        Contact contact;
	        if (input == null) {
	        	contact = new Contact(id, name);
	        }
	        else {
	        	contact = new Contact(id, name, photo);
	        }
	        
	        contact.setNumerosOrAddresses(ContactHelper.extractContactNumbersAndAddresses(contact.getID(), getActivity().getContentResolver()));
	        
	        return contact;
		}

		public long getItemId(int position) {
			return position;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			View view = null;
			
			if (convertView != null) {
				view = convertView;
			} else {
				view = mInflater.inflate(R.layout.contact_cell, parent, false);
			}
			
			Contact contact = (Contact) getItem(position);
			
			TextView name = (TextView) view.findViewById(R.id.name);
			name.setText(contact.getName());
			
			TextView separator = (TextView) view.findViewById(R.id.separator);
			LinearLayout layout = (LinearLayout) view.findViewById(R.id.layout);
			if (getPositionForSection(getSectionForPosition(position)) != position) {
				separator.setVisibility(View.GONE);
				layout.setPadding(0, margin, 0, margin);
			} else {
				separator.setVisibility(View.VISIBLE);
				separator.setText(String.valueOf(contact.getName().charAt(0)));
				layout.setPadding(0, 0, 0, margin);
			}
			
			ImageView icon = (ImageView) view.findViewById(R.id.icon);
			if (contact.getPhoto() != null) {
				icon.setImageURI(contact.getPhoto());
			} else {
				icon.setImageBitmap(bitmapUnknown);
			}
			
			return view;
		}

		@Override
		public int getPositionForSection(int section) {
			return indexer.getPositionForSection(section);
		}

		@Override
		public int getSectionForPosition(int position) {
			return indexer.getSectionForPosition(position);
		}

		@Override
		public Object[] getSections() {
			return indexer.getSections();
		}
	}
}
