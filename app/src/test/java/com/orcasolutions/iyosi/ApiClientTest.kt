package com.orcasolutions.iyosi

import com.orcasolutions.iyosi.data.AdvisoryStatus
import com.orcasolutions.iyosi.data.ApiClient
import com.orcasolutions.iyosi.data.ApiException
import com.orcasolutions.iyosi.data.GeoPoint
import com.orcasolutions.iyosi.data.PlaceKind
import com.orcasolutions.iyosi.data.ReportRequest
import com.orcasolutions.iyosi.data.Transport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ApiClientTest {
    private class Recorder(private val status: Int = 200, private val body: String) : Transport {
        val calls = mutableListOf<Triple<String, String, Map<String, String>>>()
        var sentBody: String? = null
        override suspend fun send(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String> {
            calls += Triple(method, url, headers)
            sentBody = body
            return status to this.body
        }
    }

    @Test fun allowsHttpsAndLocalDevelopmentHostsOnly() {
        assertTrue(ApiClient.isAllowedBaseUrl("https://api.example.com"))
        assertTrue(ApiClient.isAllowedBaseUrl("http://10.0.2.2:8000"))
        assertTrue(ApiClient.isAllowedBaseUrl("http://localhost:8000"))
        assertTrue(ApiClient.isAllowedBaseUrl("http://iyosi.local"))
        assertFalse(ApiClient.isAllowedBaseUrl("http://api.example.com"))
        assertFalse(ApiClient.isAllowedBaseUrl("ftp://api.example.com"))
        assertFalse(ApiClient.isAllowedBaseUrl("not a url"))
        // Release builds block cleartext, so local HTTP is refused there.
        assertFalse(ApiClient.isAllowedBaseUrl("http://10.0.2.2:8000", allowLocalHttp = false))
        assertTrue(ApiClient.isAllowedBaseUrl("https://api.example.com", allowLocalHttp = false))
    }

    @Test fun rejectedTokenSignsOutAndFieldErrorsAreShown() = runBlocking {
        var signedOut = false
        val transport = Recorder(422, """{"code":"invalid_input","message":"Check the submitted fields",
            "field_errors":[{"field":"body.email","message":"String should have at least 5 characters"}]}""")
        try {
            ApiClient("https://api.example.com", null, transport).register("a@b", "password-long")
            fail("Expected an error")
        } catch (error: ApiException) {
            assertEquals("Check the submitted fields (email: String should have at least 5 characters)", error.message)
        }
        val crossField = Recorder(422, """{"code":"invalid_input","message":"Check the submitted fields",
            "field_errors":[{"field":"body","message":"Value error, entrance must be inside the venue"}]}""")
        try {
            ApiClient("https://api.example.com", null, crossField).register("a@b.co", "password-long")
            fail("Expected an error")
        } catch (error: ApiException) {
            assertEquals("Check the submitted fields (Value error, entrance must be inside the venue)", error.message)
        }
        val expired = Recorder(401, """{"code":"unauthenticated","message":"Sign in to continue"}""")
        try {
            ApiClient("https://api.example.com", "old", expired) { signedOut = true }.me()
        } catch (_: ApiException) {
        }
        assertTrue(signedOut)
    }

    @Test fun encodesQueryAndPathSegments() {
        assertEquals("a%20b%2Fc%3F%26", ApiClient.percentEncode("a b/c?&"))
        assertEquals(
            "https://api.example.com/base/v1/places?bbox=1%2C2&q=Makati%20Ave",
            ApiClient.buildUrl("https://api.example.com/base/", "/v1/places", listOf("bbox" to "1,2", "q" to "Makati Ave")),
        )
    }

    @Test fun decodesSnakeCasePlacePage() = runBlocking {
        val transport = Recorder(body = """
            {"items":[{"id":"p1","name":"Courtyard","kind":"public_designated","advisory_status":"verified_designated",
              "location":{"lat":14.59,"lon":120.98},"verification_reviewed_at":"2026-09-01T00:00:00Z",
              "last_confirmed_at":null,"freshness":"fresh","rating":{"average":4.5,"count":2},
              "updated_at":"2026-09-02T00:00:00Z","directions_allowed":true,"future_field":1}],
             "next_cursor":"abc"}
        """.trimIndent())
        val page = ApiClient("https://api.example.com", "token", transport).places("1,2,3,4", includeProhibited = true)
        val place = page.items.single()
        assertEquals(PlaceKind.PublicDesignated, place.kind)
        assertEquals(AdvisoryStatus.VerifiedDesignated, place.advisoryStatus)
        assertEquals(GeoPoint(14.59, 120.98), place.location)
        assertNull(place.lastConfirmedAt)
        assertEquals("abc", page.nextCursor)
        val (method, url, headers) = transport.calls.single()
        assertEquals("GET", method)
        assertEquals("https://api.example.com/v1/places?bbox=1%2C2%2C3%2C4&limit=100&include_prohibited=true", url)
        assertEquals("Bearer token", headers["Authorization"])
    }

    @Test fun encodesRequestsInSnakeCaseWithIdempotencyKey() = runBlocking {
        val transport = Recorder(body = """{"id":"r1","state":"open"}""")
        val ack = ApiClient("https://api.example.com", "t", transport)
            .report(ReportRequest("place", "p1", "spam", null), key = "k1")
        assertEquals("r1", ack.id)
        assertEquals("""{"target_type":"place","target_id":"p1","reason":"spam"}""", transport.sentBody)
        assertEquals("k1", transport.calls.single().third["Idempotency-Key"])
    }

    @Test fun surfacesApiErrorCodeAndMessage() = runBlocking {
        val transport = Recorder(503, """{"code":"geocoder_unavailable","message":"Address search is not configured."}""")
        try {
            ApiClient("https://api.example.com", null, transport).suggestions("Makati")
            fail("Expected an error")
        } catch (error: ApiException) {
            assertEquals(503, error.status)
            assertEquals("geocoder_unavailable", error.code)
            assertEquals("Address search is not configured.", error.message)
        }
    }

    @Test fun rejectsInvalidServerWithoutSending() = runBlocking {
        val transport = Recorder(body = "{}")
        try {
            ApiClient("http://example.com", null, transport).me()
            fail("Expected an error")
        } catch (error: ApiException) {
            assertEquals("invalid_server_url", error.code)
        }
        assertTrue(transport.calls.isEmpty())
    }
}
