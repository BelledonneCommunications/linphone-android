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
package org.linphone.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.utils.Event

open class GenericViewModel : ViewModel() {
    // Message res id, icon
    val showGreenToastEvent: MutableLiveData<Event<Pair<Int, Int>>> by lazy {
        MutableLiveData<Event<Pair<Int, Int>>>()
    }

    val showFormattedGreenToastEvent: MutableLiveData<Event<Pair<String, Int>>> by lazy {
        MutableLiveData<Event<Pair<String, Int>>>()
    }

    // Message res id, icon
    val showRedToastEvent: MutableLiveData<Event<Pair<Int, Int>>> by lazy {
        MutableLiveData<Event<Pair<Int, Int>>>()
    }

    val showFormattedRedToastEvent: MutableLiveData<Event<Pair<String, Int>>> by lazy {
        MutableLiveData<Event<Pair<String, Int>>>()
    }

    fun showGreenToast(@StringRes message: Int, @DrawableRes icon: Int) {
        showGreenToastEvent.postValue(Event(Pair(message, icon)))
    }

    fun showFormattedGreenToast(message: String, @DrawableRes icon: Int) {
        showFormattedGreenToastEvent.postValue(Event(Pair(message, icon)))
    }

    fun showRedToast(@StringRes message: Int, @DrawableRes icon: Int) {
        showRedToastEvent.postValue(Event(Pair(message, icon)))
    }

    fun showFormattedRedToast(message: String, @DrawableRes icon: Int) {
        showFormattedRedToastEvent.postValue(Event(Pair(message, icon)))
    }
}
