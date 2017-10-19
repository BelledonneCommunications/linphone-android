package org.linphone.xmlrpc;

/*
XmlRpcListenerBase.java
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

public class XmlRpcListenerBase implements XmlRpcListener {
	@Override
	public void onError(String error) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onAccountCreated(String result) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onAccountExpireFetched(String result) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onAccountActivated(String result) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onAccountActivatedFetched(boolean isActivated) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onTrialAccountFetched(boolean isTrial) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onAccountFetched(boolean isExisting) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onAccountEmailChanged(String result) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onAccountPasswordChanged(String result) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onRecoverPasswordLinkSent(String result) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onActivateAccountLinkSent(String result) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onAccountExpireUpdated(String result) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onSignatureVerified(boolean success) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onUsernameSent(String result) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onRemoteProvisioningFilenameSent(String result) {
		// TODO Auto-generated method stub

	}
}
