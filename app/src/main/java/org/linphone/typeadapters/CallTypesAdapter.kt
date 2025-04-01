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
        return if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            null
        } else {
            val intValue = reader.nextInt()
            CallTypes.fromValue(intValue) // Map the integer to the corresponding enum
        }
    }
}
