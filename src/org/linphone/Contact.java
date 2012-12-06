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
	private List<String> numerosOrAddresses;
	private LinphoneFriend friend;
	
	public Contact(String id, String name) {
		super();
		this.id = id;
		this.name = name;
		this.photoUri = null;
	}
	
	public Contact(String id, String name, Uri photo) {
		super();
		this.id = id;
		this.name = name;
		this.photoUri = photo;
		this.photo = null;
	}
	
	public Contact(String id, String name, Uri photo, Bitmap picture) {
		super();
		this.id = id;
		this.name = name;
		this.photoUri = photo;
		this.photo = picture;
	}
	
	public void setFriend(LinphoneFriend friend) {
		this.friend = friend;
	}
	
	public LinphoneFriend getFriend() {
		return friend;
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

	public List<String> getNumerosOrAddresses() {
		if (numerosOrAddresses == null)
			numerosOrAddresses = new ArrayList<String>();
		return numerosOrAddresses;
	}
	
	public void refresh(ContentResolver cr) {
		this.numerosOrAddresses = Compatibility.extractContactNumbersAndAddresses(id, cr);
		this.name = Compatibility.refreshContactName(cr, id);
	}
}
