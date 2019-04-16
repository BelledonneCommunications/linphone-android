package org.linphone.views;

/*
ContactSelectView.java
Copyright (C) 2017  Belledonne Communications, Grenoble, France

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
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.contacts.ContactAddress;

public class ContactSelectView extends View {
    private final TextView mContactName;
    private final ImageView mDeleteContact;

    public ContactSelectView(Context context) {
        super(context);

        LayoutInflater inflater =
                (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        View view = inflater.inflate(R.layout.contact_selected, null);

        mContactName = view.findViewById(R.id.sipUri);
        mDeleteContact = view.findViewById(R.id.contactChatDelete);
    }

    public void setContactName(ContactAddress ca) {
        if (ca.getContact() != null) {
            mContactName.setText(ca.getContact().getFirstName());
        } else {
            LinphoneManager.getLc()
                    .createFriendWithAddress(ca.getAddressAsDisplayableString())
                    .getName();
            mContactName.setText(ca.getAddressAsDisplayableString());
        }
    }

    public void setListener(OnClickListener listener) {
        mDeleteContact.setOnClickListener(listener);
    }
}
