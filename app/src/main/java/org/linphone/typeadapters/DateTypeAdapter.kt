package org.linphone.typeadapters

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.linphone.utils.Log
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneId

class DateTypeAdapter : TypeAdapter<Date>() {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC") // Ensure UTC timezone
    }

    override fun write(out: JsonWriter, value: Date?) {
        if (value == null) {
            out.nullValue() // Write null if the date is null
        } else {
            val formattedDate = dateFormat.format(value) // Format the Date to ISO 8601
            out.value(formattedDate)
        }
    }

    override fun read(reader: JsonReader): Date? {
        return if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            null
        } else {
            convertToDate(reader.nextString())
        }
    }

    private fun convertToDate(dateTimeString: String): Date {
        try {
            // Parse the string into a LocalDateTime
            val localDateTime = LocalDateTime.parse(dateTimeString)

            // Convert LocalDateTime to an Instant using a time zone (e.g., UTC)
            val instant = localDateTime.atZone(ZoneId.of("UTC")).toInstant()

            val epochMilli = instant.toEpochMilli()

            // Convert the Instant to a Date
            val theDate = Date(epochMilli)

            return theDate
        } catch (e: Exception) {
            Log.e(e)
            return Date()
        }
    }
}
