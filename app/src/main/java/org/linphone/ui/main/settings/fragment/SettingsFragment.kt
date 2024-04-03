package org.linphone.ui.main.settings.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.annotation.UiThread
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import org.linphone.R
import org.linphone.compatibility.Compatibility
import org.linphone.core.tools.Log
import org.linphone.databinding.SettingsFragmentBinding
import org.linphone.ui.main.fragment.GenericFragment
import org.linphone.ui.main.settings.viewmodel.SettingsViewModel

@UiThread
class SettingsFragment : GenericFragment() {
    companion object {
        private const val TAG = "[Settings Fragment]"
    }

    private lateinit var binding: SettingsFragmentBinding

    private lateinit var viewModel: SettingsViewModel

    private val layoutListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            val label = viewModel.availableLayoutsNames[position]
            val value = viewModel.availableLayoutsValues[position]
            Log.i("$TAG Selected meeting default layout is now [$label] ($value)")
            viewModel.setDefaultLayout(value)
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
        }
    }

    private val themeListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            val label = viewModel.availableThemesNames[position]
            val value = viewModel.availableThemesValues[position]
            Log.i("$TAG Selected theme is now [$label] ($value)")
            viewModel.setTheme(value)

            when (value) {
                0 -> Compatibility.forceLightMode(requireContext())
                1 -> Compatibility.forceDarkMode(requireContext())
                else -> Compatibility.setAutoLightDarkMode(requireContext())
            }
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = SettingsFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        postponeEnterTransition()
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[SettingsViewModel::class.java]

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel

        binding.setBackClickListener {
            goBack()
        }

        viewModel.addLdapServerEvent.observe(viewLifecycleOwner) {
            it.consume {
                val action = SettingsFragmentDirections.actionSettingsFragmentToLdapServerConfigurationFragment(
                    null
                )
                findNavController().navigate(action)
            }
        }

        viewModel.editLdapServerEvent.observe(viewLifecycleOwner) {
            it.consume { name ->
                val action = SettingsFragmentDirections.actionSettingsFragmentToLdapServerConfigurationFragment(
                    name
                )
                findNavController().navigate(action)
            }
        }

        viewModel.addCardDavServerEvent.observe(viewLifecycleOwner) {
            it.consume {
                val action = SettingsFragmentDirections.actionSettingsFragmentToCardDavAddressBookConfigurationFragment(
                    null
                )
                findNavController().navigate(action)
            }
        }

        viewModel.editCardDavServerEvent.observe(viewLifecycleOwner) {
            it.consume { name ->
                val action = SettingsFragmentDirections.actionSettingsFragmentToCardDavAddressBookConfigurationFragment(
                    name
                )
                findNavController().navigate(action)
            }
        }

        // Meeting default layout related
        val layoutAdapter = ArrayAdapter(
            requireContext(),
            R.layout.drop_down_item,
            viewModel.availableLayoutsNames
        )
        layoutAdapter.setDropDownViewResource(R.layout.generic_dropdown_cell)
        binding.meetingsSettings.layoutSpinner.adapter = layoutAdapter

        viewModel.defaultLayout.observe(viewLifecycleOwner) { layout ->
            binding.meetingsSettings.layoutSpinner.setSelection(
                viewModel.availableLayoutsValues.indexOf(layout)
            )
        }
        binding.meetingsSettings.layoutSpinner.onItemSelectedListener = layoutListener

        // Light/Dark theme related
        val themeAdapter = ArrayAdapter(
            requireContext(),
            R.layout.drop_down_item,
            viewModel.availableThemesNames
        )
        themeAdapter.setDropDownViewResource(R.layout.generic_dropdown_cell)
        binding.userInterfaceSettings.themeSpinner.adapter = themeAdapter

        viewModel.theme.observe(viewLifecycleOwner) { theme ->
            binding.userInterfaceSettings.themeSpinner.setSelection(
                viewModel.availableThemesValues.indexOf(theme)
            )
        }

        binding.userInterfaceSettings.themeSpinner.onItemSelectedListener = themeListener

        startPostponedEnterTransition()
    }

    override fun onResume() {
        super.onResume()

        viewModel.reloadLdapServers()
        viewModel.reloadConfiguredCardDavServers()
    }
}
