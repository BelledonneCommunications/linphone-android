package org.linphone.typeadapters

import com.google.gson.JsonParseException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.linphone.models.UserInfo
import org.linphone.models.callhistory.CallInteractionTag
import org.linphone.utils.GsonUtils

class BooleanTypeAdapterTest {

    private val adapter = BooleanTypeAdapter()
    private val gson = GsonUtils.create()

    @Test
    fun `reads JSON booleans`() {
        assertEquals(true, adapter.fromJson("true"))
        assertEquals(false, adapter.fromJson("false"))
    }

    @Test
    fun `reads 1 as true and 0 as false`() {
        assertEquals(true, adapter.fromJson("1"))
        assertEquals(false, adapter.fromJson("0"))
    }

    @Test
    fun `reads any number other than 1 as false`() {
        assertEquals(false, adapter.fromJson("2"))
        assertEquals(false, adapter.fromJson("-1"))
    }

    @Test
    fun `reads strings like Gson's default boolean handling, plus 1`() {
        assertEquals(true, adapter.fromJson("\"true\""))
        assertEquals(true, adapter.fromJson("\"TRUE\""))
        assertEquals(true, adapter.fromJson("\"1\""))
        assertEquals(false, adapter.fromJson("\"false\""))
        assertEquals(false, adapter.fromJson("\"0\""))
        assertEquals(false, adapter.fromJson("\"yes\""))
    }

    @Test
    fun `reads null as null`() {
        assertNull(adapter.fromJson("null"))
    }

    @Test(expected = JsonParseException::class)
    fun `rejects objects`() {
        adapter.fromJson("{}")
    }

    @Test
    fun `writes JSON booleans`() {
        assertEquals("true", adapter.toJson(true))
        assertEquals("false", adapter.toJson(false))
        assertEquals("null", adapter.toJson(null))
    }

    @Test
    fun `is applied to Kotlin Boolean properties by the gateway Gson`() {
        val user = gson.fromJson("""{"isEnabled": 0}""", UserInfo::class.java)
        assertFalse(user.isEnabled)
    }

    @Test
    fun `null leaves a Kotlin Boolean property at its default instead of failing`() {
        val user = gson.fromJson(
            """{"isEnabled": null, "displayName": "Test"}""",
            UserInfo::class.java
        )
        assertTrue("UserInfo.isEnabled defaults to true", user.isEnabled)
        assertEquals("Test", user.displayName)
    }

    @Test
    fun `is applied to nullable Boolean properties by the gateway Gson`() {
        val tag = gson.fromJson("""{"required": 1}""", CallInteractionTag::class.java)
        assertEquals(true, tag.required)

        val unset = gson.fromJson("""{"required": null}""", CallInteractionTag::class.java)
        assertNull(unset.required)
    }
}
