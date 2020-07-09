package org.linphone.activities.main.chat.router

import androidx.core.os.bundleOf
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import org.linphone.R
import org.linphone.activities.main.chat.fragments.MasterChatRoomsFragment

/**
 * Class responsible for handling the routing operations for the Chat screen.
 */

internal fun MasterChatRoomsFragment.navigateToRoomCreation(shouldCreateGroup: Boolean) {
    val bundle = bundleOf("createGroup" to shouldCreateGroup)
    if (!resources.getBoolean(R.bool.isTablet)) {
        if (findNavController().currentDestination?.id == R.id.masterChatRoomsFragment) {
            findNavController().navigate(
                R.id.action_masterChatRoomsFragment_to_chatRoomCreationFragment,
                bundle
            )
        }
    } else {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.chat_nav_container) as NavHostFragment
        navHostFragment.navController.navigate(R.id.action_global_chatRoomCreationFragment, bundle)
    }
}

internal fun MasterChatRoomsFragment.navigateToChatRoomDetails(){
    if (!resources.getBoolean(R.bool.isTablet)) {
        if (findNavController().currentDestination?.id == R.id.masterChatRoomsFragment) {
            findNavController().navigate(R.id.action_masterChatRoomsFragment_to_detailChatRoomFragment)
        }
    } else {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.chat_nav_container) as NavHostFragment
        navHostFragment.navController.navigate(R.id.action_global_detailChatRoomFragment)
    }
}