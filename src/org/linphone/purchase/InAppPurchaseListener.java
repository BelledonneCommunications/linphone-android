package org.linphone.purchase;
/*
InAppPurchaseListener.java
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

import java.util.ArrayList;

/**
 * @author Sylvain Berfini
 */
public interface InAppPurchaseListener {
	/**
	 * Callback called when the in-app purchase listener is connected and available for queries
	 */
	void onServiceAvailableForQueries();
	
	/**
	 * Callback called when the query for items available for purchase is done
	 * @param items the list of items that can be purchased (also contains the ones already bought)
	 */
	void onAvailableItemsForPurchaseQueryFinished(ArrayList<Purchasable> items);
	
	/**
	 * Callback called when the query for items bought by the user is done
	 * @param items the list of items already purchased by the user
	 */
	void onPurchasedItemsQueryFinished(ArrayList<Purchasable> items);
	
	/**
	 * Callback called when the purchase has been validated by our external server
	 * @param item the item the user just bought
	 */
	void onPurchasedItemConfirmationQueryFinished(Purchasable item);

	/**
	 * Callback called when the account has been recovered (or not)
	 * @param true if the recover has been successful, false otherwise
	 */
	void onRecoverAccountSuccessful(boolean success);
	
	/**
	 * Callback called when an error occurred.
	 * @param error the error that occurred
	 */
	void onError(String error);
}
