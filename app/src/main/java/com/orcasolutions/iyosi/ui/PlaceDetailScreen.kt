package com.orcasolutions.iyosi.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.HighlightOff
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orcasolutions.iyosi.AppState
import com.orcasolutions.iyosi.data.AdvisoryStatus
import com.orcasolutions.iyosi.data.DateText
import com.orcasolutions.iyosi.data.GeoPoint
import com.orcasolutions.iyosi.data.Place
import com.orcasolutions.iyosi.data.PublicConfirmation
import com.orcasolutions.iyosi.data.PublicReview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun PlaceDetailScreen(placeId: String, state: AppState, navigator: Navigator) {
    val scope = rememberCoroutineScope()
    // Keyed by server so a server change never leaves another server's record on screen.
    var place by remember(state.baseUrl) { mutableStateOf<Place?>(null) }
    var reviews by remember(state.baseUrl) { mutableStateOf(emptyList<PublicReview>()) }
    var confirmations by remember(state.baseUrl) { mutableStateOf(emptyList<PublicConfirmation>()) }
    var reviewCursor by remember(state.baseUrl) { mutableStateOf<String?>(null) }
    var confirmationCursor by remember(state.baseUrl) { mutableStateOf<String?>(null) }
    var loading by remember(state.baseUrl) { mutableStateOf(true) }
    var error by remember(state.baseUrl) { mutableStateOf<String?>(null) }
    // Reload whenever this page returns to the top, as iOS does when a contribution sheet closes.
    val isTop = navigator.stack.lastOrNull() == Route.Detail(placeId)

    LaunchedEffect(placeId, isTop, state.baseUrl) {
        if (!isTop) return@LaunchedEffect
        error = null
        try {
            val result = state.api.place(placeId)
            place = result
            if (result.advisoryStatus == AdvisoryStatus.Closed || result.mergedInto != null) {
                reviews = emptyList(); reviewCursor = null
                confirmations = emptyList(); confirmationCursor = null
            } else {
                runCatching { state.api.reviews(placeId) }.getOrNull()?.let { reviews = it.items; reviewCursor = it.nextCursor }
                runCatching { state.api.confirmations(placeId) }.getOrNull()?.let {
                    confirmations = it.items; confirmationCursor = it.nextCursor
                }
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            if (place == null) error = failure.message ?: "The place could not load."
        }
        loading = false
    }

    GuidePage(place?.name ?: "Place", onClose = navigator::pop) { padding ->
        val loaded = place
        when {
            loaded != null -> PlaceContent(
                padding, loaded, state, navigator, reviews, confirmations,
                moreReviews = reviewCursor?.let { cursor ->
                    {
                        scope.launch {
                            runCatching { state.api.reviews(placeId, cursor) }.getOrNull()?.let {
                                reviews = reviews + it.items; reviewCursor = it.nextCursor
                            }
                        }
                        Unit
                    }
                },
                moreConfirmations = confirmationCursor?.let { cursor ->
                    {
                        scope.launch {
                            runCatching { state.api.confirmations(placeId, cursor) }.getOrNull()?.let {
                                confirmations = confirmations + it.items; confirmationCursor = it.nextCursor
                            }
                        }
                        Unit
                    }
                },
            )
            loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Guide.colors.tealInk)
            }
            else -> GuideColumn(padding) {
                Icon(Icons.Outlined.LocationOff, contentDescription = null, tint = Guide.colors.muted, modifier = Modifier.size(40.dp))
                GuideHeading(error ?: "The place could not load.", size = 20)
                GuideMuted("The place may be unavailable or the API may be offline.")
            }
        }
    }
}

