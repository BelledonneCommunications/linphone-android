package org.linphone.sync;

/*
Authenticator.java
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
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
*/

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.*;
import android.content.Context;
import android.os.Bundle;

public class Authenticator extends AbstractAccountAuthenticator {

	public Authenticator(Context context) {
		super(context);
	}

	@Override
	public Bundle editProperties(
			AccountAuthenticatorResponse r, String s) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Bundle addAccount(
			AccountAuthenticatorResponse r,
			String s,
			String s2,
			String[] strings,
			Bundle bundle) throws NetworkErrorException {
		return null;
	}

	@Override
	public Bundle confirmCredentials(
			AccountAuthenticatorResponse r,
			Account account,
			Bundle bundle) throws NetworkErrorException {
		return null;
	}

	@Override
	public Bundle getAuthToken(
			AccountAuthenticatorResponse r,
			Account account,
			String s,
			Bundle bundle) throws NetworkErrorException {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getAuthTokenLabel(String s) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Bundle updateCredentials(
			AccountAuthenticatorResponse r,
			Account account,
			String s, Bundle bundle) throws NetworkErrorException {
		throw new UnsupportedOperationException();
	}

	@Override
	public Bundle hasFeatures(
			AccountAuthenticatorResponse r,
			Account account, String[] strings) throws NetworkErrorException {
		throw new UnsupportedOperationException();
	}
}