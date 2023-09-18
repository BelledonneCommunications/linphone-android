package org.linphone.ui.main.help.fragment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.HelpFragmentBinding
import org.linphone.ui.main.MainActivity
import org.linphone.ui.main.fragment.GenericFragment
import org.linphone.ui.main.help.viewmodel.HelpViewModel

@UiThread
class HelpFragment : GenericFragment() {
    companion object {
        private const val TAG = "[Help Fragment]"
    }

    private lateinit var binding: HelpFragmentBinding

    val viewModel: HelpViewModel by navGraphViewModels(
        R.id.main_nav_graph
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = HelpFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel

        binding.setBackClickListener {
            goBack()
        }

        binding.setDebugClickListener {
            val action = HelpFragmentDirections.actionHelpFragmentToDebugFragment()
            findNavController().navigate(action)
        }

        binding.setPrivacyPolicyClickListener {
            val url = getString(R.string.website_privacy_policy_url)
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(browserIntent)
            } catch (ise: IllegalStateException) {
                Log.e(
                    "$TAG Can't start ACTION_VIEW intent for URL [$url], IllegalStateException: $ise"
                )
            }
        }

        binding.setLicensesClickListener {
            val url = getString(R.string.website_open_source_licences_usage_url)
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(browserIntent)
            } catch (ise: IllegalStateException) {
                Log.e(
                    "$TAG Can't start ACTION_VIEW intent for URL [$url], IllegalStateException: $ise"
                )
            }
        }

        binding.setTranslateClickListener {
            val url = getString(R.string.website_translate_weblate_url)
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(browserIntent)
            } catch (ise: IllegalStateException) {
                Log.e(
                    "$TAG Can't start ACTION_VIEW intent for URL [$url], IllegalStateException: $ise"
                )
            }
        }

        viewModel.newVersionAvailableEvent.observe(viewLifecycleOwner) {
            it.consume { version ->
                (requireActivity() as MainActivity).showGreenToast(
                    getString(R.string.help_update_available_toast_message, version),
                    R.drawable.info
                )
            }
        }

        viewModel.versionUpToDateEvent.observe(viewLifecycleOwner) {
            it.consume {
                (requireActivity() as MainActivity).showGreenToast(
                    getString(R.string.help_version_up_to_date_toast_message),
                    R.drawable.info
                )
            }
        }

        viewModel.errorEvent.observe(viewLifecycleOwner) {
            it.consume {
                (requireActivity() as MainActivity).showRedToast(
                    getString(R.string.help_error_checking_version_toast_message),
                    R.drawable.warning_circle
                )
            }
        }
    }
}
