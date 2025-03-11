/*
 * Copyright (c) 2010-2024 Belledonne Communications SARL.
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
package org.linphone.telecom.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.contacts.AvatarGenerator
import org.linphone.contacts.getAvatarBitmap
import org.linphone.core.MagicSearch
import org.linphone.core.tools.Log
import org.linphone.utils.AppUtils
import org.linphone.utils.LinphoneUtils

class AndroidAutoScreen(context: CarContext) : Screen(context) {
    companion object {
        private const val TAG = "[Android Auto Screen]"
    }

    private val favoritesList = arrayListOf<GridItem>()

    private var loading = true

    init {
        Log.i(
            "$TAG Creating favorites contacts list template for host with API level [${carContext.carAppApiLevel}]"
        )
        coreContext.postOnCoreThread { core ->
            val magicSearch = core.createMagicSearch()
            val results = magicSearch.getContactsList(
                "",
                LinphoneUtils.getDefaultAccount()?.params?.domain.orEmpty(),
                MagicSearch.Source.FavoriteFriends.toInt(),
                MagicSearch.Aggregation.Friend
            )
            val favorites = arrayListOf<GridItem>()
            for (result in results) {
                val builder = GridItem.Builder()
                val friend = result.friend ?: continue

                builder.setTitle(friend.name)
                Log.i("$TAG Creating car icon for friend [${friend.name}]")
                try {
                    val bitmap = friend.getAvatarBitmap(true) ?: AvatarGenerator(
                        coreContext.context
                    ).setInitials(
                        AppUtils.getInitials(friend.name.orEmpty())
                    ).buildBitmap(useTransparentBackground = false)
                    builder.setImage(
                        CarIcon.Builder(IconCompat.createWithBitmap(bitmap))
                            .build(),
                        GridItem.IMAGE_TYPE_LARGE
                    )
                } catch (e: Exception) {
                    Log.e("$TAG Exception trying to create CarIcon: $e")
                }

                builder.setOnClickListener {
                    val address = friend.address ?: friend.addresses.firstOrNull()
                    if (address != null) {
                        Log.i("$TAG Starting audio call to [${address.asStringUriOnly()}]")
                        coreContext.startAudioCall(address)
                    }
                }
                try {
                    val item = builder.build()
                    favorites.add(item)
                } catch (e: Exception) {
                    Log.e("$TAG Failed to build grid item: $e")
                }
            }
            loading = false
            Log.i("$TAG Processed [${favorites.size}] favorites")

            coreContext.postOnMainThread {
                favoritesList.addAll(favorites)
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        Log.i("$TAG onGetTemplate called, favorites are [${if (loading) "loading" else "loaded"}]")

        val listBuilder = ItemList.Builder()
        listBuilder.setNoItemsMessage(
            carContext.getString(R.string.car_favorites_contacts_list_empty)
        )
        for (favorite in favoritesList) {
            listBuilder.addItem(favorite)
        }
        val list = listBuilder.build()

        val header = Header.Builder()
            .setTitle(carContext.getString(R.string.car_favorites_contacts_title))
            .setStartHeaderAction(Action.APP_ICON)
            .build()

        val gridBuilder = GridTemplate.Builder()
        gridBuilder.setHeader(header)
        gridBuilder.setLoading(loading)
        if (!loading) {
            Log.i("$TAG Added [${favoritesList.size}] favorites items to grid")
            gridBuilder.setSingleList(list)
        }
        return gridBuilder.build()
    }
}
