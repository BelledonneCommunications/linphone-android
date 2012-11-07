package org.linphone;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.linphone.compatibility.Compatibility;
import org.linphone.ui.AvatarWithShadow;

import android.content.ContentProviderOperation;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.RawContacts;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TextView;

public class EditContactFragment extends Fragment {
	private View view;
	private TextView ok;
	private EditText displayName;
	private LayoutInflater inflater;
	private View deleteContact;
	
	private boolean isNewContact = true;
	private int contactID;
	private List<NewOrUpdatedNumberOrAddress> numbersAndAddresses;
	private ArrayList<ContentProviderOperation> ops;
	private int firstSipAddressIndex = -1;
	
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		this.inflater = inflater;
		
		Contact contact = null;
		if (getArguments() != null && getArguments().getSerializable("Contact") != null) {
			contact = (Contact) getArguments().getSerializable("Contact");
			isNewContact = false;
			contactID = Integer.parseInt(contact.getID());
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
		
		displayName = (EditText) view.findViewById(R.id.contactName);
		displayName.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				if (displayName.getText().length() > 0) {
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
			displayName.setText(contact.getName());
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
		
		return view;
	}
	
	private void initNumbersFields(final TableLayout controls, final Contact contact) {
		controls.removeAllViews();
		numbersAndAddresses = new ArrayList<NewOrUpdatedNumberOrAddress>();
		
		if (contact != null) {
			for (String numberOrAddress : contact.getNumerosOrAddresses()) {
				final boolean isSip = numberOrAddress.startsWith("sip:");
				if (isSip) {
					if (firstSipAddressIndex == -1) {
						firstSipAddressIndex = controls.getChildCount();
					}
					
					numberOrAddress = numberOrAddress.replace("sip:", "");
				}
				
				final NewOrUpdatedNumberOrAddress nounoa = new NewOrUpdatedNumberOrAddress(numberOrAddress, isSip);
				numbersAndAddresses.add(nounoa);
				
				final View view = inflater.inflate(R.layout.contact_edit_row, null);
				
				final EditText noa = (EditText) view.findViewById(R.id.numoraddr);
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
				
				ImageView delete = (ImageView) view.findViewById(R.id.delete);
				delete.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						nounoa.delete();
						numbersAndAddresses.remove(nounoa);
						view.setVisibility(View.GONE);
						if (isSip) // Add back the add SIP row
							addEmptyRowToAllowNewNumberOrAddress(controls, true);
					}
				});
				
				controls.addView(view);
			}
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
		addEmptyRowToAllowNewNumberOrAddress(controls, false);
		if (firstSipAddressIndex == -1) { // Only add new SIP address field if there is no SIP address yet
			firstSipAddressIndex = controls.getChildCount() - 2; // Update the value to alwas display phone numbers before SIP accounts
			addEmptyRowToAllowNewNumberOrAddress(controls, true);
		}
	}
	
	private void addEmptyRowToAllowNewNumberOrAddress(final TableLayout controls, final boolean isSip) {
		final View view = inflater.inflate(R.layout.contact_add_row, null);
		
		final NewOrUpdatedNumberOrAddress nounoa = new NewOrUpdatedNumberOrAddress(isSip);
		
		final EditText noa = (EditText) view.findViewById(R.id.numoraddr);
		noa.setHint(isSip ? getString(R.string.sip_address) : getString(R.string.phone_number));
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
				numbersAndAddresses.add(nounoa);
				add.setImageResource(R.drawable.list_delete);
				add.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						numbersAndAddresses.remove(nounoa);
						view.setVisibility(View.GONE);
						if (isSip) // Add back the add SIP row
							addEmptyRowToAllowNewNumberOrAddress(controls, true);
					}
				});
				if (!isSip) { // Only 1 SIP address / contact
					firstSipAddressIndex++;
					addEmptyRowToAllowNewNumberOrAddress(controls, false);
				}
			}
		});
		
		if (isSip) {
			controls.addView(view, controls.getChildCount());
			// Move to the bottom the remove contact button
			controls.removeView(deleteContact);
			controls.addView(deleteContact, controls.getChildCount());
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
        
        if (displayName.getText().length() > 0) {           
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)              
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, contactID)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, displayName.getText().toString())
                .build()
            );
        }
	}
	
	private void updateExistingContact() {
		if (displayName.getText().length() > 0) {        
			String select = ContactsContract.Data.CONTACT_ID + "=? AND " + ContactsContract.Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE +  "'" ; 
			String[] args = new String[] { String.valueOf(contactID) };   
			
            ops.add(ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI) 
        		.withSelection(select, args) 
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, displayName.getText().toString())
                .build()
            );
        }
	}
	
	private void deleteExistingContact() {
		if (displayName.getText().length() > 0) {        
			String select = ContactsContract.Data.CONTACT_ID + "=?"; 
			String[] args = new String[] { String.valueOf(contactID) };   
			
            ops.add(ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI) 
        		.withSelection(select, args) 
                .build()
            );
        }
        
        try {
            getActivity().getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
        } catch (Exception e) {
        	e.printStackTrace();
        }
	}
	
	private String findRawContactID(String contactID) {
		Cursor c = getActivity().getContentResolver().query(RawContacts.CONTENT_URI,
		          new String[]{RawContacts._ID},
		          RawContacts.CONTACT_ID + "=?",
		          new String[]{contactID}, null);
		if (c != null && c.moveToFirst()) {
			return c.getString(c.getColumnIndex(RawContacts._ID));
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
				String select = ContactsContract.Data.CONTACT_ID + "=? AND " 
						+ ContactsContract.Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE +  "' AND " 
						+ ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS + "=?"; 
				String[] args = new String[] { String.valueOf(contactID), oldNumberOrAddress };   
				
	            ops.add(ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI) 
	        		.withSelection(select, args) 
	                .build()
	            );
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
					ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)        
				        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
				        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE)
				        .withValue(ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS, newNumberOrAddress)
				        .withValue(ContactsContract.CommonDataKinds.SipAddress.TYPE,  ContactsContract.CommonDataKinds.SipAddress.TYPE_CUSTOM)
				        .withValue(ContactsContract.CommonDataKinds.SipAddress.LABEL, getString(R.string.addressbook_label))
				        .build()
				    );
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
					ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)         
					    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)       
				        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE)
				        .withValue(ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS, newNumberOrAddress)
				        .withValue(ContactsContract.CommonDataKinds.SipAddress.TYPE,  ContactsContract.CommonDataKinds.SipAddress.TYPE_CUSTOM)
				        .withValue(ContactsContract.CommonDataKinds.SipAddress.LABEL, getString(R.string.addressbook_label))
				        .build()
				    );
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
				String select = ContactsContract.Data.CONTACT_ID + "=? AND " 
						+ ContactsContract.Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE +  "' AND " 
						+ ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS + "=?"; 
				String[] args = new String[] { String.valueOf(contactID), oldNumberOrAddress };   
				
	            ops.add(ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI) 
	        		.withSelection(select, args) 
	                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE)
	                .withValue(ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS, newNumberOrAddress)
	                .build()
	            );
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
