/*
AddressView.java
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

import org.linphone.LinphoneManager.AddressType;

import android.content.Context;
import android.net.Uri;
import android.util.AttributeSet;
import android.widget.EditText;

/**
 * @author Guillaume Beraudo
 *
 */
public class AddressText extends EditText implements AddressType {

	private String displayedName;
	private Uri pictureUri;
	public void setPictureUri(Uri uri) {pictureUri = uri;}
	public Uri getPictureUri() {return pictureUri;}


	public AddressText(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public void clearDisplayedName() {
		displayedName = "";
	}
	
	public String getDisplayedName() {
		return displayedName;
	}

	public void setContactAddress(String uri, String displayedName) {
		setText(uri);
		this.displayedName = displayedName;
	}

	public void setDisplayedName(String displayedName) {
		this.displayedName = displayedName;
	}

	@Override
	protected void onTextChanged(CharSequence text, int start, int before,
			int after) {
		clearDisplayedName();
		pictureUri=null;
		super.onTextChanged(text, start, before, after);
	}

}
