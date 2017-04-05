package org.linphone.purchase;

import java.sql.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

/*
Purchasable.java
Copyright (C) 2015  Belledonne Communications, Grenoble, France

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

/**
 * @author Sylvain Berfini
 */
public class Purchasable {
	private String id, title, description, price;
	private long expire;
	private String purchasePayload, purchasePayloadSignature; 
	private String userData;
	
	public Purchasable(String id) {
		this.id = id;
	}

	public String getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public Purchasable setTitle(String title) {
		this.title = title;
		return this;
	}

	public String getDescription() {
		return description;
	}

	public Purchasable setDescription(String description) {
		this.description = description;
		return this;
	}

	public String getPrice() {
		return price;
	}

	public Purchasable setPrice(String price) {
		this.price = price;
		return this;
	}

	public long getExpire() {
		return expire;
	}
	
	public String getExpireDate() {
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault());
		Date date = new Date(expire);
		return dateFormat.format(date);
	}

	public Purchasable setExpire(long expire) {
		this.expire = expire;
		return this;
	}
	
	public Purchasable setPayloadAndSignature(String payload, String signature) {
		this.purchasePayload = payload;
		this.purchasePayloadSignature = signature;
		return this;
	}
	
	public String getPayload() {
		return this.purchasePayload;
	}
	
	public String getPayloadSignature() {
		return this.purchasePayloadSignature;
	}
	
	public Purchasable setUserData(String data) {
		this.userData = data;
		return this;
	}
	
	public String getUserData() {
		return this.userData;
	}
}
