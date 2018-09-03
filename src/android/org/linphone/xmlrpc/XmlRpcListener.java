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
    void onError(String error);

    void onAccountCreated(String result);

    void onAccountExpireFetched(String result);

    void onAccountExpireUpdated(String result);

    void onAccountActivated(String result);

    void onAccountActivatedFetched(boolean isActivated);

    void onTrialAccountFetched(boolean isTrial);

    void onAccountFetched(boolean isExisting);

    void onAccountEmailChanged(String result);

    void onAccountPasswordChanged(String result);

    void onRecoverPasswordLinkSent(String result);

    void onActivateAccountLinkSent(String result);

    void onSignatureVerified(boolean success);

    void onUsernameSent(String result);

    void onRemoteProvisioningFilenameSent(String result);
}
