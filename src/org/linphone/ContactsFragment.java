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
import java.util.List;

import org.linphone.compatibility.Compatibility;
import org.linphone.core.LinphoneFriend;
import org.linphone.core.PresenceActivityType;

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AlphabetIndexer;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SectionIndexer;
import android.widget.TextView;

/**
 * @author Sylvain Berfini
 */
@SuppressLint("DefaultLocale")
public class ContactsFragment extends Fragment implements OnClickListener, OnItemClickListener {
	private Handler mHandler = new Handler();
	
	private LayoutInflater mInflater;
	private ListView contactsList;
	private TextView allContacts, linphoneContacts, newContact, noSipContact, noContact;
	private boolean onlyDisplayLinphoneContacts;
	private int lastKnownPosition;
	private AlphabetIndexer indexer;
	private boolean editOnClick = false, editConsumed = false, onlyDisplayChatAddress = false;
	private String sipAddressToAdd;
	private ImageView clearSearchField;
	private EditText searchField;
	private Cursor searchCursor;

	private static ContactsFragment instance;
	
	static final boolean isInstanciated() {
		return instance != null;
	}

	public static final ContactsFragment instance() {
		return instance;
	}

	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, 
        Bundle savedInstanceState) {
		mInflater = inflater;
        View view = inflater.inflate(R.layout.contacts_list, container, false);
        
        if (getArguments() != null) {
	        editOnClick = getArguments().getBoolean("EditOnClick");
	        sipAddressToAdd = getArguments().getString("SipAddress");
	        
	        onlyDisplayChatAddress = getArguments().getBoolean("ChatAddressOnly");
        }
        
        noSipContact = (TextView) view.findViewById(R.id.noSipContact);
        noContact = (TextView) view.findViewById(R.id.noContact);
        
        contactsList = (ListView) view.findViewById(R.id.contactsList);
        contactsList.setOnItemClickListener(this);
        
        allContacts = (TextView) view.findViewById(R.id.allContacts);
        allContacts.setOnClickListener(this);
        
        linphoneContacts = (TextView) view.findViewById(R.id.linphoneContacts);
        linphoneContacts.setOnClickListener(this);
        
        newContact = (TextView) view.findViewById(R.id.newContact);
        newContact.setOnClickListener(this);
        newContact.setEnabled(LinphoneManager.getLc().getCallsNb() == 0);
        
        allContacts.setEnabled(onlyDisplayLinphoneContacts);
        linphoneContacts.setEnabled(!allContacts.isEnabled());
		
        
		clearSearchField = (ImageView) view.findViewById(R.id.clearSearchField);
		clearSearchField.setOnClickListener(this);
		
		searchField = (EditText) view.findViewById(R.id.searchField);
		searchField.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				
			}
			
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
				
			}
			
			@Override
			public void afterTextChanged(Editable s) {
				searchContacts(searchField.getText().toString());
			}
		});
        
		return view;
    }

	@Override
	public void onClick(View v) {
		int id = v.getId();
		
		if (id == R.id.allContacts) {
			onlyDisplayLinphoneContacts = false;
			if (searchField.getText().toString().length() > 0) {
				searchContacts();
			} else {
				changeContactsAdapter();
			}
		} 
		else if (id == R.id.linphoneContacts) {
			onlyDisplayLinphoneContacts = true;
			if (searchField.getText().toString().length() > 0) {
				searchContacts();
			} else {
				changeContactsAdapter();
			}
		} 
		else if (id == R.id.newContact) {
			editConsumed = true;
			LinphoneActivity.instance().addContact(null, sipAddressToAdd);
		} 
		else if (id == R.id.clearSearchField) {
			searchField.setText("");
		}
	}
	
	private void searchContacts() {
		searchContacts(searchField.getText().toString());
	}

	private void searchContacts(String search) {
		if (search == null || search.length() == 0) {
			changeContactsAdapter();
			return;
		}
		
		changeContactsToggle();
		
		if (searchCursor != null) {
			searchCursor.close();
		}
		
		if (onlyDisplayLinphoneContacts) {
			searchCursor = Compatibility.getSIPContactsCursor(getActivity().getContentResolver(), search);
			indexer = new AlphabetIndexer(searchCursor, Compatibility.getCursorDisplayNameColumnIndex(searchCursor), " ABCDEFGHIJKLMNOPQRSTUVWXYZ");
			contactsList.setAdapter(new ContactsListAdapter(null, searchCursor));
		} else {
			searchCursor = Compatibility.getContactsCursor(getActivity().getContentResolver(), search);
			indexer = new AlphabetIndexer(searchCursor, Compatibility.getCursorDisplayNameColumnIndex(searchCursor), " ABCDEFGHIJKLMNOPQRSTUVWXYZ");
			contactsList.setAdapter(new ContactsListAdapter(null, searchCursor));
		}
	}
	
	private void changeContactsAdapter() {
		changeContactsToggle();
		
		if (searchCursor != null) {
			searchCursor.close();
		}
		
		Cursor allContactsCursor = LinphoneActivity.instance().getAllContactsCursor();
		Cursor sipContactsCursor = LinphoneActivity.instance().getSIPContactsCursor();

		noSipContact.setVisibility(View.GONE);
		noContact.setVisibility(View.GONE);
		contactsList.setVisibility(View.VISIBLE);
		
		if (onlyDisplayLinphoneContacts) {
			if (sipContactsCursor.getCount() == 0) {
				noSipContact.setVisibility(View.VISIBLE);
				contactsList.setVisibility(View.GONE);
			} else {
				indexer = new AlphabetIndexer(sipContactsCursor, Compatibility.getCursorDisplayNameColumnIndex(sipContactsCursor), " ABCDEFGHIJKLMNOPQRSTUVWXYZ");
				contactsList.setAdapter(new ContactsListAdapter(LinphoneActivity.instance().getSIPContacts(), sipContactsCursor));
			}
		} else {
			if (allContactsCursor.getCount() == 0) {
				noContact.setVisibility(View.VISIBLE);
				contactsList.setVisibility(View.GONE);
			} else {
				indexer = new AlphabetIndexer(allContactsCursor, Compatibility.getCursorDisplayNameColumnIndex(allContactsCursor), " ABCDEFGHIJKLMNOPQRSTUVWXYZ");
				contactsList.setAdapter(new ContactsListAdapter(LinphoneActivity.instance().getAllContacts(), allContactsCursor));
			}
		}
		LinphoneActivity.instance().setLinphoneContactsPrefered(onlyDisplayLinphoneContacts);
	}
	
	private void changeContactsToggle() {
		if (onlyDisplayLinphoneContacts) {
			allContacts.setEnabled(true);
			linphoneContacts.setEnabled(false);
		} else {
			allContacts.setEnabled(false);
			linphoneContacts.setEnabled(true);
		}
	}

	@Override
	public void onItemClick(AdapterView<?> adapter, View view, int position, long id) {
		Contact contact = (Contact) adapter.getItemAtPosition(position);
		if (editOnClick) {
			editConsumed = true;
			LinphoneActivity.instance().editContact(contact, sipAddressToAdd);
		} else {
			lastKnownPosition = contactsList.getFirstVisiblePosition();
			LinphoneActivity.instance().displayContact(contact, onlyDisplayChatAddress);
		}
	}
	
	@Override
	public void onResume() {
		instance = this;
		super.onResume();
		
		if (editConsumed) {
			editOnClick = false;
			sipAddressToAdd = null;
		}
		
		if (LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().selectMenu(FragmentsAvailable.CONTACTS);
			onlyDisplayLinphoneContacts = LinphoneActivity.instance().isLinphoneContactsPrefered();
			
			if (getResources().getBoolean(R.bool.show_statusbar_only_on_dialer)) {
				LinphoneActivity.instance().hideStatusBar();
			}
		}
		
		invalidate();
	}
	
	@Override
	public void onPause() {
		instance = null;
		if (searchCursor != null) {
			searchCursor.close();
		}
		super.onPause();
	}
	
	public void invalidate() {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				if (searchField != null && searchField.getText().toString().length() > 0) {
					searchContacts(searchField.getText().toString());
				} else {
					changeContactsAdapter();
				}
				contactsList.setSelectionFromTop(lastKnownPosition, 0);
			}
		});
	}
	
	class ContactsListAdapter extends BaseAdapter implements SectionIndexer {
		private int margin;
		private Bitmap bitmapUnknown;
		private List<Contact> contacts;
		private Cursor cursor;
		
		ContactsListAdapter(List<Contact> contactsList, Cursor c) {
			contacts = contactsList;
			cursor = c;

			margin = LinphoneUtils.pixelsToDpi(getResources(), 10);
			bitmapUnknown = BitmapFactory.decodeResource(getResources(), R.drawable.unknown_small);
		}
		
		public int getCount() {
			return cursor.getCount();
		}

		public Object getItem(int position) {
			if (contacts == null || position >= contacts.size()) {
				return Compatibility.getContact(getActivity().getContentResolver(), cursor, position);
			} else {
				return contacts.get(position);
			}
		}

		public long getItemId(int position) {
			return position;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			View view = null;
			Contact contact = null;
			do {
				contact = (Contact) getItem(position);
			} while (contact == null);
			
			if (convertView != null) {
				view = convertView;
			} else {
				view = mInflater.inflate(R.layout.contact_cell, parent, false);
			}
			
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
				icon.setImageBitmap(contact.getPhoto());
			} else if (contact.getPhotoUri() != null) {
				icon.setImageURI(contact.getPhotoUri());
			} else {
				icon.setImageBitmap(bitmapUnknown);
			}
			
			ImageView friendStatus = (ImageView) view.findViewById(R.id.friendStatus);
			LinphoneFriend friend = contact.getFriend();
			if (!LinphoneActivity.instance().isContactPresenceDisabled() && friend != null) {
				friendStatus.setVisibility(View.VISIBLE);
				PresenceActivityType presenceActivity = friend.getPresenceModel().getActivity().getType();
				if (presenceActivity == PresenceActivityType.Online) {
					friendStatus.setImageResource(R.drawable.led_connected);
				} else if (presenceActivity == PresenceActivityType.Busy) {
					friendStatus.setImageResource(R.drawable.led_error);
				} else if (presenceActivity == PresenceActivityType.Away) {
					friendStatus.setImageResource(R.drawable.led_inprogress);
				} else if (presenceActivity == PresenceActivityType.Offline) {
					friendStatus.setImageResource(R.drawable.led_disconnected);
				} else {
					friendStatus.setImageResource(R.drawable.call_quality_indicator_0);
				}
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
