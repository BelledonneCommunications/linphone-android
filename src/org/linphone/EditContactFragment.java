package org.linphone;

import java.util.ArrayList;

import android.content.ContentProviderOperation;
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
import android.widget.TextView;

public class EditContactFragment extends Fragment {
	private View view;
	private TextView ok;
	private EditText displayName;
	
	private boolean isNewContact = true;
	private int contactID;
	
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
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
		
		return view;
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
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, displayName.getText().toString()).build()
            );
        }
        
        try {
            getActivity().getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
        } catch (Exception e) {
        	
        }
	}
	
	private void updateExistingContact() {
		ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
		
		if (displayName.getText().length() > 0) {        
			String selectPhone = ContactsContract.Data.CONTACT_ID + "=? AND " + ContactsContract.Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE +  "'" ; 
			String[] phoneArgs = new String[] { String.valueOf(contactID) };   
			
            ops.add(ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI) 
        		.withSelection(selectPhone, phoneArgs) 
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, displayName.getText().toString()).build()
            );
        }
        
        try {
            getActivity().getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
        } catch (Exception e) {
        	
        }
	}
}
