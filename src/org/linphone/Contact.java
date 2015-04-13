package org.linphone;
/*
Contact.java
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
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.linphone.compatibility.Compatibility;
import org.linphone.core.LinphoneFriend;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.net.Uri;

/**
 * @author Sylvain Berfini
 */
public class Contact implements Serializable {
	private static final long serialVersionUID = 3790717505065723499L;
	
	private String id;
	private String name;
	private transient Uri photoUri;
	private transient Bitmap photo;
	private List<String> numbersOrAddresses;
	private boolean hasFriends;
	
	public Contact(String id, String name) {
		super();
		this.id = id;
		this.name = name;
		this.photoUri = null;
		this.hasFriends = false;
	}
	
	public Contact(String id, String name, Uri photo) {
		super();
		this.id = id;
		this.name = name;
		this.photoUri = photo;
		this.photo = null;
		this.hasFriends = false;
	}
	
	public Contact(String id, String name, Uri photo, Bitmap picture) {
		super();
		this.id = id;
		this.name = name;
		this.photoUri = photo;
		this.photo = picture;
		this.hasFriends = false;
	}


	public boolean hasFriends() {
		return hasFriends;
	}
	
	public String getID() {
		return id;
	}
	
	public String getName() {
		return name;
	}

	public Uri getPhotoUri() {
		return photoUri;
	}
	
	public Bitmap getPhoto() {
		return photo;
	}

	public List<String> getNumbersOrAddresses() {
		if (numbersOrAddresses == null)
			numbersOrAddresses = new ArrayList<String>();
		return numbersOrAddresses;
	}
	
	public void refresh(ContentResolver cr) {
		this.numbersOrAddresses = Compatibility.extractContactNumbersAndAddresses(id, cr);
		for(LinphoneFriend friend : LinphoneManager.getLc().getFriendList()) {
			if (friend.getRefKey().equals(id)) {
				hasFriends = true;
				this.numbersOrAddresses.add(friend.getAddress().asStringUriOnly());
			}
		}
		this.name = Compatibility.refreshContactName(cr, id);
	}
}
