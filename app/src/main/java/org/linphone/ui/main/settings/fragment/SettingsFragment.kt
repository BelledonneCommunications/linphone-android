package org.linphone.ui.main.settings.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.navigation.navGraphViewModels
import org.linphone.R
import org.linphone.databinding.SettingsFragmentBinding
import org.linphone.ui.main.fragment.GenericFragment
import org.linphone.ui.main.settings.viewmodel.SettingsViewModel

@UiThread
class SettingsFragment : GenericFragment() {
    private lateinit var binding: SettingsFragmentBinding

    private val viewModel: SettingsViewModel by navGraphViewModels(
        R.id.main_nav_graph
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = SettingsFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel

        binding.setBackClickListener {
            goBack()
        }
    }
}