@Composable
private fun PlaceContent(
    padding: androidx.compose.foundation.layout.PaddingValues,
    place: Place,
    state: AppState,
    navigator: Navigator,
    reviews: List<PublicReview>,
    confirmations: List<PublicConfirmation>,
    moreReviews: (() -> Unit)?,
    moreConfirmations: (() -> Unit)?,
) {
    val colors = Guide.colors
    val context = LocalContext.current
    GuideColumn(padding, spacing = 20) {
        GuideCard(spacing = 10.dp) {
            GuideHeading(place.name, size = 27)
            KindLabel(place.kind, size = 15)
            GuideStatusBadge(place.advisoryStatus)
            GuideMuted(
                place.verification?.let { "Evidence reviewed ${DateText.display(it.reviewedAt)}" }
                    ?: "No reviewed designation evidence on record.",
                13,
            )
            GuideFact("Last confirmation", DateText.display(place.lastConfirmedAt), Icons.Outlined.Schedule)
            when (place.advisoryStatus) {
                AdvisoryStatus.VerifiedDesignated -> GuideNotice("Evidence reviewed is an advisory designation, not official certification. Check posted rules and current conditions.")
                AdvisoryStatus.CommunityReported, AdvisoryStatus.Disputed -> GuideNotice(
                    "This report does not establish permission to smoke here.", Icons.Outlined.Warning,
                    warning = place.advisoryStatus == AdvisoryStatus.Disputed,
                )
                AdvisoryStatus.Prohibited -> GuideNotice("Smoking is reported as prohibited here. No directions are provided.", Icons.Filled.Block, warning = true)
                AdvisoryStatus.Closed -> GuideNotice("This place is closed. No directions are provided.", Icons.Outlined.HighlightOff, warning = true)
            }
            place.mergedInto?.let { merged ->
                GuideNotice("This place was merged into another record.", Icons.Outlined.Info)
                TextButton(onClick = { navigator.push(Route.Detail(merged)) }) { Text("Open the current record") }
            }
        }

        GuideCard(spacing = 18.dp) {
            GuideEyebrow("Before you go")
            if (place.directionsAllowed) place.accessNotes?.let { GuideFact("Access note", it, Icons.AutoMirrored.Filled.DirectionsWalk) }
            place.addressLabel?.let { GuideFact("Location", it, Icons.Filled.Place) }
            place.insideVenue?.let {
                GuideFact("Setting", if (it) "Inside establishment" else "Outside establishment", Icons.Outlined.Business)
            }
            place.verification?.let { GuideFact("Public source", it.publicSource, Icons.Outlined.Description) }
            GuideMuted(if (place.freshness == "fresh") "Recent community confirmation" else "No recent community confirmation")
            if (place.directionsAllowed) {
                var noMapsApp by remember { mutableStateOf(false) }
                GuideButton(
                    onClick = { noMapsApp = !openDirections(context, place.accessPoint ?: place.location, place.name) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Directions to public access point")
                }
                if (noMapsApp) GuideMuted("No maps app or browser is available to open directions.", 13)
            }
        }

        if (place.advisoryStatus != AdvisoryStatus.Closed && place.mergedInto == null) {
            GuideCard(spacing = 12.dp) {
                GuideEyebrow("Help keep the guide current")
                GuideHeading("Share what you observed.", size = 23, editorial = true)
                GuideMuted("Confirmations and ratings describe community experience. They do not grant permission.")
                GuideButton(onClick = { navigator.push(Route.Confirm(place.id)) }, secondary = true, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Confirm an observation")
                }
                GuideButton(onClick = { navigator.push(Route.Review(place.id)) }, secondary = true, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.StarOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Rate or review")
                }
                Row {
                    TextButton(onClick = { navigator.push(Route.Submit(place.location, place)) }) { Text("Suggest correction") }
                    TextButton(onClick = { navigator.push(Route.Report("place", place.id)) }) { Text("Report issue") }
                }
            }
        }

        GuideCard(spacing = 14.dp) {
            GuideEyebrow("Community observations")
            GuideHeading("Recent observations", size = 22)
            if (confirmations.isEmpty()) GuideMuted("No observations yet.", 16)
            confirmations.forEach {
                GuideFact(
                    DateText.observation(it.observation), DateText.display(it.observedAt),
                    if (it.observation == "still_there") Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
                )
            }
            moreConfirmations?.let { TextButton(onClick = it) { Text("More observations") } }
        }

        GuideCard(spacing = 14.dp) {
            GuideEyebrow("Community experience")
            GuideHeading("Reviews", size = 22)
            val average = place.rating.average?.let { "%.1f".format(it) } ?: "—"
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.StarOutline, contentDescription = null, tint = colors.ink, modifier = Modifier.size(18.dp))
                Text("$average / 5 · ${place.rating.count} reviews", style = Guide.heading(15.sp), color = colors.ink)
            }
            if (reviews.isEmpty()) GuideMuted("No reviews yet.", 16)
            reviews.forEach { review ->
                HorizontalDivider(color = colors.line)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("${review.stars} / 5 stars", style = Guide.heading(16.sp), color = colors.ink)
                    GuideMuted(DateText.display(review.updatedAt), 13)
                    review.body?.let { Text(it, style = Guide.body(16.sp), color = colors.ink) }
                    TextButton(onClick = { navigator.push(Route.Report("review", review.id)) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Text("Report review", style = Guide.body(14.sp))
                    }
                }
            }
            moreReviews?.let { TextButton(onClick = it) { Text("More reviews") } }
        }
    }
}

/**
 * Opens walking directions like iOS does. The universal Maps URL opens the maps app or a browser;
 * a plain geo: pin is the fallback. Returns false when nothing on the device can open either.
 */
private fun openDirections(context: android.content.Context, point: GeoPoint, name: String): Boolean {
    val directions = Uri.parse("https://www.google.com/maps/dir/?api=1&travelmode=walking&destination=${point.lat},${point.lon}")
    val geo = Uri.parse("geo:${point.lat},${point.lon}?q=${point.lat},${point.lon}(${Uri.encode(name)})")
    for (uri in listOf(directions, geo)) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            return true
        } catch (_: ActivityNotFoundException) {
        }
    }
    return false
}
