package org.linphone.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import java.util.Locale
import org.linphone.authentication.AuthStateManager
import org.linphone.environment.DimensionsEnvironmentService
import org.linphone.services.BrandingService

class UrlHelper {
    companion object {

        @SuppressLint("CheckResult")
        fun openHelp(context: Context, path: String? = null) {
            BrandingService.getInstance(context).brand.subscribe { brand ->
                val deployment = DimensionsEnvironmentService.getInstance(context).getCurrentEnvironment()
                val user = AuthStateManager.getInstance(context).getUser()
                var lang = Locale.getDefault().toString().lowercase()

                // val validLocales : ArrayList<String> = arrayListOf("en-us", "en-gb")  //put this back in when we have localization on mobile docs
                val validLocales: ArrayList<String> = arrayListOf("en-us")
                if (!validLocales.contains(lang)) {
                    lang = "en-us"
                }

                val tenantBrandingDefinition = if (brand.isPresent()) brand.get() else null
                val brandingDocumentUri = if (tenantBrandingDefinition?.documentationRootUrl.isNullOrBlank()) deployment?.documentationUri else tenantBrandingDefinition?.documentationRootUrl

                val subPath = if (path == null) "" else "$path/"
                val tenantId = if (user == null) "" else "?tenantId=${user.tenantId}"

                if (!brandingDocumentUri.isNullOrBlank()) {
                    openBrowser(
                        context,
                        "$brandingDocumentUri/mobile/$lang/${subPath}$tenantId".lowercase()
                    )
                }
            }
        }

        fun openBrowser(context: Context, uri: String) {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = uri.toUri()
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            }
        }
    }
}
