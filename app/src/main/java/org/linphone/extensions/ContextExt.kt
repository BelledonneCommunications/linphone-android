package org.linphone.extensions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import org.linphone.core.tools.Log

fun Context.openHanTalkChat(chatId: String = "123") {
    val uri = "hantalk_scheme://hantalk_host?chatId=$chatId".toUri()
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
}

fun Context.isHanTalkAppInstalled(): Boolean {
    return try {
        packageManager.getPackageInfo("com.hansol.hantalk", 0)
        Log.i("isHanTalkAppInstalled: app found")
        true
    } catch (e: PackageManager.NameNotFoundException) {
        Log.i("isHanTalkAppInstalled: app not found")
        false
    }
}
