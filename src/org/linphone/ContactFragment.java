package org.linphone;
/*
ContactFragment.java
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
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TextView;

/**
 * @author Sylvain Berfini
 */
public class ContactFragment extends Fragment {
	private Contact contact;
	private OnClickListener dialListener, chatListener;
	
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		contact = (Contact) getArguments().getSerializable("Contact");
		View view = inflater.inflate(R.layout.contact, container, false);
		
		ImageView contactPicture = (ImageView) view.findViewById(R.id.contactPicture);
		if (contact.getPhoto() != null) {
        	LinphoneUtils.setImagePictureFromUri(view.getContext(), contactPicture, contact.getPhotoUri(), R.drawable.unknown_small);
        }
		
		chatListener = getChatListener();
		dialListener = getDialListener();
		
		TextView contactName = (TextView) view.findViewById(R.id.contactName);
		contactName.setText(contact.getName());	
		
		TableLayout controls = (TableLayout) view.findViewById(R.id.controls);
		
		for (String numberOrAddress : contact.getNumerosOrAddresses()) {
			View v = inflater.inflate(R.layout.contact_control_row, null);
			
			((TextView) v.findViewById(R.id.numeroOrAddress)).setText(numberOrAddress);
			
			v.findViewById(R.id.dial).setOnClickListener(dialListener);
			v.findViewById(R.id.dial).setTag(numberOrAddress);

			if (LinphoneUtils.isSipAddress(numberOrAddress)) {
				v.findViewById(R.id.chat).setOnClickListener(chatListener);
				v.findViewById(R.id.chat).setTag(numberOrAddress);
			} else {
				v.findViewById(R.id.chat).setVisibility(View.INVISIBLE);
			}
			
			controls.addView(v);
		}
		
		return view;
	}
	
	@Override
	public void onResume() {
		super.onResume();
		if (LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().selectMenu(FragmentsAvailable.CONTACT);
		}
	}

	public OnClickListener getDialListener() {
		return new OnClickListener() {
			@Override
			public void onClick(View v) {
				LinphoneActivity.instance().setAddresGoToDialerAndCall(v.getTag().toString(), contact.getName(), contact.getPhotoUri());
			}
		};
	}
	
	public OnClickListener getChatListener() {
		return new OnClickListener() {
			@Override
			public void onClick(View v) {
				LinphoneActivity.instance().displayChat(v.getTag().toString());
			}
		};
	}
}
