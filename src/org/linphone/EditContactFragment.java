package org.linphone;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.linphone.compatibility.Compatibility;
import org.linphone.mediastream.Version;
import org.linphone.ui.AvatarWithShadow;

import android.content.ContentProviderOperation;
import android.content.Context;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.RawContacts;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TextView;

public class EditContactFragment extends Fragment {
	private View view;
	private TextView ok;
	private EditText firstName, lastName;
	private LayoutInflater inflater;
	private View deleteContact;
	
	private boolean isNewContact = true;
	private Contact contact;
	private int contactID;
	private List<NewOrUpdatedNumberOrAddress> numbersAndAddresses;
	private ArrayList<ContentProviderOperation> ops;
	private int firstSipAddressIndex = -1;
	private String newSipOrNumberToAdd;
	
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		this.inflater = inflater;
		
		contact = null;
		if (getArguments() != null) {
			if (getArguments().getSerializable("Contact") != null) {
				contact = (Contact) getArguments().getSerializable("Contact");
				isNewContact = false;
				contactID = Integer.parseInt(contact.getID());
				contact.refresh(getActivity().getContentResolver());
			}
			if (getArguments().getString("NewSipAdress") != null) {
				newSipOrNumberToAdd = getArguments().getString("NewSipAdress");
			}
		}
		
		view = inflater.inflate(R.layout.edit_contact, container, false);
		
