package org.linphone.typeadapters
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import org.linphone.models.callhistory.CallDirections

class CallDirectionsAdapter : TypeAdapter<CallDirections>() {
    override fun write(out: JsonWriter, value: CallDirections?) {
        if (value == null) {
            out.nullValue()
        } else {
            out.value(value.value) // Serialize the enum as its integer value
        }
    }

    override fun read(reader: JsonReader): CallDirections? {
        return when (reader.peek()) {
            JsonToken.NULL -> {
                reader.nextNull()
                null
            }
            JsonToken.NUMBER -> CallDirections.fromValue(reader.nextInt()) // Map the integer to the corresponding enum
            // Names, as written by Gson's default enum handling
            else -> reader.nextString().let { str ->
                str.toIntOrNull()?.let { CallDirections.fromValue(it) }
                    ?: CallDirections.values().find { it.name.equals(str, ignoreCase = true) }
                    ?: CallDirections.Unknown
            }
        }
    }
}
