/*
ContactsListFragment.java
Copyright (C) 2015  Belledonne Communications, Grenoble, France

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.linphone.core.LinphoneFriend;
import org.linphone.core.PresenceActivityType;

import android.app.Dialog;
import android.app.Fragment;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SectionIndexer;
import android.widget.TextView;

/**
 * @author Sylvain Berfini
 */
public class ContactsListFragment extends Fragment implements OnClickListener, OnItemClickListener, ContactsUpdatedListener {
	private LayoutInflater mInflater;
	private ListView contactsList;
	private TextView noSipContact, noContact;
	private ImageView allContacts, linphoneContacts, newContact, edit, selectAll, deselectAll, delete, cancel;
	private boolean onlyDisplayLinphoneContacts, isEditMode;
	private View allContactsSelected, linphoneContactsSelected;
	private LinearLayout editList, topbar;
	private int lastKnownPosition;
	private boolean editOnClick = false, editConsumed = false, onlyDisplayChatAddress = false;
	private String sipAddressToAdd;
	private ImageView clearSearchField;
	private EditText searchField;

	private static ContactsListFragment instance;
	
	static final boolean isInstanciated() {
		return instance != null;
	}

	public static final ContactsListFragment instance() {
		return instance;
	}

	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
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
        
        allContacts = (ImageView) view.findViewById(R.id.all_contacts);
        allContacts.setOnClickListener(this);
        
        linphoneContacts = (ImageView) view.findViewById(R.id.linphone_contacts);
        linphoneContacts.setOnClickListener(this);

		allContactsSelected = view.findViewById(R.id.all_contacts_select);
		linphoneContactsSelected = view.findViewById(R.id.linphone_contacts_select);
        
        newContact = (ImageView) view.findViewById(R.id.newContact);
        newContact.setOnClickListener(this);
        newContact.setEnabled(LinphoneManager.getLc().getCallsNb() == 0);
        
        allContacts.setEnabled(onlyDisplayLinphoneContacts);
        linphoneContacts.setEnabled(!allContacts.isEnabled());

		selectAll = (ImageView) view.findViewById(R.id.select_all);
		selectAll.setOnClickListener(this);

		deselectAll = (ImageView) view.findViewById(R.id.deselect_all);
		deselectAll.setOnClickListener(this);

		delete = (ImageView) view.findViewById(R.id.delete);
		delete.setOnClickListener(this);

		editList = (LinearLayout) view.findViewById(R.id.edit_list);
		topbar = (LinearLayout) view.findViewById(R.id.top_bar);

		cancel = (ImageView) view.findViewById(R.id.cancel);
		cancel.setOnClickListener(this);

		edit = (ImageView) view.findViewById(R.id.edit);
		edit.setOnClickListener(this);
        
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

	public int getNbItemsChecked(){
		int size = contactsList.getAdapter().getCount();
		int nb = 0;
		for(int i=0; i<size; i++) {
			if(contactsList.isItemChecked(i)) {
				nb ++;
			}
		}
		return nb;
	}

	public void enabledDeleteButton(Boolean enabled){
		if(enabled){
			delete.setEnabled(true);
		} else {
			if (getNbItemsChecked() == 0){
				delete.setEnabled(false);
			}
		}
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();

		if (id == R.id.select_all) {
			deselectAll.setVisibility(View.VISIBLE);
			selectAll.setVisibility(View.GONE);
			enabledDeleteButton(true);
			selectAllList(true);
			return;
		}
		if (id == R.id.deselect_all) {
			deselectAll.setVisibility(View.GONE);
			selectAll.setVisibility(View.VISIBLE);
			enabledDeleteButton(false);
			selectAllList(false);
			return;
		}

		if (id == R.id.cancel) {
			quitEditMode();
			return;
		}

		if (id == R.id.delete) {
			final Dialog dialog = LinphoneActivity.instance().displayDialog(getString(R.string.delete_text));
			Button delete = (Button) dialog.findViewById(R.id.delete_button);
			Button cancel = (Button) dialog.findViewById(R.id.cancel);

			delete.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
					removeContacts();
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
			editList.setVisibility(View.VISIBLE);
			topbar.setVisibility(View.GONE);
			enabledDeleteButton(false);
			isEditMode = true;
		}
		
		if (id == R.id.all_contacts) {
			onlyDisplayLinphoneContacts = false;
			allContactsSelected.setVisibility(View.VISIBLE);
			allContacts.setEnabled(false);
			linphoneContacts.setEnabled(true);
			linphoneContactsSelected.setVisibility(View.INVISIBLE);
		} 
		else if (id == R.id.linphone_contacts) {
			allContactsSelected.setVisibility(View.INVISIBLE);
			linphoneContactsSelected.setVisibility(View.VISIBLE);
			linphoneContacts.setEnabled(false);
			allContacts.setEnabled(true);
			onlyDisplayLinphoneContacts = true;

		}

		if(isEditMode){
			deselectAll.setVisibility(View.GONE);
			selectAll.setVisibility(View.VISIBLE);
		}

		if (searchField.getText().toString().length() > 0) {
			searchContacts();
		} else {
			changeContactsAdapter();
		}

		if (id == R.id.newContact) {
			editConsumed = true;
			LinphoneActivity.instance().addContact(null, sipAddressToAdd);
		} 
		else if (id == R.id.clearSearchField) {
			searchField.setText("");
		}
	}

