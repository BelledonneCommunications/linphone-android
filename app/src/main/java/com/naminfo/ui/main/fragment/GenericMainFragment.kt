/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
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
package com.naminfo.ui.main.fragment

import android.os.Bundle
import android.view.View
import androidx.annotation.UiThread
import androidx.lifecycle.ViewModelProvider
import org.linphone.core.tools.Log
import com.naminfo.ui.GenericFragment
import com.naminfo.ui.main.viewmodel.SharedMainViewModel

@UiThread
abstract class GenericMainFragment : GenericFragment() {
    companion object {
        private const val TAG = "[Generic Main Fragment]"
    }

    protected lateinit var sharedViewModel: SharedMainViewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sharedViewModel = requireActivity().run {
            ViewModelProvider(this)[SharedMainViewModel::class.java]
        }
    }

    protected fun getFragmentRealClassName(): String {
        return "[${this.javaClass.name}]"
    }

    protected open fun goBack(): Boolean {
        Log.d("$TAG ${getFragmentRealClassName()} Going back")
        try {
            Log.d("$TAG ${getFragmentRealClassName()} Calling onBackPressed on activity dispatcher")
            requireActivity().onBackPressedDispatcher.onBackPressed()
        } catch (ise: IllegalStateException) {
            Log.w("$TAG ${getFragmentRealClassName()} Can't go back: $ise")
            return false
        }
        return true
    }
}
