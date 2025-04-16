package org.linphone.activities.main.presence.fragments

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.lifecycle.ViewModelProvider
import com.google.gson.GsonBuilder
import io.reactivex.rxjava3.disposables.Disposable
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.*
import org.linphone.activities.main.presence.viewmodels.PresenceEditorViewModel
import org.linphone.databinding.FragmentPresenceEditorBinding
import org.linphone.models.realtime.PresenceProfile
import org.linphone.services.PresenceProfileService
import org.linphone.services.UserService
import org.linphone.utils.Log

class PresenceEditorFragment : GenericFragment<FragmentPresenceEditorBinding>() {
    private lateinit var viewModel: PresenceEditorViewModel
    private val presenceProfileService = PresenceProfileService.getInstance(coreContext.context)

    private var presenceProfileSubscription: Disposable? = null
    private var userSubscription: Disposable? = null

    override fun getLayoutId(): Int = R.layout.fragment_presence_editor

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(this)[PresenceEditorViewModel::class.java]
        binding.viewModel = viewModel

        val userSvc = UserService.getInstance(requireContext())
        userSubscription = userSvc.user
            .subscribe(
                { u ->
                    Log.i("Userinfo: " + GsonBuilder().create().toJson(u))
                    viewModel.user.set(u)
                    viewModel.userImageUrl.set(u.profileImageUrl.replace("_36.png", "_128.png"))
                },
                { error -> Log.e(error) }
            )

        presenceProfileSubscription = presenceProfileService.presenceProfiles.subscribe(
            { response ->
                try {
                    updateSpinnerAdapter(response)
                } catch (e: Exception) {
                    Log.e("presenceProfileSubscription", e)
                }
            },
            { error -> Log.e(error) }
        )

        val presenceProfileSpinner: Spinner = requireView().findViewById(
            R.id.presenceProfileSpinner
        )

        binding.setApplyProfileClickListener {
            try {
                viewModel.applyPresence()
            } catch (e: Exception) {
                Log.e("setApplyProfileClickListener", e)
            }
        }
    }

    private fun updateSpinnerAdapter(presenceProfiles: List<PresenceProfile>) {
        val spinner: Spinner = requireView().findViewById(R.id.presenceProfileSpinner)
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            presenceProfiles
        )

        spinner.adapter = adapter

        // Observe the selectedItem LiveData
        viewModel.presenceProfile.observe(viewLifecycleOwner) { selectedItem ->
            val position = (spinner.adapter as ArrayAdapter<PresenceProfile>).getPosition(
                selectedItem
            )
            spinner.setSelection(position)
        }

        // Set the spinner selection listener to update the ViewModel
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }

            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                val selectedItem = parent.getItemAtPosition(position) as PresenceProfile
                viewModel.presenceProfile.value = selectedItem
                viewModel.applyPresence()
            }
        }
    }
}
