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
import org.threeten.bp.ZonedDateTime
import org.threeten.bp.format.DateTimeParseException

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
            val instant = try {
                // Timestamps with a zone or offset, e.g. "2025-03-01T10:15:30Z" (including our own output)
                ZonedDateTime.parse(dateTimeString).toInstant()
            } catch (e: DateTimeParseException) {
                // Timestamps without a zone are treated as UTC
                LocalDateTime.parse(dateTimeString).atZone(ZoneId.of("UTC")).toInstant()
            }

            return Date(instant.toEpochMilli())
        } catch (e: Exception) {
            Log.e(e)
            return Date()
        }
    }
}
