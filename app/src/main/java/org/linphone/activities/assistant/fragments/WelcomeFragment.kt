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
package org.linphone.activities.assistant.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import androidx.lifecycle.ViewModelProvider
import java.util.regex.Pattern
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.*
import org.linphone.activities.assistant.viewmodels.WelcomeViewModel
import org.linphone.activities.navigateToAccountLogin
import org.linphone.activities.navigateToEmailAccountCreation
import org.linphone.activities.navigateToRemoteProvisioning
import org.linphone.core.tools.Log
import org.linphone.databinding.AssistantWelcomeFragmentBinding

class WelcomeFragment : GenericFragment<AssistantWelcomeFragmentBinding>() {
    private lateinit var viewModel: WelcomeViewModel

    override fun getLayoutId(): Int = R.layout.assistant_welcome_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(this)[WelcomeViewModel::class.java]
        binding.viewModel = viewModel

        binding.setCreateAccountClickListener {
            if (resources.getBoolean(R.bool.isTablet)) {
                navigateToEmailAccountCreation()
            } else {
                navigateToPhoneAccountCreation()
            }
        }

        binding.setAccountLoginClickListener {
            navigateToAccountLogin()
        }

        binding.setGenericAccountLoginClickListener {
            navigateToGenericLoginWarning()
        }

        binding.setRemoteProvisioningClickListener {
            navigateToRemoteProvisioning()
        }

        viewModel.termsAndPrivacyAccepted.observe(
            viewLifecycleOwner
        ) {
            if (it) corePreferences.readAndAgreeTermsAndPrivacy = true
        }

        setUpTermsAndPrivacyLinks()
    }

    private fun setUpTermsAndPrivacyLinks() {
        val terms = getString(R.string.assistant_general_terms)
        val privacy = getString(R.string.assistant_privacy_policy)

        val label = getString(
            R.string.assistant_read_and_agree_terms,
            terms,
            privacy
        )
        val spannable = SpannableString(label)

        val termsMatcher = Pattern.compile(terms).matcher(label)
        if (termsMatcher.find()) {
            val clickableSpan: ClickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    val browserIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(getString(R.string.assistant_general_terms_link))
                    )
                    try {
                        startActivity(browserIntent)
                    } catch (e: Exception) {
                        Log.e("[Welcome] Can't start activity: $e")
                    }
                }
            }
            spannable.setSpan(clickableSpan, termsMatcher.start(0), termsMatcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val policyMatcher = Pattern.compile(privacy).matcher(label)
        if (policyMatcher.find()) {
            val clickableSpan: ClickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    val browserIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(getString(R.string.assistant_privacy_policy_link))
                    )
                    try {
                        startActivity(browserIntent)
                    } catch (e: Exception) {
                        Log.e("[Welcome] Can't start activity: $e")
                    }
                }
            }
            spannable.setSpan(clickableSpan, policyMatcher.start(0), policyMatcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        binding.termsAndPrivacy.text = spannable
        binding.termsAndPrivacy.movementMethod = LinkMovementMethod.getInstance()
    }
}
