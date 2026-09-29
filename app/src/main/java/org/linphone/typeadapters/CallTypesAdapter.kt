package org.linphone.typeadapters

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import org.linphone.models.callhistory.CallTypes

class CallTypesAdapter : TypeAdapter<CallTypes>() {
    override fun write(out: JsonWriter, value: CallTypes?) {
        if (value == null) {
            out.nullValue()
        } else {
            out.value(value.callTypeValue) // Serialize the enum as its integer value
        }
    }

    override fun read(reader: JsonReader): CallTypes? {
        return when (reader.peek()) {
            JsonToken.NULL -> {
                reader.nextNull()
                null
            }
            JsonToken.NUMBER -> CallTypes.fromValue(reader.nextInt()) // Map the integer to the corresponding enum
            // Names, as written by Gson's default enum handling (e.g. older call history caches)
            else -> reader.nextString().let { str ->
                str.toIntOrNull()?.let { CallTypes.fromValue(it) }
                    ?: CallTypes.values().find { it.name.equals(str, ignoreCase = true) }
                    ?: CallTypes.Unknown
            }
        }
    }
}
