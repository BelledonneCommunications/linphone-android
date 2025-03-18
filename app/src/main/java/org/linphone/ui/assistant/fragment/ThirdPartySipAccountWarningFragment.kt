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
package org.linphone.ui.assistant.fragment

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.navigation.fragment.findNavController
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.AssistantThirdPartySipAccountWarningFragmentBinding
import org.linphone.ui.GenericFragment
import androidx.core.net.toUri

@UiThread
class ThirdPartySipAccountWarningFragment : GenericFragment() {
    companion object {
        private const val TAG = "[Third Party SIP Account Warning Fragment]"
    }

    private lateinit var binding: AssistantThirdPartySipAccountWarningFragmentBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = AssistantThirdPartySipAccountWarningFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        binding.setBackClickListener {
            goBack()
        }

        binding.setContactClickListener {
            val url = getString(R.string.website_contact_url)
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, url.toUri())
                startActivity(browserIntent)
            } catch (ise: IllegalStateException) {
                Log.e(
                    "$TAG Can't start ACTION_VIEW intent for URL [$url], IllegalStateException: $ise"
                )
            } catch (anfe: ActivityNotFoundException) {
                Log.e(
                    "$TAG Can't start ACTION_VIEW intent for URL [$url], ActivityNotFoundException: $anfe"
                )
            } catch (e: Exception) {
                Log.e(
                    "$TAG Can't start ACTION_VIEW intent for URL [$url]: $e"
                )
            }
        }

        binding.setCreateAccountClickListener {
            if (findNavController().currentDestination?.id == R.id.thirdPartySipAccountWarningFragment) {
                val action =
                    ThirdPartySipAccountWarningFragmentDirections.actionThirdPartySipAccountWarningFragmentToRegisterFragment()
                findNavController().navigate(action)
            }
        }

        binding.setLoginClickListener {
            if (findNavController().currentDestination?.id == R.id.thirdPartySipAccountWarningFragment) {
                val action =
                    ThirdPartySipAccountWarningFragmentDirections.actionThirdPartySipAccountWarningFragmentToThirdPartySipAccountLoginFragment()
                findNavController().navigate(action)
            }
        }
    }

    private fun goBack() {
        findNavController().popBackStack()
    }
}
