package com.orcasolutions.iyosi

import com.orcasolutions.iyosi.data.AdvisoryStatus
import com.orcasolutions.iyosi.data.DateText
import com.orcasolutions.iyosi.data.GeoPoint
import com.orcasolutions.iyosi.data.PlaceKind
import com.orcasolutions.iyosi.data.PlaceSummary
import com.orcasolutions.iyosi.data.Transport
import com.orcasolutions.iyosi.ui.IdempotencyKey
import com.orcasolutions.iyosi.ui.MapBounds
import com.orcasolutions.iyosi.ui.apiBbox
import com.orcasolutions.iyosi.ui.cluster
import com.orcasolutions.iyosi.ui.distanceMeters
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
    private fun summary(id: String, lat: Double, lon: Double) = PlaceSummary(
        id = id, name = id, kind = PlaceKind.InformalOutdoor, advisoryStatus = AdvisoryStatus.CommunityReported,
        location = GeoPoint(lat, lon),
    )

    @Test fun bboxIsClampedLikeIos() {
        assertEquals("120.744000,14.355000,121.224000,14.835000", MapBounds(120.0, 14.0, 121.968, 15.19).apiBbox())
        assertEquals("120.981500,14.592500,120.986500,14.597500", MapBounds(120.983, 14.594, 120.985, 14.596).apiBbox())
    }

    @Test fun clustersOnlyWhenZoomedOut() {
        val places = listOf(summary("a", 14.5901, 120.9801), summary("b", 14.5902, 120.9802), summary("c", 14.70, 121.05))
        val wide = cluster(places, MapBounds(120.9, 14.5, 121.1, 14.75))
        assertEquals(2, wide.size)
        assertEquals(2, wide.first { it.places.size > 1 }.places.size)
        val close = cluster(places, MapBounds(120.979, 14.589, 120.981, 14.591))
        assertEquals(3, close.size)
    }

    @Test fun measuresDistance() {
        val d = distanceMeters(GeoPoint(14.5995, 120.9842), GeoPoint(14.6004, 120.9842))
        assertTrue(d in 95.0..105.0)
    }

    @Test fun idempotencyKeyChangesOnlyWithPayload() {
        val key = IdempotencyKey()
        val first = key.forPayload("a")
        assertEquals(first, key.forPayload("a"))
        assertNotEquals(first, key.forPayload("b"))
    }

    @Test fun formatsDates() {
        assertEquals("No recent confirmation", DateText.display(null))
        assertEquals("not a date", DateText.display("not a date"))
        assertFalse(DateText.display("2026-10-01T09:00:00Z").contains("T09"))
        assertFalse(DateText.display("2026-10-01T09:00:00.123+00:00").contains("T09"))
    }

    @Test fun changingServerSignsOut() = runBlocking {
        val prefs = object : AppState.Preferences {
            val values = mutableMapOf<String, Any>()
            override fun string(key: String) = values[key] as String?
            override fun putString(key: String, value: String) { values[key] = value }
            override fun boolean(key: String) = values[key] as Boolean? ?: false
            override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        }
        val tokens = object : AppState.TokenSink {
            var token: String? = "saved"
            override fun read() = token
            override fun save(token: String?) { this.token = token }
        }
        val transport = Transport { _, _, _, _ -> 200 to """{"id":"u","email":"a@b.c","role":"member","adult_attested_at":"2026-01-01T00:00:00Z"}""" }
        val state = AppState(prefs, tokens, transport)
        assertTrue(state.isSignedIn)
        state.refreshAccount()
        assertEquals("a@b.c", state.account?.email)
        assertFalse(state.updateBaseUrl("http://example.com"))
        assertTrue(state.isSignedIn)
        assertTrue(state.updateBaseUrl("https://api.example.com/"))
        assertEquals("https://api.example.com", state.baseUrl)
        assertFalse(state.isSignedIn)
        assertNull(tokens.token)
    }
}
