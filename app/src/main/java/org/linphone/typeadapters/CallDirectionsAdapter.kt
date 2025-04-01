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
        return if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            null
        } else {
            val intValue = reader.nextInt()
            CallDirections.fromValue(intValue) // Map the integer to the corresponding enum
        }
    }
}
