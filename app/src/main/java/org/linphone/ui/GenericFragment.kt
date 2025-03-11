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
package org.linphone.ui

import androidx.annotation.UiThread
import androidx.fragment.app.Fragment

@UiThread
abstract class GenericFragment : Fragment() {
    protected fun observeToastEvents(viewModel: GenericViewModel) {
        viewModel.showRedToastEvent.observe(viewLifecycleOwner) {
            it.consume { pair ->
                val message = getString(pair.first)
                val icon = pair.second
                (requireActivity() as GenericActivity).showRedToast(message, icon)
            }
        }

        viewModel.showFormattedRedToastEvent.observe(viewLifecycleOwner) {
            it.consume { pair ->
                val message = pair.first
                val icon = pair.second
                (requireActivity() as GenericActivity).showRedToast(message, icon)
            }
        }

        viewModel.showGreenToastEvent.observe(viewLifecycleOwner) {
            it.consume { pair ->
                val message = getString(pair.first)
                val icon = pair.second
                (requireActivity() as GenericActivity).showGreenToast(message, icon)
            }
        }

        viewModel.showFormattedGreenToastEvent.observe(viewLifecycleOwner) {
            it.consume { pair ->
                val message = pair.first
                val icon = pair.second
                (requireActivity() as GenericActivity).showGreenToast(message, icon)
            }
        }
    }
}
