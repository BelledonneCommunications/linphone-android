package org.linphone.ui.main.settings.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.UiThread
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.navGraphViewModels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.AccountProfileFragmentBinding
import org.linphone.ui.main.contacts.fragment.EditContactFragment
import org.linphone.ui.main.fragment.GenericFragment
import org.linphone.ui.main.settings.viewmodel.AccountProfileViewModel
import org.linphone.utils.FileUtils

@UiThread
class AccountProfileFragment : GenericFragment() {
    companion object {
        const val TAG = "[Account Profile Fragment]"
    }

    private lateinit var binding: AccountProfileFragmentBinding

    private val viewModel: AccountProfileViewModel by navGraphViewModels(
        R.id.accountProfileFragment
    )

    private val args: AccountProfileFragmentArgs by navArgs()

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val identity = "john" // TODO FIXME: use account identity
            val localFileName = FileUtils.getFileStoragePath(
                "$identity.jpg", // TODO FIXME: use correct file extension
                isImage = true,
                overrideExisting = true
            )
            Log.i("$TAG Picture picked [$uri], will be stored as [${localFileName.absolutePath}]")

            lifecycleScope.launch {
                if (FileUtils.copyFile(uri, localFileName)) {
                    withContext(Dispatchers.Main) {
                        viewModel.setImage(localFileName)
                    }
                } else {
                    Log.e(
                        "${EditContactFragment.TAG} Failed to copy file from [$uri] to [${localFileName.absolutePath}]"
                    )
                }
            }
        } else {
            Log.w("${EditContactFragment.TAG} No picture picked")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = AccountProfileFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun goBack() {
        findNavController().popBackStack()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        postponeEnterTransition()

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel

        val identity = args.accountIdentity
        Log.i("$TAG Looking up for account with identity address [$identity]")
        viewModel.findAccountMatchingIdentity(identity)

        binding.setBackClickListener {
            goBack()
        }

        binding.setPickImageClickListener {
            pickImage()
        }

        viewModel.accountFoundEvent.observe(viewLifecycleOwner) {
            it.consume { found ->
                if (found) {
                    (view.parent as? ViewGroup)?.doOnPreDraw {
                        startPostponedEnterTransition()
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

        Log.i("$TAG Leaving account profile, saving changes")
        viewModel.saveDisplayNameChanges()
    }

    private fun pickImage() {
        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
}
