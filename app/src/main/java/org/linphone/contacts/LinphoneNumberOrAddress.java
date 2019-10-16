/*
 * Copyright (c) 2010-2019 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.contacts;

import java.io.Serializable;

public class LinphoneNumberOrAddress implements Serializable, Comparable<LinphoneNumberOrAddress> {
    private static final long serialVersionUID = -2301689469730072896L;

    private final boolean mIsSIPAddress;
    private String mValue, mOldValueForUpdatePurpose;
    private final String mNormalizedPhone;

    public LinphoneNumberOrAddress(String v, boolean isSIP) {
        mValue = v;
        mIsSIPAddress = isSIP;
        mOldValueForUpdatePurpose = null;
        mNormalizedPhone = null;
    }

    public LinphoneNumberOrAddress(String v, String normalizedV) {
        mValue = v;
        mNormalizedPhone = normalizedV != null ? normalizedV : v;
        mIsSIPAddress = false;
        mOldValueForUpdatePurpose = null;
    }

    public LinphoneNumberOrAddress(String v, boolean isSip, String old) {
        this(v, isSip);
        mOldValueForUpdatePurpose = old;
    }

    @Override
    public int compareTo(LinphoneNumberOrAddress noa) {
        if (mValue != null) {
            if (noa.isSIPAddress() && isSIPAddress()) {
                return mValue.compareTo(noa.getValue());
            } else if (!noa.isSIPAddress() && !isSIPAddress()) {
                return getNormalizedPhone().compareTo(noa.getNormalizedPhone());
            }
        }
        return -1;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj.getClass() != LinphoneNumberOrAddress.class) return false;
        LinphoneNumberOrAddress noa = (LinphoneNumberOrAddress) obj;
        return this.compareTo(noa) == 0;
    }

    public boolean isSIPAddress() {
        return mIsSIPAddress;
    }

    public String getOldValue() {
        return mOldValueForUpdatePurpose;
    }

    public void setOldValue(String v) {
        mOldValueForUpdatePurpose = v;
    }

    public String getValue() {
        return mValue;
    }

    public void setValue(String v) {
        mValue = v;
    }

    public String getNormalizedPhone() {
        return mNormalizedPhone != null ? mNormalizedPhone : mValue;
    }

    public String toString() {
        return (isSIPAddress() ? "sip:" : "tel:") + getNormalizedPhone();
    }
}