	private void selectAllList(boolean isSelectAll){
		int size = contactsList.getAdapter().getCount();
		for(int i=0; i<size; i++) {
			contactsList.setItemChecked(i,isSelectAll);
		}
	}

	private void removeContacts() {
		ArrayList<String> ids = new ArrayList<String>();
		int size = contactsList.getAdapter().getCount();
		
		for (int i = size - 1; i >= 0; i--) {
			if (contactsList.isItemChecked(i)) {
				LinphoneContact contact = (LinphoneContact) contactsList.getAdapter().getItem(i);
				if (contact.isAndroidContact()) {
					contact.deleteFriend();
					ids.add(contact.getAndroidId());
				} else {
					contact.delete();
				}
			}
		}
		
		ContactsManager.getInstance().deleteMultipleContactsAtOnce(ids);
	}

	public void quitEditMode(){
		isEditMode = false;
		editList.setVisibility(View.GONE);
		topbar.setVisibility(View.VISIBLE);
		invalidate();
		if(getResources().getBoolean(R.bool.isTablet)){
			displayFirstContact();
		}
	}

	public void displayFirstContact(){
		if(contactsList.getAdapter() != null && contactsList.getAdapter().getCount() > 0) {
			LinphoneActivity.instance().displayContact((LinphoneContact) contactsList.getAdapter().getItem(0), false);
		} else {
			LinphoneActivity.instance().displayEmptyFragment();
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

		if (onlyDisplayLinphoneContacts) {
			contactsList.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
			contactsList.setAdapter(new ContactsListAdapter(ContactsManager.getInstance().getSIPContacts()));
		} else {
			contactsList.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
			contactsList.setAdapter(new ContactsListAdapter(ContactsManager.getInstance().getContacts()));
		}
	}
	
	private void changeContactsAdapter() {
		changeContactsToggle();

		noSipContact.setVisibility(View.GONE);
		noContact.setVisibility(View.GONE);
		contactsList.setVisibility(View.VISIBLE);

		if (onlyDisplayLinphoneContacts) {
			contactsList.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
			contactsList.setAdapter(new ContactsListAdapter(ContactsManager.getInstance().getSIPContacts()));
			edit.setEnabled(true);
		} else {
			contactsList.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
			contactsList.setAdapter(new ContactsListAdapter(ContactsManager.getInstance().getContacts()));
			edit.setEnabled(true);
		}
		ContactsManager.getInstance().setLinphoneContactsPrefered(onlyDisplayLinphoneContacts);
	}
	
	private void changeContactsToggle() {
		if (onlyDisplayLinphoneContacts) {
			allContacts.setEnabled(true);
			allContactsSelected.setVisibility(View.INVISIBLE);
			linphoneContacts.setEnabled(false);
			linphoneContactsSelected.setVisibility(View.VISIBLE);
		} else {
			allContacts.setEnabled(false);
			allContactsSelected.setVisibility(View.VISIBLE);
			linphoneContacts.setEnabled(true);
			linphoneContactsSelected.setVisibility(View.INVISIBLE);
		}
	}

	@Override
	public void onItemClick(AdapterView<?> adapter, View view, int position, long id) {
		LinphoneContact contact = (LinphoneContact) adapter.getItemAtPosition(position);
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
		ContactsManager.addContactsListener(this);
		super.onResume();

		if (editConsumed) {
			editOnClick = false;
			sipAddressToAdd = null;
		}

		if (LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().selectMenu(FragmentsAvailable.CONTACTS_LIST);
			LinphoneActivity.instance().hideTabBar(false);
			onlyDisplayLinphoneContacts = ContactsManager.getInstance().isLinphoneContactsPrefered();
		}
		changeContactsToggle();
		invalidate();
	}
	
	@Override
	public void onPause() {
		instance = null;
		ContactsManager.removeContactsListener(this);
		super.onPause();
	}
	
	@Override
	public void onContactsUpdated() {
		invalidate();
	}
	
	public void invalidate() {
		if (searchField != null && searchField.getText().toString().length() > 0) {
			searchContacts(searchField.getText().toString());
		} else {
			changeContactsAdapter();
		}
		contactsList.setSelectionFromTop(lastKnownPosition, 0);
	}
	
	class ContactsListAdapter extends BaseAdapter implements SectionIndexer {
		private List<LinphoneContact> contacts;
		String[] sections;
		ArrayList<String> sectionsList;
		Map<String, Integer>map = new LinkedHashMap<String, Integer>();
		
		ContactsListAdapter(List<LinphoneContact> contactsList) {
			contacts = contactsList;
			
			map = new LinkedHashMap<String, Integer>();
			String prevLetter = null;
			for (int i = 0; i < contacts.size(); i++) {
				LinphoneContact contact = contacts.get(i);
				String firstLetter = contact.getFullName().substring(0, 1).toUpperCase(Locale.getDefault());
				if (!firstLetter.equals(prevLetter)) {
					prevLetter = firstLetter;
					map.put(firstLetter, i);
				}
			}
			sectionsList = new ArrayList<String>(map.keySet());
			sections = new String[sectionsList.size()];
			sectionsList.toArray(sections);
		}
		
		public int getCount() {
			return contacts.size();
		}

		public Object getItem(int position) {
			if (position >= getCount()) return null;
			return contacts.get(position);
		}

		public long getItemId(int position) {
			return position;
		}

		public View getView(final int position, View convertView, ViewGroup parent) {
			View view = null;
			LinphoneContact contact = null;
			do {
				contact = (LinphoneContact) getItem(position);
			} while (contact == null);
			
			if (convertView != null) {
				view = convertView;
			} else {
				view = mInflater.inflate(R.layout.contact_cell, parent, false);
			}

			CheckBox delete = (CheckBox) view.findViewById(R.id.delete);
			
			TextView name = (TextView) view.findViewById(R.id.name);
			name.setText(contact.getFullName());

			LinearLayout separator = (LinearLayout) view.findViewById(R.id.separator);
			TextView separatorText = (TextView) view.findViewById(R.id.separator_text);
			if (getPositionForSection(getSectionForPosition(position)) != position) {
				separator.setVisibility(View.GONE);
			} else {
				separator.setVisibility(View.VISIBLE);
				separatorText.setText(String.valueOf(contact.getFullName().charAt(0)));
			}
			
			ImageView icon = (ImageView) view.findViewById(R.id.contact_picture);
			if (contact.hasPhoto()) {
				LinphoneUtils.setImagePictureFromUri(getActivity(), icon, contact.getPhotoUri(), contact.getThumbnailUri());
			} else if (contact.getPhotoUri() != null) {
				icon.setImageURI(contact.getPhotoUri());
			} else {
				icon.setImageResource(R.drawable.avatar);
			}

			if (isEditMode) {
				delete.setVisibility(View.VISIBLE);
				delete.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
					@Override
					public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
						contactsList.setItemChecked(position, b);
						if(getNbItemsChecked() == getCount()){
							deselectAll.setVisibility(View.VISIBLE);
							selectAll.setVisibility(View.GONE);
							enabledDeleteButton(true);
						} else {
							if(getNbItemsChecked() == 0){
								deselectAll.setVisibility(View.GONE);
								selectAll.setVisibility(View.VISIBLE);
								enabledDeleteButton(false);
							} else {
								deselectAll.setVisibility(View.GONE);
								selectAll.setVisibility(View.VISIBLE);
								enabledDeleteButton(true);
							}
						}
					}
				});
				if (contactsList.isItemChecked(position)) {
					delete.setChecked(true);
				} else {
					delete.setChecked(false);
				}
			} else {
				delete.setVisibility(View.GONE);
			}
			
			ImageView friendStatus = (ImageView) view.findViewById(R.id.friendStatus);
			LinphoneFriend[] friends = LinphoneManager.getLc().getFriendList();
			if (!ContactsManager.getInstance().isContactPresenceDisabled() && friends != null) {
				friendStatus.setVisibility(View.VISIBLE);
				PresenceActivityType presenceActivity = friends[0].getPresenceModel().getActivity().getType();
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
		public Object[] getSections() {
			return sections;
		}

		@Override
		public int getPositionForSection(int sectionIndex) {
			return map.get(sections[sectionIndex]);
		}

		@Override
		public int getSectionForPosition(int position) {
			LinphoneContact contact = contacts.get(position);
			String letter = contact.getFullName().substring(0, 1);
			return sectionsList.indexOf(letter);
		}
	}
}
