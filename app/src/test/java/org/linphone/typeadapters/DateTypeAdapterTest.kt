package org.linphone.typeadapters

import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DateTypeAdapterTest {

    private val adapter = DateTypeAdapter()

    // 2025-03-01T10:15:30Z
    private val instantMillis = 1740824130000L

    @Test
    fun `reads UTC timestamps with a Z suffix`() {
        assertEquals(Date(instantMillis), adapter.fromJson("\"2025-03-01T10:15:30Z\""))
    }

    @Test
    fun `reads offsets`() {
        assertEquals(Date(instantMillis), adapter.fromJson("\"2025-03-01T11:15:30+01:00\""))
    }

    @Test
    fun `reads local date-time as UTC`() {
        assertEquals(Date(instantMillis), adapter.fromJson("\"2025-03-01T10:15:30\""))
    }

    @Test
    fun `reads fractional seconds`() {
        assertEquals(Date(instantMillis + 123), adapter.fromJson("\"2025-03-01T10:15:30.123Z\""))
        assertEquals(Date(instantMillis + 123), adapter.fromJson("\"2025-03-01T10:15:30.123\""))
    }

    @Test
    fun `reads null as null`() {
        assertNull(adapter.fromJson("null"))
    }

    @Test
    fun `writes ISO 8601 in UTC with a Z suffix`() {
        assertEquals("\"2025-03-01T10:15:30.000Z\"", adapter.toJson(Date(instantMillis)))
    }

    @Test
    fun `writes null as null`() {
        assertEquals("null", adapter.toJson(null))
    }

    @Test
    fun `round-trips its own output`() {
        val date = Date(instantMillis + 123)
        assertEquals(date, adapter.fromJson(adapter.toJson(date)))
    }
}
