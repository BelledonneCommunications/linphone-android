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

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;


import android.os.Bundle;
import android.provider.Contacts;
import android.provider.Contacts.People;


public class ContactPickerActivity extends Activity {
    static final int PICK_CONTACT_REQUEST = 0;
    static final int PICK_PHONE_NUMBER_REQUEST = 1;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);    

    }

    @Override
	protected void onResume() {
		super.onResume();
        startActivityForResult(new Intent(Intent.ACTION_PICK, Contacts.Phones.CONTENT_URI)
		,
		PICK_CONTACT_REQUEST);

	}

	protected void onActivityResult(int requestCode, int resultCode,
            Intent data) {
        if (requestCode == PICK_CONTACT_REQUEST) {
            if (resultCode == RESULT_OK) {
            	String lColumns[] = new String[] { People.NAME, People.NUMBER };
 
                Cursor lCur = managedQuery(data.getData(), lColumns, // Which columns to return
                        null, // WHERE clause; which rows to return(all rows)
                        null, // WHERE clause selection arguments (none)
                        null // Order-by clause (ascending by name)

                );
                if (lCur.moveToFirst()) {
                    String lName = null;
                    String lPhoneNo = null;
                    // Get the field values
                    lName = lCur.getString(lCur.getColumnIndex(People.NAME));
                    lPhoneNo = lCur.getString(lCur.getColumnIndex(People.NUMBER));
                    DialerActivity.getDialer().setContactAddress(lPhoneNo, lName);
                }
            }
            	
            	LinphoneActivity.instance().getTabHost().setCurrentTabByTag(LinphoneActivity.DIALER_TAB);	
            }
        }
}
