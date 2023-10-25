package org.linphone.ui.main.settings.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.annotation.UiThread
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import java.util.Locale
import org.linphone.R
import org.linphone.core.TransportType
import org.linphone.core.tools.Log
import org.linphone.databinding.AccountSettingsFragmentBinding
import org.linphone.ui.main.fragment.GenericFragment
import org.linphone.ui.main.settings.viewmodel.AccountSettingsViewModel

@UiThread
class AccountSettingsFragment : GenericFragment() {
    companion object {
        private const val TAG = "[Account Settings Fragment]"
    }

    private lateinit var binding: AccountSettingsFragmentBinding

    private val args: AccountSettingsFragmentArgs by navArgs()

    private lateinit var viewModel: AccountSettingsViewModel

    private val dropdownListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            val transport = viewModel.availableTransports[position]
            val transportType = when {
                transport == TransportType.Tcp.name.uppercase(Locale.getDefault()) -> TransportType.Tcp
                transport == TransportType.Tls.name.uppercase(Locale.getDefault()) -> TransportType.Tls
                else -> TransportType.Udp
            }
            Log.i("$TAG Selected transport updated [$transport] -> [${transportType.name}]")
            viewModel.selectedTransport.value = transportType
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
        }
    }

    override fun goBack(): Boolean {
        return findNavController().popBackStack()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = AccountSettingsFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = requireActivity().run {
            ViewModelProvider(this)[AccountSettingsViewModel::class.java]
        }
        binding.viewModel = viewModel

        val identity = args.accountIdentity
        Log.i("$TAG Looking up for account with identity address [$identity]")
        viewModel.findAccountMatchingIdentity(identity)

        binding.setBackClickListener {
            goBack()
        }

        viewModel.accountFoundEvent.observe(viewLifecycleOwner) {
            it.consume { found ->
                if (found) {
                    (view.parent as? ViewGroup)?.doOnPreDraw {
                        startPostponedEnterTransition()

                        val adapter = ArrayAdapter(
                            requireContext(),
                            R.layout.drop_down_item,
                            viewModel.availableTransports
                        )
                        adapter.setDropDownViewResource(R.layout.generic_dropdown_cell)
                        val currentTransport = viewModel.selectedTransport.value?.name?.uppercase(
                            Locale.getDefault()
                        )
                        binding.transportSpinner.adapter = adapter
                        binding.transportSpinner.setSelection(
                            viewModel.availableTransports.indexOf(currentTransport)
                        )
                        binding.transportSpinner.onItemSelectedListener = dropdownListener
                    }
                } else {
                    Log.e(
                        "$TAG Failed to find an account matching this identity address [$identity]"
                    )
                    // TODO: show error
                    goBack()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()

        viewModel.saveChanges()
    }
}
