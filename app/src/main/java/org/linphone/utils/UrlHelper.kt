package org.linphone.utils

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import java.util.Locale
import org.linphone.authentication.AuthStateManager
import org.linphone.environment.DimensionsEnvironmentService
import org.linphone.services.BrandingService

class UrlHelper {
    companion object {
        fun openHelp(context: Context, path: String? = null) {
            val deployment = DimensionsEnvironmentService.getInstance(context).getCurrentEnvironment()
            val user = AuthStateManager.getInstance(context).getUser()
            val lang = Locale.getDefault().toString()

            val tenantBrandingDefinition = BrandingService.getInstance(context).TenantBrandingDefinition()
            val brandingDocumentUri = tenantBrandingDefinition?.documentationRootUrl ?: deployment?.documentationUri

            val basePath = formatString(brandingDocumentUri ?: "", lang)
            val subPath = if (path == null) "" else "/$path"
            val tenantId = if (user == null) "" else "?tenantId=${user.tenantId}"

            if (basePath.isNotBlank()) {
                openBrowser(context, "${basePath}${subPath}$tenantId}")
            }
        }

        private fun formatString(template: String, vararg args: Any): String {
            var formattedString = template
            args.forEachIndexed { index, arg ->
                formattedString = formattedString.replace("{$index}", arg.toString())
            }
            return formattedString
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
