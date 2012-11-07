package org.linphone;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.linphone.compatibility.Compatibility;
import org.linphone.ui.AvatarWithShadow;

import android.content.ContentProviderOperation;
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
	
	private boolean isNewContact = true;
	private int contactID;
	private List<NewOrUpdatedNumberOrAddress> numbersAndAddresses;
	
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
		
		return view;
	}
	
	private void initNumbersFields(TableLayout controls, final Contact contact) {
		controls.removeAllViews();
		numbersAndAddresses = new ArrayList<NewOrUpdatedNumberOrAddress>();
		
		if (contact != null) {
			for (String numberOrAddress : contact.getNumerosOrAddresses()) {
				boolean isSip = numberOrAddress.startsWith("sip:");
				if (isSip) {
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
						view.setVisibility(View.GONE);
					}
				});
				
				controls.addView(view);
			}
			
			if (!isNewContact) {
				View deleteContact = inflater.inflate(R.layout.contact_delete_button, null);
				deleteContact.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						deleteExistingContact();
						LinphoneActivity.instance().removeContactFromLists(contact);
						LinphoneActivity.instance().displayContacts(false);
					}
				});
				controls.addView(deleteContact);
			}
			
		}
	}
	
	private void createNewContact() {
		ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
        contactID = ops.size();

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
        
        try {
            getActivity().getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
        } catch (Exception e) {
        	e.printStackTrace();
        }
	}
	
	private void updateExistingContact() {
		ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
		
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
        
        try {
            getActivity().getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
        } catch (Exception e) {
        	e.printStackTrace();
        }
	}
	
	private void deleteExistingContact() {
		ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
		
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
	
	class NewOrUpdatedNumberOrAddress {
		private String oldNumberOrAddress;
		private String newNumberOrAddress;
		private boolean isSipAddress;
		
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

			ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
			
			if (oldNumberOrAddress == null) {
				// New number to add
				addNewNumber(ops);
			} else {
				// Old number to update
				updateNumber(ops);
			}
			
			try {
	            getActivity().getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
	        } catch (Exception e) {
	        	e.printStackTrace();
	        }
		}
		
		public void delete() {
			//TODO
		}
		
		private void addNewNumber(ArrayList<ContentProviderOperation> ops) {
			if (isSipAddress) {
				ops.add(ContentProviderOperation.
			        newInsert(ContactsContract.Data.CONTENT_URI)
			        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, contactID)
			        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE)
			        .withValue(ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS, newNumberOrAddress)
			        .withValue(ContactsContract.CommonDataKinds.SipAddress.TYPE,  ContactsContract.CommonDataKinds.SipAddress.TYPE_CUSTOM)
			        .withValue(ContactsContract.CommonDataKinds.SipAddress.LABEL, "Linphone")
			        .build()
			    );
			} else {
				ops.add(ContentProviderOperation.
			        newInsert(ContactsContract.Data.CONTENT_URI)
			        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, contactID)
			        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
			        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, newNumberOrAddress)
			        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE,  ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM)
			        .withValue(ContactsContract.CommonDataKinds.Phone.LABEL, "Linphone")
			        .build()
			    );
			}
		}
		
		private void updateNumber(ArrayList<ContentProviderOperation> ops) {
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
