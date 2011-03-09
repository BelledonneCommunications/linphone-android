/*
ContactPickerActivity.java
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
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FilterQueryProvider;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;


/**
 * Activity for retrieving a phone number / SIP address to call.
 * <br />
 *
 * The cinematic is:
 * <ul>
 * <li>Select contact (either through native or custom way)</li>
 * <li>Select phone number or SIP address
 * <li>Back to dialer</li>
 * </ul>
 * 
 * @author Guillaume Beraudo
 *
 */
public class ContactPickerActivityNew extends Activity implements FilterQueryProvider {

    private ListView mContactList;
    private EditText mcontactFilter;

    private SimpleCursorAdapter adapter;
    private boolean useNativePicker;


	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);    

		useNativePicker = getResources().getBoolean(R.bool.use_android_contact_picker);

		if (!useNativePicker) {
			setContentView(R.layout.contact_picker);
			createPicker();
		}
	}

	

	private void createPicker() {
        mContactList = (ListView) findViewById(R.id.contactList);

        mcontactFilter = (EditText) findViewById(R.id.contactFilter);
        mcontactFilter.addTextChangedListener(new TextWatcher() {
			public void onTextChanged(CharSequence s, int start, int b, int c) {}
			public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
			
			public void afterTextChanged(Editable s) {
				adapter.runQueryOnBackgroundThread(s);
				adapter.getFilter().filter(s.toString());
			}
		});


        // Populate the contact list
        String[] from = new String[] {ContactsContract.Data.DISPLAY_NAME};
        int[] to = new int[] {android.R.id.text1};
        int layout = android.R.layout.simple_list_item_1;
        adapter = new SimpleCursorAdapter(this, layout, runQuery(null), from, to);
        adapter.setFilterQueryProvider(this);
        mContactList.setAdapter(adapter);
        
        mContactList.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				final CharSequence contactName = ((TextView) view.findViewById(android.R.id.text1)).getText();
				choosePhoneNumberAndDial(contactName, String.valueOf(id));
			}
        });
	}



	private void choosePhoneNumberAndDial(final CharSequence contactName, final String id) {
		List<String> phones = extractPhones(id);
		phones.addAll(extractSipNumbers(id));
		
		switch (phones.size()) {
		case 0:
			String msg = String.format(getString(R.string.no_phone_numbers), contactName);
			Toast.makeText(ContactPickerActivityNew.this, msg, Toast.LENGTH_LONG).show();
			break;
		case 1:
			returnSelectedValues(phones.get(0), contactName.toString());
			break;
		default:
			AlertDialog.Builder builder = new AlertDialog.Builder(ContactPickerActivityNew.this);

			final ArrayAdapter<String> pAdapter = new ArrayAdapter<String>(ContactPickerActivityNew.this,
					android.R.layout.simple_dropdown_item_1line, phones);

			builder.setTitle(String.format(getString(R.string.title_numbers_dialog),contactName));
			builder.setAdapter(pAdapter, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					returnSelectedValues(pAdapter.getItem(which), contactName.toString());
				}
			});
			builder.setCancelable(true);
			builder.setNeutralButton("cancel", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
				}
			});
			AlertDialog dialog = builder.create();
			dialog.show();
		}
	}

	private void returnSelectedValues(String number, String name) {
/*		if (getCallingActivity() != null) {
			setResult(RESULT_OK, new Intent()
				.putExtra(Intent.EXTRA_PHONE_NUMBER, number)
				.putExtra(EXTRA_CONTACT_NAME, name));
			finish();
		}*/

		LinphoneActivity.setAddressAndGoToDialer(number, name);
	}

	
	private List<String> extractPhones(String id) {
		List<String> list = new ArrayList<String>();
		Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
		String selection = ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?";
		String[] selArgs = new String[] {id};
 		Cursor c = this.getContentResolver().query(uri,	null, selection, selArgs, null);

 		int nbId = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);

 		while (c.moveToNext()) {
 			list.add(c.getString(nbId)); 
 		}
 
 		c.close();
 		
 		return list;
	}
	
	private List<String> extractSipNumbers(String id) {
		List<String> list = new ArrayList<String>();
		Uri uri = ContactsContract.Data.CONTENT_URI;
		String selection = new StringBuilder()
			.append(ContactsContract.Data.CONTACT_ID).append(" =  ? AND ")
			.append(ContactsContract.Data.MIMETYPE).append(" = ? ")
			.append(" AND lower(")
				.append(ContactsContract.CommonDataKinds.Im.CUSTOM_PROTOCOL)
				.append(") = 'sip'").toString();
		String[] selArgs = new String[] {id, ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE};
 		Cursor c = this.getContentResolver().query(uri,	null, selection, selArgs, null);

 		int nbId = c.getColumnIndex(ContactsContract.CommonDataKinds.Im.DATA);
 		
 		while (c.moveToNext()) {
 			list.add("sip:" + c.getString(nbId)); 
 		}
 
 		c.close();
 		
 		return list;
	}

	@Override
	protected void onResume() {
		super.onResume();
		
		if (useNativePicker) {
			Uri uri = ContactsContract.Contacts.CONTENT_URI;
			//ContactsContract.CommonDataKinds.Phone.CONTENT_URI
			startActivityForResult(new Intent(Intent.ACTION_PICK, uri),	0);
		}
	}

	protected void onActivityResult(int reqCode, int resultCode, Intent intent) {
		// If using native picker
		if (reqCode == 0) {
			if (resultCode == RESULT_OK) {
		        String id = intent.getData().getLastPathSegment();
		        String contactName = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
		        if (contactName == null)
		        	contactName = retrieveContactName(id);
				choosePhoneNumberAndDial(contactName, id);
			}
		}
		
		LinphoneActivity.instance().getTabHost().setCurrentTabByTag(LinphoneActivity.DIALER_TAB);
	}


	private String retrieveContactName(String id) {
		Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
		String selection = ContactsContract.CommonDataKinds.Phone._ID + " = ?";
		String[] selArgs = new String[] {id};
 		Cursor c = this.getContentResolver().query(uri,	null, selection, selArgs, null);

 		String name = "";
 		if (c.moveToFirst()) {
 			name =  c.getString(c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)); 
 		}
 		c.close();
 
 		return name;
	}



	public Cursor runQuery(CharSequence constraint) {
		// Run query
        Uri uri = ContactsContract.Contacts.CONTENT_URI;
        String[] projection = new String[] {
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME
        };
        String selection =
        	ContactsContract.Contacts.IN_VISIBLE_GROUP + " = '1' and "
        	+ ContactsContract.Contacts.HAS_PHONE_NUMBER + " = '1'";
        String[] selectionArgs = null;
        if (!TextUtils.isEmpty(constraint)) {
        	// FIXME absolutely unsecure
        	selection += " and " + ContactsContract.Contacts.DISPLAY_NAME + " ilike '%"+mcontactFilter.getText()+"%'";
        }
        
        String sortOrder = ContactsContract.Contacts.DISPLAY_NAME + " COLLATE LOCALIZED ASC";

        return managedQuery(uri, projection, selection, selectionArgs, sortOrder);
	}

	
}
