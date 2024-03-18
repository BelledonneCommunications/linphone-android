package org.linphone.ui.main.settings.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.navArgs
import org.linphone.core.tools.Log
import org.linphone.databinding.SettingsContactsCarddavBinding
import org.linphone.ui.main.MainActivity
import org.linphone.ui.main.fragment.GenericFragment
import org.linphone.ui.main.settings.viewmodel.CardDavViewModel

@UiThread
class CardDavAddressBookConfigurationFragment : GenericFragment() {
    companion object {
        private const val TAG = "[CardDAV Address Book Configuration Fragment]"
    }

    private lateinit var binding: SettingsContactsCarddavBinding

    private lateinit var viewModel: CardDavViewModel

    private val args: CardDavAddressBookConfigurationFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = SettingsContactsCarddavBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[CardDavViewModel::class.java]

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel

        val friendListDisplayName = args.displayName
        if (friendListDisplayName != null) {
            Log.i("$TAG Found display name in arguments, loading friends list values")
            viewModel.loadFriendList(friendListDisplayName)
        } else {
            Log.i("$TAG No display name found in arguments, starting from scratch")
        }

        binding.setBackClickListener {
            goBack()
        }

        viewModel.cardDavOperationSuccessfulEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG CardDAV friend list operation was successful, going back")
                // TODO: show green toast
                goBack()
            }
        }

        viewModel.showErrorToastEvent.observe(viewLifecycleOwner) {
            it.consume { pair ->
                val icon = pair.first
                val message = pair.second
                (requireActivity() as MainActivity).showRedToast(message, icon)
            }
        }
    }
}
