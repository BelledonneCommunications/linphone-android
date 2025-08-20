package org.linphone.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import java.util.Locale
import org.linphone.authentication.AuthStateManager
import org.linphone.environment.DimensionsEnvironmentService
import org.linphone.services.BrandingService

class UrlHelper {
    companion object {

        suspend fun openHelp(context: Context, path: String? = null) {
            val user = AuthStateManager.getInstance(context).getUser()
            val deployment = DimensionsEnvironmentService.getInstance(context).getCurrentEnvironment()
            var lang = Locale.getDefault().toString().lowercase()

            val brand = BrandingService.getInstance(context).brand
                .blockingFirst()
                .getOrNull()

            if (brand == null) Log.w("User brand returned null.")
            else Log.d("Brand loaded: ${brand.brandName}. Docs URL: ${brand.documentationRootUrl}")

            val validLocales: ArrayList<String> = arrayListOf("en-us", "en-gb")
            if (!validLocales.contains(lang)) {
                lang = "en-us"
            }

            val brandingDocumentUri = if (brand?.documentationRootUrl.isNullOrBlank()) deployment?.documentationUri else brand?.documentationRootUrl
            val subPath = if (path == null) "" else "$path/"
            val tenantId = if (user == null) "" else "?tenantId=${user.tenantId}"

            if (!brandingDocumentUri.isNullOrBlank()) {
                openBrowser(
                    context,
                    "$brandingDocumentUri/mobile/$lang/${subPath}$tenantId"
                )
            }
        }

        fun openBrowser(context: Context, uri: String) {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = uri.toUri()
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            }
        }

        fun startTeamsCall(context: Context, email: String) {
            // Create the Teams deep link
            val teamsUri = Uri.parse("https://teams.microsoft.com/l/call/0/0?users=$email")

            // Create an intent to open the Teams app
            val intent = Intent(Intent.ACTION_VIEW, teamsUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK) // Start Teams in a new task
            }

            // Check if the Teams app is installed
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent) // Launch the Teams app
            } else {
                // Handle the case where Teams is not installed
                // Optionally redirect the user to the Play Store
                val playStoreUri = Uri.parse(
                    "https://play.google.com/store/apps/details?id=com.microsoft.teams"
                )
                val playStoreIntent = Intent(Intent.ACTION_VIEW, playStoreUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // Start Play Store in a new task
                }
                context.startActivity(playStoreIntent)
            }
        }
    }
}
