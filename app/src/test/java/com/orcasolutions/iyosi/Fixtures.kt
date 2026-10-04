package com.orcasolutions.iyosi

import com.orcasolutions.iyosi.data.Transport
import java.net.URI

/** Read-only fictional records, like the iOS design preview. Writes are rejected. */
object Fixtures {
    private data class Record(val id: String, val name: String, val kind: String, val status: String, val lat: Double, val lon: Double)

    private val records = listOf(
        Record("reviewed", "Fictional courtyard area", "public_designated", "verified_designated", 14.594, 120.985),
        Record("community", "Fictional neighborhood report", "informal_outdoor", "community_reported", 14.587, 121.002),
        Record("disputed", "Fictional riverside report", "informal_outdoor", "disputed", 14.609, 120.975),
        Record("prohibited", "Fictional prohibited area", "venue_claim", "prohibited", 14.58, 120.975),
        Record("closed", "Fictional closed area", "venue_designated", "closed", 14.59, 121.01),
    )

    private fun summary(r: Record): String {
        val restricted = r.status == "prohibited" || r.status == "closed"
        val reviewed = if (r.status == "verified_designated") "\"2026-09-15T00:00:00Z\"" else "null"
        return """{"id":"${r.id}","name":"${r.name}","kind":"${r.kind}","advisory_status":"${r.status}",
            "location":{"lat":${r.lat},"lon":${r.lon}},"verification_reviewed_at":$reviewed,
            "last_confirmed_at":"2026-10-01T09:00:00Z","freshness":"fresh","rating":{"average":4.0,"count":1},
            "updated_at":"2026-10-01T09:00:00Z","directions_allowed":${!restricted}}"""
    }

    private fun place(r: Record): String {
        val restricted = r.status == "prohibited" || r.status == "closed"
        val access = if (restricted) "" else """"access_point":{"lat":${r.lat},"lon":${r.lon}},
            "access_notes":"Fictional access note for layout testing. Check current signs and venue rules. Do not travel to this sample coordinate.","""
        val verification = if (r.status == "verified_designated") """"verification":{"reviewed_at":"2026-09-15T00:00:00Z","public_source":"Fictional ordinance excerpt for layout testing"},""" else ""
        return """{"id":"${r.id}","name":"${r.name}","kind":"${r.kind}","advisory_status":"${r.status}",
            "location":{"lat":${r.lat},"lon":${r.lon}},$access $verification
            "address_label":"Fictional design fixture · not a real destination","inside_venue":false,
            "last_confirmed_at":"2026-10-01T09:00:00Z","rating":{"average":4.0,"count":1},"freshness":"fresh",
            "updated_at":"2026-10-01T09:00:00Z","version":1,"directions_allowed":${!restricted}}"""
    }

    val transport = Transport { method, url, _, _ ->
        val path = URI(url).path
        if (method != "GET") return@Transport 403 to """{"code":"read_only","message":"The fixture session is read-only."}"""
        val segments = path.removePrefix("/v1/").split("/")
        when {
            path == "/v1/places" || path == "/v1/places/nearby" ->
                200 to """{"items":[${records.joinToString(",") { summary(it) }}],"next_cursor":null}"""
            path == "/v1/me" -> 200 to """{"id":"u1","email":"pilot@example.com","role":"member","adult_attested_at":"2026-09-01T00:00:00Z"}"""
            path == "/v1/me/place-submissions" -> 200 to """{"items":[{"id":"s1","intent":"create","place_id":null,
                "name":"Fictional rooftop report","kind":"informal_outdoor","state":"pending","decision_reason":null,
                "created_at":"2026-10-02T08:00:00Z","decided_at":null}]}"""
            segments.size == 3 && segments[2] == "reviews" -> 200 to """{"items":[{"id":"rv1","stars":4,
                "body":"Fictional review text for layout testing.","created_at":"2026-09-20T00:00:00Z","updated_at":"2026-09-20T00:00:00Z"}],"next_cursor":null}"""
            segments.size == 3 && segments[2] == "confirmations" -> 200 to """{"items":[{"id":"c1","observation":"still_there",
                "observed_at":"2026-10-01T09:00:00Z","created_at":"2026-10-01T09:05:00Z"}],"next_cursor":null}"""
            segments.size == 2 && segments[0] == "places" -> records.firstOrNull { it.id == segments[1] }
                ?.let { 200 to place(it) } ?: (404 to """{"code":"not_found","message":"Place not found."}""")
            else -> 404 to """{"code":"not_found","message":"Not found."}"""
        }
    }

    fun state(signedIn: Boolean = false, adult: Boolean = true) = AppState(
        prefs = object : AppState.Preferences {
            val values = mutableMapOf<String, Any>("adultAccessAccepted" to adult)
            override fun string(key: String) = values[key] as String?
            override fun putString(key: String, value: String) { values[key] = value }
            override fun boolean(key: String) = values[key] as Boolean? ?: false
            override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        },
        tokens = object : AppState.TokenSink {
            var token: String? = if (signedIn) "fixture" else null
            override fun read() = token
            override fun save(token: String?) { this.token = token }
        },
        transport = transport,
    )
}
