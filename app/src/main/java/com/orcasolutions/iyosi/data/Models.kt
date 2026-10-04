package com.orcasolutions.iyosi.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Serializable
data class GeoPoint(val lat: Double, val lon: Double)

@Serializable
enum class PlaceKind(val title: String) {
    @SerialName("public_designated") PublicDesignated("Public designated area"),
    @SerialName("venue_designated") VenueDesignated("Establishment designated area"),
    @SerialName("informal_outdoor") InformalOutdoor("Community outdoor spot"),
    @SerialName("venue_claim") VenueClaim("Establishment claim"),
}

@Serializable
enum class AdvisoryStatus(val title: String) {
    @SerialName("verified_designated") VerifiedDesignated("Evidence-reviewed designation"),
    @SerialName("community_reported") CommunityReported("Community report · unverified"),
    @SerialName("disputed") Disputed("Disputed"),
    @SerialName("prohibited") Prohibited("Prohibited · warning"),
    @SerialName("closed") Closed("Closed");

    val isWarning get() = this == Disputed || this == Prohibited || this == Closed

    val explanation: String
        get() = when (this) {
            VerifiedDesignated -> "Supporting evidence for this designation has been reviewed by the iYosi team. Check the evidence date and current signs."
            CommunityReported -> "A community observation without verified designation evidence. It does not establish permission to smoke."
            Disputed -> "The information has been questioned. Read the warning and check current rules before visiting."
            Prohibited -> "Smoking is reported as prohibited. Directions are unavailable."
            Closed -> "This record is closed. Directions are unavailable."
        }
}

@Serializable
data class Rating(val average: Double? = null, val count: Int = 0)

@Serializable
data class Verification(val reviewedAt: String, val publicSource: String)

@Serializable
data class PlaceSummary(
    val id: String,
    val name: String,
    val kind: PlaceKind,
    val advisoryStatus: AdvisoryStatus,
    val location: GeoPoint,
    val verificationReviewedAt: String? = null,
    val lastConfirmedAt: String? = null,
    val freshness: String = "stale",
    val rating: Rating = Rating(),
    val updatedAt: String = "",
    val directionsAllowed: Boolean = false,
)

@Serializable
data class Place(
    val id: String,
    val name: String,
    val kind: PlaceKind,
    val advisoryStatus: AdvisoryStatus,
    val location: GeoPoint,
    val accessPoint: GeoPoint? = null,
    val addressLabel: String? = null,
    val accessNotes: String? = null,
    val insideVenue: Boolean? = null,
    val verification: Verification? = null,
    val lastConfirmedAt: String? = null,
    val rating: Rating = Rating(),
    val updatedAt: String = "",
    val version: Int = 0,
    val directionsAllowed: Boolean = false,
    val freshness: String = "stale",
    val mergedInto: String? = null,
)

@Serializable
data class Page<T>(val items: List<T>, val nextCursor: String? = null)

@Serializable
data class AddressSuggestion(val label: String, val location: GeoPoint)

@Serializable
data class AddressSuggestions(val items: List<AddressSuggestion>, val provider: String? = null)

@Serializable
data class PublicReview(
    val id: String,
    val stars: Int,
    val body: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class PublicConfirmation(
    val id: String,
    val observation: String,
    val observedAt: String,
    val createdAt: String,
)

@Serializable
data class Account(val id: String, val email: String, val role: String, val adultAttestedAt: String)

@Serializable
data class AuthToken(val accessToken: String)

@Serializable
data class Acknowledgement(val id: String, val state: String)

@Serializable
data class SubmissionStatus(
    val id: String,
    val intent: String,
    val placeId: String? = null,
    val name: String,
    val kind: PlaceKind,
    val state: String,
    val decisionReason: String? = null,
    val createdAt: String,
    val decidedAt: String? = null,
)

@Serializable
data class SubmissionList(val items: List<SubmissionStatus>)

@Serializable
data class SubmissionRequest(
    val intent: String,
    val placeId: String?,
    val name: String,
    val kind: PlaceKind,
    val location: GeoPoint,
    val accessPoint: GeoPoint?,
    val addressLabel: String?,
    val accessNotes: String?,
    val insideVenue: Boolean?,
    val basis: String,
)

@Serializable
data class ConfirmationRequest(val observation: String, val observedAt: String, val accessNoteSuggestion: String?)

@Serializable
data class ReviewRequest(val stars: Int, val body: String?)

@Serializable
data class ReportRequest(val targetType: String, val targetId: String, val reason: String, val detail: String?)

@Serializable
data class Credentials(val email: String, val password: String)

@Serializable
data class Registration(val email: String, val password: String, val adultAttested: Boolean)

@Serializable
data class ApiErrorBody(val code: String, val message: String)

object DateText {
    private val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    fun display(value: String?): String {
        value ?: return "No recent confirmation"
        return runCatching { formatter.format(OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault())) }.getOrDefault(value)
    }

    fun observation(raw: String) = when (raw) {
        "still_there" -> "Still there"
        "changed" -> "Changed"
        "gone" -> "Gone"
        else -> raw
    }
}
