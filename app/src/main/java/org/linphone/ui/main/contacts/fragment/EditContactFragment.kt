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
package org.linphone.ui.main.contacts.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.navGraphViewModels
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.ContactNewOrEditFragmentBinding
import org.linphone.ui.main.contacts.viewmodel.ContactNewOrEditViewModel
import org.linphone.ui.main.fragment.GenericFragment

class EditContactFragment : GenericFragment() {
    companion object {
        const val TAG = "[Contact Edit Fragment]"
    }

    private lateinit var binding: ContactNewOrEditFragmentBinding

    private val viewModel: ContactNewOrEditViewModel by navGraphViewModels(
        R.id.editContactFragment
    )

    private val args: EditContactFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ContactNewOrEditFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun goBack() {
        findNavController().popBackStack()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel

        postponeEnterTransition()

        val refKey = args.contactRefKey
        Log.i("[Contact Edit Fragment] Looking up for contact with ref key [$refKey]")
        viewModel.findFriendByRefKey(refKey)

        binding.setCancelClickListener {
            goBack()
        }

        viewModel.saveChangesEvent.observe(viewLifecycleOwner) {
            it.consume { ok ->
                if (ok) {
                    Log.i("$TAG Changes were applied, going back to details page")
                    goBack()
                } else {
                    Log.e("$TAG Changes couldn't be applied!")
                    // TODO FIXME : show error
                }
            }
        }

        viewModel.friendFoundEvent.observe(viewLifecycleOwner) {
            it.consume {
                startPostponedEnterTransition()
            }
        }
    }
}