		TextView cancel = (TextView) view.findViewById(R.id.cancel);
		cancel.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				getFragmentManager().popBackStackImmediate();
			}
		});
		
		ok = (TextView) view.findViewById(R.id.ok);
		ok.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (isNewContact) {
					if (getResources().getBoolean(R.bool.forbid_empty_new_contact_in_editor)) {
						boolean areAllFielsEmpty = true;
						for (NewOrUpdatedNumberOrAddress nounoa : numbersAndAddresses) {
							if (nounoa.newNumberOrAddress != null && !nounoa.newNumberOrAddress.equals("")) {
								areAllFielsEmpty = false;
								break;
							}
						}
						if (areAllFielsEmpty) {
							getFragmentManager().popBackStackImmediate();
							return;
						}
					}
					createNewContact();
				} else {
					updateExistingContact();
				}
				
				for (NewOrUpdatedNumberOrAddress numberOrAddress : numbersAndAddresses) {
					numberOrAddress.save();
				}

		        try {
		            getActivity().getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
					
			        if (isNewContact) {
			        	LinphoneActivity.instance().prepareContactsInBackground();
			        }
		        } catch (Exception e) {
		        	e.printStackTrace();
		        }
		        
				getFragmentManager().popBackStackImmediate();
			}
		});
		
		lastName = (EditText) view.findViewById(R.id.contactLastName);
		// Hack to display keyboard when touching focused edittext on Nexus One
		if (Version.sdkStrictlyBelow(Version.API11_HONEYCOMB_30)) {
			lastName.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					InputMethodManager imm = (InputMethodManager) LinphoneActivity.instance().getSystemService(Context.INPUT_METHOD_SERVICE);
					imm.toggleSoftInput(InputMethodManager.SHOW_FORCED,0);
				}
			});
		}
		lastName.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				if (lastName.getText().length() > 0 || firstName.getText().length() > 0) {
					ok.setEnabled(true);
				} else {
					ok.setEnabled(false);
				}
			}
			
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}
			
			@Override
			public void afterTextChanged(Editable s) {
			}
		});
		
		firstName = (EditText) view.findViewById(R.id.contactFirstName);
		firstName.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				if (firstName.getText().length() > 0 || lastName.getText().length() > 0) {
					ok.setEnabled(true);
				} else {
					ok.setEnabled(false);
				}
			}
			
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}
			
			@Override
			public void afterTextChanged(Editable s) {
			}
		});
		
		if (!isNewContact) {
			String fn = findContactFirstName(String.valueOf(contactID));
			String ln = findContactLastName(String.valueOf(contactID));
			if (fn != null || ln != null) {
				firstName.setText(fn);
				lastName.setText(ln);
			} else {
				lastName.setText(contact.getName());
				firstName.setText("");
			}
		}
		
		AvatarWithShadow contactPicture = (AvatarWithShadow) view.findViewById(R.id.contactPicture);
		if (contact != null && contact.getPhotoUri() != null) {
			InputStream input = Compatibility.getContactPictureInputStream(getActivity().getContentResolver(), contact.getID());
			contactPicture.setImageBitmap(BitmapFactory.decodeStream(input));
        } else {
        	contactPicture.setImageResource(R.drawable.unknown_small);
        }
		
		initNumbersFields((TableLayout) view.findViewById(R.id.controls), contact);
		
		ops = new ArrayList<ContentProviderOperation>();
		lastName.requestFocus();
		
		return view;
	}
	
	private void initNumbersFields(final TableLayout controls, final Contact contact) {
		controls.removeAllViews();
		numbersAndAddresses = new ArrayList<NewOrUpdatedNumberOrAddress>();
		
		if (contact != null) {
			for (String numberOrAddress : contact.getNumerosOrAddresses()) {
				View view = displayNumberOrAddress(controls, numberOrAddress);
				if (view != null)
					controls.addView(view);
			}
		}
		if (newSipOrNumberToAdd != null) {
			View view = displayNumberOrAddress(controls, newSipOrNumberToAdd, true);
			if (view != null)
				controls.addView(view);
		}
		
		if (!isNewContact) {
			deleteContact = inflater.inflate(R.layout.contact_delete_button, null);
			deleteContact.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					deleteExistingContact();
					LinphoneActivity.instance().removeContactFromLists(contact);
					LinphoneActivity.instance().displayContacts(false);
				}
			});
			controls.addView(deleteContact, controls.getChildCount());
		}

		// Add one for phone numbers, one for SIP address
		if (!getResources().getBoolean(R.bool.hide_phone_numbers_in_editor)) {
			addEmptyRowToAllowNewNumberOrAddress(controls, false);
		}
		
		if (!getResources().getBoolean(R.bool.hide_sip_addresses_in_editor)) {
			firstSipAddressIndex = controls.getChildCount() - 2; // Update the value to always display phone numbers before SIP accounts
			addEmptyRowToAllowNewNumberOrAddress(controls, true);
		}
	}
	
	private View displayNumberOrAddress(final TableLayout controls, String numberOrAddress) {
		return displayNumberOrAddress(controls, numberOrAddress, false);
	}
	
	private View displayNumberOrAddress(final TableLayout controls, String numberOrAddress, boolean forceAddNumber) {
		final boolean isSip = numberOrAddress.startsWith("sip:");
		if (isSip) {
			if (firstSipAddressIndex == -1) {
				firstSipAddressIndex = controls.getChildCount();
			}
			numberOrAddress = numberOrAddress.replace("sip:", "");
		}
		if ((getResources().getBoolean(R.bool.hide_phone_numbers_in_editor) && !isSip) || (getResources().getBoolean(R.bool.hide_sip_addresses_in_editor) && isSip)) {
			return null;
		}
		
		NewOrUpdatedNumberOrAddress tempNounoa;
		if (forceAddNumber) {
			tempNounoa = new NewOrUpdatedNumberOrAddress(isSip);
		} else {
			tempNounoa = new NewOrUpdatedNumberOrAddress(numberOrAddress, isSip);
		}
		final NewOrUpdatedNumberOrAddress nounoa = tempNounoa;
		numbersAndAddresses.add(nounoa);
		
		final View view = inflater.inflate(R.layout.contact_edit_row, null);
		
		final EditText noa = (EditText) view.findViewById(R.id.numoraddr);
		noa.setInputType(isSip ? InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS : InputType.TYPE_CLASS_PHONE);
		noa.setText(numberOrAddress);
		noa.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				nounoa.setNewNumberOrAddress(noa.getText().toString());
			}
			
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}
			
			@Override
			public void afterTextChanged(Editable s) {
			}
		});
		if (forceAddNumber) {
			nounoa.setNewNumberOrAddress(noa.getText().toString());
		}
		
		ImageView delete = (ImageView) view.findViewById(R.id.delete);
		delete.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				nounoa.delete();
				numbersAndAddresses.remove(nounoa);
				view.setVisibility(View.GONE);
			}
		});
		return view;
	}
	
	private void addEmptyRowToAllowNewNumberOrAddress(final TableLayout controls, final boolean isSip) {
		final View view = inflater.inflate(R.layout.contact_add_row, null);
		
		final NewOrUpdatedNumberOrAddress nounoa = new NewOrUpdatedNumberOrAddress(isSip);
		
		final EditText noa = (EditText) view.findViewById(R.id.numoraddr);
		numbersAndAddresses.add(nounoa);
		noa.setHint(isSip ? getString(R.string.sip_address) : getString(R.string.phone_number));
		noa.setInputType(isSip ? InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS : InputType.TYPE_CLASS_PHONE);
		noa.requestFocus();
		noa.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				nounoa.setNewNumberOrAddress(noa.getText().toString());
			}
			
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}
			
			@Override
			public void afterTextChanged(Editable s) {
			}
		});
		
		final ImageView add = (ImageView) view.findViewById(R.id.add);
		add.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// Add a line, and change add button for a delete button
				add.setImageResource(R.drawable.list_delete);
				ImageView delete = add;
				delete.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						nounoa.delete();
						numbersAndAddresses.remove(nounoa);
						view.setVisibility(View.GONE);
					}
				});
				if (!isSip) {
					firstSipAddressIndex++;
					addEmptyRowToAllowNewNumberOrAddress(controls, false);
				} else {
					addEmptyRowToAllowNewNumberOrAddress(controls, true);
				}
			}
		});
		
		if (isSip) {
			controls.addView(view, controls.getChildCount());
			if (deleteContact != null) {
				// Move to the bottom the remove contact button
				controls.removeView(deleteContact);
				controls.addView(deleteContact, controls.getChildCount());
			}
		} else {
			if (firstSipAddressIndex != -1) {
				controls.addView(view, firstSipAddressIndex);
			} else {
				controls.addView(view);
			}
		}
	}
	
	private void createNewContact() {
        contactID = 0;

        ops.add(ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
    		.withValue(RawContacts.ACCOUNT_TYPE, null)
    		.withValue(RawContacts.ACCOUNT_NAME, null).build());
        
        if (getDisplayName() != null) {           
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)              
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, contactID)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, firstName.getText().toString())
                .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, lastName.getText().toString())
                .build()
            );
        }
	}
	
	private void updateExistingContact() {
		if (getDisplayName() != null) {        
			String select = ContactsContract.Data.CONTACT_ID + "=? AND " + ContactsContract.Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE +  "'" ; 
			String[] args = new String[] { String.valueOf(contactID) };   
			
            ops.add(ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI) 
        		.withSelection(select, args) 
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, firstName.getText().toString())
                .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, lastName.getText().toString())
                .build()
            );
        }
	}
	
	private void deleteExistingContact() {
		String select = ContactsContract.Data.CONTACT_ID + "=?"; 
		String[] args = new String[] { String.valueOf(contactID) };   
		
        ops.add(ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI) 
    		.withSelection(select, args) 
            .build()
        );
        
        try {
            getActivity().getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
        } catch (Exception e) {
        	e.printStackTrace();
        }
	}
	
	private String getDisplayName() {
		String displayName = null;
		if (firstName.getText().length() > 0 && lastName.getText().length() > 0)
			displayName = firstName.getText().toString() + " " + lastName.getText().toString();
		else if (firstName.getText().length() > 0)
			displayName = firstName.getText().toString();
		else if (lastName.getText().length() > 0)
			displayName = lastName.getText().toString();
		return displayName;
	}
	
	private String findRawContactID(String contactID) {
		Cursor c = getActivity().getContentResolver().query(RawContacts.CONTENT_URI,
		          new String[]{RawContacts._ID},
		          RawContacts.CONTACT_ID + "=?",
		          new String[]{contactID}, null);
		if (c != null) {
			String result = null;
			if (c.moveToFirst()) {
				result = c.getString(c.getColumnIndex(RawContacts._ID));
			}
			c.close();
			return result;
		}
		return null;
	}
	
	private String findContactFirstName(String contactID) {
		Cursor c = getActivity().getContentResolver().query(ContactsContract.Data.CONTENT_URI,
		          new String[]{ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME},
		          ContactsContract.Data.CONTACT_ID + "=? AND " + ContactsContract.Data.MIMETYPE + "=?",
		          new String[]{contactID, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE}, null);
		if (c != null) {
			String result = null;
			if (c.moveToFirst()) {
				result = c.getString(c.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME));
			}
			c.close();
			return result;
		}
		return null;
	}
	
	private String findContactLastName(String contactID) {
		Cursor c = getActivity().getContentResolver().query(ContactsContract.Data.CONTENT_URI,
		          new String[]{ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME},
		          ContactsContract.Data.CONTACT_ID + "=? AND " + ContactsContract.Data.MIMETYPE + "=?",
		          new String[]{contactID, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE}, null);
		if (c != null) {
			String result = null;
			if (c.moveToFirst()) {
				result = c.getString(c.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME));
			}
			c.close();
			return result;
		}
		return null;
	}
	
	class NewOrUpdatedNumberOrAddress {
		private String oldNumberOrAddress;
		private String newNumberOrAddress;
		private boolean isSipAddress;
		
		public NewOrUpdatedNumberOrAddress() {
			oldNumberOrAddress = null;
			newNumberOrAddress = null;
			isSipAddress = false;
		}
		
		public NewOrUpdatedNumberOrAddress(boolean isSip) {
			oldNumberOrAddress = null;
			newNumberOrAddress = null;
			isSipAddress = isSip;
		}
		
		public NewOrUpdatedNumberOrAddress(String old, boolean isSip) {
			oldNumberOrAddress = old;
			newNumberOrAddress = null;
			isSipAddress = isSip;
		}
		
		public void setNewNumberOrAddress(String newN) {
			newNumberOrAddress = newN;
		}
		
		public void save() {
			if (newNumberOrAddress == null || newNumberOrAddress.equals(oldNumberOrAddress))
				return;

			if (oldNumberOrAddress == null) {
				// New number to add
				addNewNumber();
			} else {
				// Old number to update
				updateNumber();
			}
		}
		
		public void delete() {
			if (isSipAddress) {
				Compatibility.deleteSipAddressFromContact(ops, oldNumberOrAddress, String.valueOf(contactID));
			} else {
				String select = ContactsContract.Data.CONTACT_ID + "=? AND " 
						+ ContactsContract.Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE +  "' AND " 
						+ ContactsContract.CommonDataKinds.Phone.NUMBER + "=?"; 
				String[] args = new String[] { String.valueOf(contactID), oldNumberOrAddress };   
				
	            ops.add(ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI) 
	        		.withSelection(select, args) 
	                .build()
	            );
			}
		}
		
		private void addNewNumber() {
			if (isNewContact) {
				if (isSipAddress) {
					if (newNumberOrAddress.startsWith("sip:"))
						newNumberOrAddress = newNumberOrAddress.substring(4);
					Compatibility.addSipAddressToContact(getActivity(), ops, newNumberOrAddress);
				} else {
					ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)        
				        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
				        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
				        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, newNumberOrAddress)
				        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE,  ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM)
				        .withValue(ContactsContract.CommonDataKinds.Phone.LABEL, getString(R.string.addressbook_label))
				        .build()
				    );
				}
			} else {
				String rawContactId = findRawContactID(String.valueOf(contactID));
				
				if (isSipAddress) {
					if (newNumberOrAddress.startsWith("sip:"))
						newNumberOrAddress = newNumberOrAddress.substring(4);
					Compatibility.addSipAddressToContact(getActivity(), ops, newNumberOrAddress, rawContactId);
				} else {
					ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)         
					    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)       
				        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
				        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, newNumberOrAddress)
				        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE,  ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM)
				        .withValue(ContactsContract.CommonDataKinds.Phone.LABEL, getString(R.string.addressbook_label))
				        .build()
				    );
				}
			}
		}
		
		private void updateNumber() {
			if (isSipAddress) {
				if (newNumberOrAddress.startsWith("sip:"))
					newNumberOrAddress = newNumberOrAddress.substring(4);
				Compatibility.updateSipAddressForContact(ops, oldNumberOrAddress, newNumberOrAddress, String.valueOf(contactID));
			} else {
				String select = ContactsContract.Data.CONTACT_ID + "=? AND " 
						+ ContactsContract.Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE +  "' AND " 
						+ ContactsContract.CommonDataKinds.Phone.NUMBER + "=?"; 
				String[] args = new String[] { String.valueOf(contactID), oldNumberOrAddress };   
				
	            ops.add(ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI) 
	        		.withSelection(select, args) 
	                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
	                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, newNumberOrAddress)
	                .build()
	            );
			}
		}
	}
}
