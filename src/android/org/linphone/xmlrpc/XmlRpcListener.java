package org.linphone.xmlrpc;

/*
XmlRpcListener.java
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

public interface XmlRpcListener {
	public void onError(String error);
	public void onAccountCreated(String result);
	public void onAccountExpireFetched(String result);
	public void onAccountExpireUpdated(String result);
	public void onAccountActivated(String result);
	public void onAccountActivatedFetched(boolean isActivated);
	public void onTrialAccountFetched(boolean isTrial);
	public void onAccountFetched(boolean isExisting);
	public void onAccountEmailChanged(String result);
	public void onAccountPasswordChanged(String result);
	public void onRecoverPasswordLinkSent(String result);
	public void onActivateAccountLinkSent(String result);
	public void onSignatureVerified(boolean success);
	public void onUsernameSent(String result);
	public void onRemoteProvisioningFilenameSent(String result);
}
