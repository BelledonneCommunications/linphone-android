package org.linphone.typeadapters

import ZonedDateTimeAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneId
import org.threeten.bp.ZoneOffset
import org.threeten.bp.ZonedDateTime

class ZonedDateTimeAdapterTest {

    private val adapter = ZonedDateTimeAdapter()

    @Test
    fun `reads UTC timestamps`() {
        val expected = ZonedDateTime.of(2025, 3, 1, 10, 15, 30, 0, ZoneOffset.UTC)
        assertEquals(expected, adapter.fromJson("\"2025-03-01T10:15:30Z\""))
    }

    @Test
    fun `reads offsets`() {
        val parsed = adapter.fromJson("\"2025-03-01T10:15:30+01:00\"")!!
        assertEquals(ZoneOffset.ofHours(1), parsed.offset)
        assertEquals(LocalDateTime.of(2025, 3, 1, 10, 15, 30), parsed.toLocalDateTime())
    }

    @Test
    fun `reads region zone ids`() {
        val parsed = adapter.fromJson("\"2025-07-01T10:15:30+01:00[Europe/London]\"")!!
        assertEquals(ZoneId.of("Europe/London"), parsed.zone)
    }

    @Test
    fun `reads timestamps without a zone in the device time zone`() {
        val parsed = adapter.fromJson("\"2025-03-01T10:15:30\"")!!
        assertEquals(ZoneId.systemDefault(), parsed.zone)
        assertEquals(LocalDateTime.of(2025, 3, 1, 10, 15, 30), parsed.toLocalDateTime())
    }

    @Test
    fun `reads unparseable values as null instead of failing`() {
        assertNull(adapter.fromJson("\"not a date\""))
    }

    @Test
    fun `reads null as null`() {
        assertNull(adapter.fromJson("null"))
    }

    @Test
    fun `writes ISO zoned date-time`() {
        val value = ZonedDateTime.of(2025, 3, 1, 10, 15, 30, 0, ZoneOffset.UTC)
        assertEquals("\"2025-03-01T10:15:30Z\"", adapter.toJson(value))
    }

    @Test
    fun `round-trips its own output`() {
        val value = ZonedDateTime.of(2025, 7, 1, 10, 15, 30, 0, ZoneId.of("Europe/London"))
        assertEquals(value, adapter.fromJson(adapter.toJson(value)))
    }
}
