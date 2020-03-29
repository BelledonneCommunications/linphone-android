/*
 * Copyright (c) 2010-2020 Belledonne Communications SARL.
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
package org.linphone.utils

import androidx.databinding.ViewDataBinding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.recyclerview.widget.RecyclerView

/**
 * This class allows us to use ViewHolder as lifecycle owner so items in lists can refresh when
 * a live data value changes without having to notify the adapter that the item as changed.
 */
abstract class LifecycleViewHolder(viewBinding: ViewDataBinding) : RecyclerView.ViewHolder(viewBinding.root), LifecycleOwner {
    private val lifecycleRegistry = LifecycleRegistry(this) // TODO FIXME leak ?

    init {
        lifecycleRegistry.currentState = Lifecycle.State.INITIALIZED
    }

    override fun getLifecycle(): Lifecycle {
        return lifecycleRegistry
    }

    fun attach() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    fun detach() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }
}
