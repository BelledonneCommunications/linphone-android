package org.linphone.typeadapters

import com.google.gson.JsonParseException
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

class BooleanTypeAdapter : TypeAdapter<Boolean>() {
    override fun write(out: JsonWriter, value: Boolean?) {
        out.value(value) // Serialize as true/false
    }

    override fun read(reader: JsonReader): Boolean {
        return when (reader.peek()) {
            JsonToken.NUMBER -> reader.nextInt() == 1 // Convert 1 to true, 0 to false
            JsonToken.BOOLEAN -> reader.nextBoolean() // Handle standard boolean values
            else -> throw JsonParseException("Invalid boolean value")
        }
    }
}
