/*
NumpadView.java
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

import java.util.ArrayList;
import java.util.Collection;

import org.linphone.R;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

/**
 * @author Guillaume Beraudo
 *
 */
public class Numpad extends LinearLayout implements AddressAware {

	public Numpad(Context context, AttributeSet attrs) {
		super(context, attrs);
		LayoutInflater.from(context).inflate(R.layout.numpad, this);
		setLongClickable(true);
	}

	public void setAddressWidget(AddressText address) {
		for (AddressAware v : retrieveChildren(this)) {
			v.setAddressWidget(address);
		}
	}


	private Collection<AddressAware> retrieveChildren(ViewGroup viewGroup) {
		final Collection<AddressAware> views = new ArrayList<AddressAware>();

		for (int i = 0; i < viewGroup.getChildCount(); i++) {
			View v = viewGroup.getChildAt(i);
			if (v instanceof ViewGroup) {
				views.addAll(retrieveChildren((ViewGroup) v));
			} else {
				if (v instanceof AddressAware)
					views.add((AddressAware) v);
			}
		}

		return views;
	}

}
