package com.kipu.app.feature.settings.data

import java.util.UUID
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteProfileDtoTest {
    private val owner = "a0699999-1111-4111-8111-111111111111"

    @Test fun nullableRemoteNameAndMetadataDecodeAndNormalizeForRoom() {
        val remote = Json.decodeFromString<RemoteProfileDto>("""{"user_id":"$owner","display_name":null,"time_zone":null,"updated_at":null}""")
        val cached = remote.toCacheEntity()
        assertEquals(UUID.fromString(owner), cached.userId)
        assertEquals("", cached.displayName)
        assertEquals("PEN", cached.currencyCode)
        assertNull(cached.timeZone)
    }

    @Test fun namedProfileKeepsItsServerValue() {
        val remote = Json.decodeFromString<RemoteProfileDto>("""{"user_id":"$owner","display_name":"Fixture"}""")
        assertEquals("Fixture", remote.toCacheEntity().displayName)
    }

    @Test fun absentNameUsesSameEmptyUiDefault() {
        val remote = Json.decodeFromString<RemoteProfileDto>("""{"user_id":"$owner"}""")
        assertEquals("", remote.toCacheEntity().displayName)
    }
}
