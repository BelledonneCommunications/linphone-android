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
package org.linphone.ui.contacts.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import androidx.navigation.fragment.findNavController
import org.linphone.databinding.NewContactFragmentBinding
import org.linphone.ui.fragment.GenericFragment

class NewContactFragment : GenericFragment() {
    private lateinit var binding: NewContactFragmentBinding

    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation? {
        /*if (findNavController().currentDestination?.id == R.id.contactFragment) {
            // Holds fragment in place while created contact fragment slides over it
            return AnimationUtils.loadAnimation(activity, R.anim.hold)
        }*/
        return super.onCreateAnimation(transit, enter, nextAnim)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = NewContactFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        // postponeEnterTransition()

        binding.setCancelClickListener {
            findNavController().popBackStack()
        }

        /*(view.parent as? ViewGroup)?.doOnPreDraw {
            startPostponedEnterTransition()
        }*/
    }
}
