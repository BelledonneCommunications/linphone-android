package org.linphone.utils

import ZonedDateTimeAdapter
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.util.Date
import org.linphone.models.callhistory.CallDirections
import org.linphone.models.callhistory.CallTypes
import org.linphone.typeadapters.BooleanTypeAdapter
import org.linphone.typeadapters.CallDirectionsAdapter
import org.linphone.typeadapters.CallTypesAdapter
import org.linphone.typeadapters.DateTypeAdapter
import org.threeten.bp.ZonedDateTime

class GsonUtils {
    companion object {

        // Single source of the gateway JSON configuration, shared by the API client and the unit tests.
        fun create(): Gson = GsonBuilder()
            // Boolean::class.java is the primitive (Kotlin Boolean), javaObjectType the boxed one (Boolean?)
            .registerTypeAdapter(Boolean::class.java, BooleanTypeAdapter())
            .registerTypeAdapter(Boolean::class.javaObjectType, BooleanTypeAdapter())
            .registerTypeAdapter(Date::class.java, DateTypeAdapter())
            .registerTypeAdapter(ZonedDateTime::class.java, ZonedDateTimeAdapter())
            .registerTypeAdapter(CallTypes::class.java, CallTypesAdapter())
            .registerTypeAdapter(CallDirections::class.java, CallDirectionsAdapter())
            .create()

        var defaultGsonInstance = create()
    }
}
