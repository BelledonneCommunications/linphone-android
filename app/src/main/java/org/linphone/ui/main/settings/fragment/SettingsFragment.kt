package org.linphone.ui.main.settings.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.annotation.UiThread
import androidx.navigation.navGraphViewModels
import org.linphone.R
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

    private val viewModel: SettingsViewModel by navGraphViewModels(
        R.id.main_nav_graph
    )

    private val ringtoneListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            val path = viewModel.availableRingtonesPaths[position]
            val label = viewModel.availableRingtonesNames[position]
            Log.i("$TAG Selected ringtone is now [$label] ($path)")
            viewModel.setRingtone(path)
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
        super.onViewCreated(view, savedInstanceState)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel

        binding.setBackClickListener {
            goBack()
        }

        // Ringtone related
        val ringtonesAdapter = ArrayAdapter(
            requireContext(),
            R.layout.drop_down_item,
            viewModel.availableRingtonesNames
        )
        ringtonesAdapter.setDropDownViewResource(R.layout.assistant_transport_dropdown_cell)
        binding.deviceRingtoneSpinner.adapter = ringtonesAdapter

        viewModel.selectedRingtone.observe(viewLifecycleOwner) { ringtone ->
            binding.deviceRingtoneSpinner.setSelection(
                viewModel.availableRingtonesPaths.indexOf(
                    ringtone
                )
            )
        }

        binding.deviceRingtoneSpinner.onItemSelectedListener = ringtoneListener
    }
}
