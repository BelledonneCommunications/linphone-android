/*
EraseButton.java
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
package org.linphone.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.Button;

public class EraseButton extends Button implements AddressAware, OnClickListener, OnLongClickListener{

	private AddressText address;

	public EraseButton(Context context, AttributeSet attrs) {
		super(context, attrs);
		setOnClickListener(this);
		setOnLongClickListener(this);
	}

	public void onClick(View v) {
		if (address.getText().length() >0) {
			int lBegin = address.getSelectionStart();
			if (lBegin == -1) {
				lBegin = address.getEditableText().length()-1;
			}
			if (lBegin >0) {
				address.getEditableText().delete(lBegin-1,lBegin);
			}
		}
	}

	public boolean onLongClick(View v) {
		address.getEditableText().clear();
		return true;
	}

	public void setAddressWidget(AddressText view) {
		address = view;
	}

}
