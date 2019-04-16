package org.linphone.purchase;

import java.sql.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

/*
Purchasable.java
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

public class Purchasable {
    private final String mId;
    private String mTitle;
    private String mDescription;
    private String mPrice;
    private String mPurchasePayload, mPurchasePayloadSignature;
    private String mUserData;

    public Purchasable(String id) {
        this.mId = id;
    }

    public String getId() {
        return mId;
    }

    public String getTitle() {
        return mTitle;
    }

    public Purchasable setTitle(String title) {
        this.mTitle = title;
        return this;
    }

    public String getDescription() {
        return mDescription;
    }

    public Purchasable setDescription(String description) {
        this.mDescription = description;
        return this;
    }

    public String getPrice() {
        return mPrice;
    }

    public Purchasable setPrice(String price) {
        this.mPrice = price;
        return this;
    }

    public String getExpireDate() {
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault());
        Date date = new Date(0);
        return dateFormat.format(date);
    }

    public Purchasable setPayloadAndSignature(String payload, String signature) {
        this.mPurchasePayload = payload;
        this.mPurchasePayloadSignature = signature;
        return this;
    }

    public String getPayload() {
        return this.mPurchasePayload;
    }

    public String getPayloadSignature() {
        return this.mPurchasePayloadSignature;
    }

    public String getUserData() {
        return this.mUserData;
    }

    public Purchasable setUserData(String data) {
        this.mUserData = data;
        return this;
    }
}
