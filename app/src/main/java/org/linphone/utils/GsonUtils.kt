package org.linphone.utils

import ZonedDateTimeAdapter
import com.google.gson.GsonBuilder
import java.util.Date
import org.linphone.typeadapters.BooleanTypeAdapter
import org.linphone.typeadapters.DateTypeAdapter
import org.threeten.bp.ZonedDateTime

class GsonUtils {
    companion object {

        var defaultGsonInstance = GsonBuilder()
            .registerTypeAdapter(Boolean::class.java, BooleanTypeAdapter()) // Register the adapter
            .registerTypeAdapter(Date::class.java, DateTypeAdapter())
            .registerTypeAdapter(ZonedDateTime::class.java, ZonedDateTimeAdapter())
            .create()
    }
}
