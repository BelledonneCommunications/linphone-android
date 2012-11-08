package org.linphone.compatibility;

import java.util.ArrayList;

import org.linphone.R;

import android.annotation.TargetApi;
import android.content.ContentProviderOperation;
import android.content.Context;
import android.provider.ContactsContract;

/*
ApiNinePlus.java
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
/**
 * @author Sylvain Berfini
 */
@TargetApi(9)
public class ApiNinePlus {
	
	public static void addSipAddressToContact(Context context, ArrayList<ContentProviderOperation> ops, String sipAddress) {
		ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)        
	        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
	        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE)
	        .withValue(ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS, sipAddress)
	        .withValue(ContactsContract.CommonDataKinds.SipAddress.TYPE,  ContactsContract.CommonDataKinds.SipAddress.TYPE_CUSTOM)
	        .withValue(ContactsContract.CommonDataKinds.SipAddress.LABEL, context.getString(R.string.addressbook_label))
	        .build()
	    );
	}
	
	public static void addSipAddressToContact(Context context, ArrayList<ContentProviderOperation> ops, String sipAddress, String rawContactID) {
		ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)         
		    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactID)       
	        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE)
	        .withValue(ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS, sipAddress)
	        .withValue(ContactsContract.CommonDataKinds.SipAddress.TYPE,  ContactsContract.CommonDataKinds.SipAddress.TYPE_CUSTOM)
	        .withValue(ContactsContract.CommonDataKinds.SipAddress.LABEL, context.getString(R.string.addressbook_label))
	        .build()
	    );
	}
	
	public static void updateSipAddressForContact(ArrayList<ContentProviderOperation> ops, String oldSipAddress, String newSipAddress, String contactID) {
		String select = ContactsContract.Data.CONTACT_ID + "=? AND " 
				+ ContactsContract.Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE +  "' AND " 
				+ ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS + "=?"; 
		String[] args = new String[] { String.valueOf(contactID), oldSipAddress };   
		
        ops.add(ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI) 
    		.withSelection(select, args) 
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS, newSipAddress)
            .build()
        );
	}
	
	public static void deleteSipAddressFromContact(ArrayList<ContentProviderOperation> ops, String oldSipAddress, String contactID) {
		String select = ContactsContract.Data.CONTACT_ID + "=? AND " 
				+ ContactsContract.Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE +  "' AND " 
				+ ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS + "=?"; 
		String[] args = new String[] { String.valueOf(contactID), oldSipAddress };   
		
        ops.add(ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI) 
    		.withSelection(select, args) 
            .build()
        );
	}
}
