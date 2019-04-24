package org.linphone.purchase;

/*
InAppPurchaseListenerBase.java
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

import java.util.ArrayList;

public class InAppPurchaseListenerBase implements InAppPurchaseListener {
    @Override
    public void onServiceAvailableForQueries() {}

    @Override
    public void onAvailableItemsForPurchaseQueryFinished(ArrayList<Purchasable> items) {}

    @Override
    public void onPurchasedItemsQueryFinished(ArrayList<Purchasable> items) {}

    @Override
    public void onPurchasedItemConfirmationQueryFinished(boolean success) {}

    @Override
    public void onRecoverAccountSuccessful() {}

    @Override
    public void onActivateAccountSuccessful(boolean success) {}

    @Override
    public void onError(String error) {}
}
