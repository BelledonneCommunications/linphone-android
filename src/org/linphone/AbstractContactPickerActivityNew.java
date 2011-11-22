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

import java.util.Collections;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
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
public abstract class AbstractContactPickerActivityNew extends Activity implements FilterQueryProvider {

    private ListView mContactList;
    protected EditText mcontactFilter;

    private SimpleCursorAdapter adapter;
    protected boolean useNativePicker;

    protected final String col_display_name = "display_name";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);    

		useNativePicker = getResources().getBoolean(R.bool.use_android_contact_picker);

		if (!useNativePicker) {
			setContentView(R.layout.contact_picker);
			createCustomPicker();
		}

		onNewIntent(getIntent());
	}

	

	protected void createCustomPicker() {
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
        String[] from = new String[] {col_display_name};
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



	protected void choosePhoneNumberAndDial(final CharSequence contactName, final String id) {
		List<String> phones = extractPhones(id);
		phones.addAll(extractSipNumbers(id));
		
		switch (phones.size()) {
		case 0:
			String msg = String.format(getString(R.string.no_phone_numbers), contactName);
			Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
			((ContactPicked) getParent()).goToDialer();
			break;
		case 1:
			returnSelectedValues(phones.get(0), contactName.toString(), getPhotoUri(id));
			break;
		default:
			AlertDialog.Builder builder = new AlertDialog.Builder(this);

			final ArrayAdapter<String> pAdapter = new ArrayAdapter<String>(this,
					android.R.layout.simple_dropdown_item_1line, phones);

			builder.setTitle(String.format(getString(R.string.title_numbers_dialog),contactName));
			builder.setAdapter(pAdapter, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
					returnSelectedValues(pAdapter.getItem(which), contactName.toString(),getPhotoUri(id));
				}
			});
			builder.setCancelable(true);
			builder.setNeutralButton("cancel", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
				}
			});
			builder.create().show();
		}
	}

	private void returnSelectedValues(String number, String name, Uri photo) {
/*		if (getCallingActivity() != null) {
			setResult(RESULT_OK, new Intent()
				.putExtra(Intent.EXTRA_PHONE_NUMBER, number)
				.putExtra(EXTRA_CONTACT_NAME, name));
			finish();
		}*/

		((ContactPicked) getParent()).setAddressAndGoToDialer(number, name, photo);
	}

	
	protected abstract List<String> extractPhones(String id);
	protected abstract Uri getPhotoUri(String id);

	// Hook
	protected List<String> extractSipNumbers(String id) {
		return Collections.emptyList();
	}

	public abstract Cursor runQuery(CharSequence constraint);

}
