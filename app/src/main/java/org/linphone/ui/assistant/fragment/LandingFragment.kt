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
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telephony.TelephonyManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.AssistantLandingFragmentBinding
import org.linphone.ui.GenericActivity
import org.linphone.ui.GenericFragment
import org.linphone.ui.assistant.model.AcceptConditionsAndPolicyDialogModel
import org.linphone.ui.assistant.viewmodel.AccountLoginViewModel
import org.linphone.utils.DialogUtils
import org.linphone.utils.PhoneNumberUtils
import androidx.core.net.toUri

@UiThread
class LandingFragment : GenericFragment() {
    companion object {
        private const val TAG = "[Landing Fragment]"
    }

    private lateinit var binding: AssistantLandingFragmentBinding

    private val viewModel: AccountLoginViewModel by navGraphViewModels(
        R.id.assistant_nav_graph
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = AssistantLandingFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel
        observeToastEvents(viewModel)

        binding.setBackClickListener {
            requireActivity().finish()
        }

        binding.setRegisterClickListener {
            if (viewModel.conditionsAndPrivacyPolicyAccepted) {
                goToRegisterFragment()
            } else {
                showAcceptConditionsAndPrivacyDialog(goToAccountCreate = true)
            }
        }

        binding.setQrCodeClickListener {
            if (findNavController().currentDestination?.id == R.id.landingFragment) {
                val action =
                    LandingFragmentDirections.actionLandingFragmentToQrCodeScannerFragment()
                findNavController().navigate(action)
            }
        }

        binding.setThirdPartySipAccountLoginClickListener {
            if (viewModel.conditionsAndPrivacyPolicyAccepted) {
                goToLoginThirdPartySipAccountFragment(false)
            } else {
                showAcceptConditionsAndPrivacyDialog(goToThirdPartySipAccountLogin = true)
            }
        }

        binding.setForgottenPasswordClickListener {
            if (findNavController().currentDestination?.id == R.id.landingFragment) {
                val action =
                    LandingFragmentDirections.actionLandingFragmentToRecoverAccountFragment()
                findNavController().navigate(action)
            }
        }

        viewModel.showPassword.observe(viewLifecycleOwner) {
            lifecycleScope.launch {
                delay(50)
                binding.password.setSelection(binding.password.text?.length ?: 0)
            }
        }

        viewModel.accountLoggedInEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Account successfully logged-in, leaving assistant")
                requireActivity().finish()
            }
        }

        viewModel.accountLoginErrorEvent.observe(viewLifecycleOwner) {
            it.consume { message ->
                (requireActivity() as GenericActivity).showRedToast(
                    message,
                    R.drawable.warning_circle
                )
            }
        }

        viewModel.skipLandingToThirdPartySipAccountEvent.observe(viewLifecycleOwner) {
            it.consume {
                goToLoginThirdPartySipAccountFragment(true)
            }
        }

        val telephonyManager = requireContext().getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val countryIso = telephonyManager.networkCountryIso
        coreContext.postOnCoreThread {
            val dialPlan = PhoneNumberUtils.getDeviceDialPlan(countryIso)
            if (dialPlan != null) {
                viewModel.internationalPrefix.postValue(dialPlan.countryCallingCode)
                viewModel.internationalPrefixIsoCountryCode.postValue(dialPlan.isoCountryCode)
            }
        }
    }

    private fun goToRegisterFragment() {
        if (findNavController().currentDestination?.id == R.id.landingFragment) {
            val action = LandingFragmentDirections.actionLandingFragmentToRegisterFragment()
            findNavController().navigate(action)
        }
    }

    private fun goToLoginThirdPartySipAccountFragment(skipWarning: Boolean) {
        if (findNavController().currentDestination?.id == R.id.landingFragment) {
            val action = if (skipWarning) {
                LandingFragmentDirections.actionLandingFragmentToThirdPartySipAccountLoginFragment()
            } else {
                LandingFragmentDirections.actionLandingFragmentToThirdPartySipAccountWarningFragment()
            }
            findNavController().navigate(action)
        }
    }

    private fun showAcceptConditionsAndPrivacyDialog(
        goToAccountCreate: Boolean = false,
        goToThirdPartySipAccountLogin: Boolean = false
    ) {
        val model = AcceptConditionsAndPolicyDialogModel()
        val dialog = DialogUtils.getAcceptConditionsAndPrivacyDialog(
            requireActivity(),
            model
        )

        model.dismissEvent.observe(viewLifecycleOwner) {
            it.consume {
                dialog.dismiss()
            }
        }

        model.conditionsAcceptedEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Conditions & Privacy policy have been accepted")
                coreContext.postOnCoreThread {
                    corePreferences.conditionsAndPrivacyPolicyAccepted = true
                }
                dialog.dismiss()

                if (goToAccountCreate) {
                    goToRegisterFragment()
                } else if (goToThirdPartySipAccountLogin) {
                    goToLoginThirdPartySipAccountFragment(false)
                }
            }
        }

        model.privacyPolicyClickedEvent.observe(viewLifecycleOwner) {
            it.consume {
                val url = getString(R.string.website_privacy_policy_url)
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
        }

        model.generalTermsClickedEvent.observe(viewLifecycleOwner) {
            it.consume {
                val url = getString(R.string.website_terms_and_conditions_url)
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
        }

        dialog.show()
    }
}
