package org.linphone.typeadapters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.linphone.models.callhistory.CallDirections
import org.linphone.models.callhistory.CallTypes
import org.linphone.utils.GsonUtils

class CallEnumAdaptersTest {

    private val directions = CallDirectionsAdapter()
    private val types = CallTypesAdapter()

    @Test
    fun `reads call directions by number`() {
        assertEquals(CallDirections.Unknown, directions.fromJson("0"))
        assertEquals(CallDirections.Internal, directions.fromJson("1"))
        assertEquals(CallDirections.Incoming, directions.fromJson("2"))
        assertEquals(CallDirections.Outgoing, directions.fromJson("3"))
        assertEquals(CallDirections.Both, directions.fromJson("4"))
    }

    @Test
    fun `reads call directions by name`() {
        assertEquals(CallDirections.Incoming, directions.fromJson("\"Incoming\""))
        assertEquals(CallDirections.Outgoing, directions.fromJson("\"outgoing\""))
        assertEquals(CallDirections.Internal, directions.fromJson("\"1\""))
    }

    @Test
    fun `reads unknown call directions as Unknown`() {
        assertEquals(CallDirections.Unknown, directions.fromJson("99"))
        assertEquals(CallDirections.Unknown, directions.fromJson("\"Sideways\""))
    }

    @Test
    fun `round-trips call directions`() {
        for (value in CallDirections.values()) {
            assertEquals(value, directions.fromJson(directions.toJson(value)))
        }
    }

    @Test
    fun `reads call types by number`() {
        assertEquals(CallTypes.Unknown, types.fromJson("0"))
        assertEquals(CallTypes.Internal, types.fromJson("1"))
        assertEquals(CallTypes.External, types.fromJson("2"))
    }

    @Test
    fun `reads call types by name`() {
        // Older call history caches were written by Gson's default enum handling, i.e. by name.
        assertEquals(CallTypes.External, types.fromJson("\"External\""))
        assertEquals(CallTypes.Internal, types.fromJson("\"internal\""))
        assertEquals(CallTypes.External, types.fromJson("\"2\""))
    }

    @Test
    fun `reads unknown call types as Unknown`() {
        assertEquals(CallTypes.Unknown, types.fromJson("99"))
        assertEquals(CallTypes.Unknown, types.fromJson("\"Carrier pigeon\""))
    }

    @Test
    fun `round-trips call types`() {
        for (value in CallTypes.values()) {
            assertEquals(value, types.fromJson(types.toJson(value)))
        }
    }

    @Test
    fun `reads and writes null`() {
        assertNull(directions.fromJson("null"))
        assertNull(types.fromJson("null"))
        assertEquals("null", directions.toJson(null))
        assertEquals("null", types.toJson(null))
    }

    @Test
    fun `are registered in the gateway Gson`() {
        val gson = GsonUtils.create()
        assertEquals(CallTypes.External, gson.fromJson("2", CallTypes::class.java))
        assertEquals(CallTypes.External, gson.fromJson("\"External\"", CallTypes::class.java))
        assertEquals(CallDirections.Incoming, gson.fromJson("2", CallDirections::class.java))
        assertEquals("2", gson.toJson(CallTypes.External))
    }
}
