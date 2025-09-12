import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import org.linphone.utils.Log
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneId
import org.threeten.bp.ZonedDateTime
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.format.DateTimeParseException

class ZonedDateTimeAdapter : TypeAdapter<ZonedDateTime>() {
    override fun write(out: JsonWriter, value: ZonedDateTime?) {
        try {
            if (value == null) {
                out.nullValue()
            } else {
                out.value(value.format(DateTimeFormatter.ISO_ZONED_DATE_TIME)) // Serialize ZonedDateTime to ISO 8601 string
            }
        } catch (e: Exception) {
            Log.e("ZonedDateTimeAdapter.write", e)
            out.nullValue()
        }
    }

    override fun read(reader: JsonReader): ZonedDateTime? {
        try {
            return if (reader.peek() == com.google.gson.stream.JsonToken.NULL) {
                reader.nextNull()
                null
            } else {
                val str = reader.nextString()
                try {
                    ZonedDateTime.parse(str)
                } catch (e: DateTimeParseException) {
                    LocalDateTime.parse(str).atZone(ZoneId.systemDefault())
                }
            }
        } catch (e: Exception) {
            Log.e("ZonedDateTimeAdapter.read", e)
            return null
        }
    }
}
