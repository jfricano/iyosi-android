package com.orcasolutions.iyosi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoorFront
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.selection.toggleable
import com.orcasolutions.iyosi.AppState
import com.orcasolutions.iyosi.data.Acknowledgement
import com.orcasolutions.iyosi.data.ApiException
import com.orcasolutions.iyosi.data.ApiJson
import com.orcasolutions.iyosi.data.GeoPoint
import com.orcasolutions.iyosi.data.Place
import com.orcasolutions.iyosi.data.PlaceKind
import com.orcasolutions.iyosi.data.PlaceSummary
import com.orcasolutions.iyosi.data.SubmissionRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Great-circle distance in metres. */
fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
    return 2 * 6_371_000 * asin(sqrt(h))
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SubmissionScreen(state: AppState, navigator: Navigator, initialPoint: GeoPoint, correcting: Place?) {
    val title = if (correcting == null) "Propose a place" else "Suggest correction"
    if (!state.isSignedIn) {
        GuidePage(title, onClose = navigator::pop) { padding -> AuthForm(state, navigator, padding) }
        return
    }
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf(correcting?.location ?: initialPoint) }
    var accessPoint by remember { mutableStateOf(correcting?.accessPoint) }
    var selectingEntrance by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(correcting?.name ?: "") }
    var kind by remember { mutableStateOf(correcting?.kind ?: PlaceKind.InformalOutdoor) }
    var address by remember { mutableStateOf(correcting?.addressLabel ?: "") }
    var notes by remember { mutableStateOf(correcting?.accessNotes ?: "") }
    var insideVenue by remember { mutableStateOf(correcting?.insideVenue ?: false) }
    var basis by remember { mutableStateOf("") }
    var nearby by remember { mutableStateOf(emptyList<PlaceSummary>()) }
    var nearbyError by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var acknowledgement by remember { mutableStateOf<Acknowledgement?>(null) }
    var busy by remember { mutableStateOf(false) }
    val key = remember { IdempotencyKey() }
    val camera = remember { MapCamera(pin, 16.0) }

    val closeDuplicates = nearby.filter { it.id != correcting?.id && distanceMeters(pin, it.location) < 50 }
    val canSubmit = name.trim().length in 2..120 && basis.trim().length in 10..1000 && address.length <= 240 &&
        notes.length <= 500 && (correcting != null || closeDuplicates.isEmpty()) && !busy

    LaunchedEffect(pin, state.baseUrl) {
        nearby = emptyList(); nearbyError = null
        delay(600)
        try {
            nearby = state.api.nearby(pin).items
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            nearbyError = failure.message
        }
    }

    fun submit() = scope.launch {
        busy = true; error = null
        val payload = SubmissionRequest(
            intent = if (correcting == null) "create" else "correct",
            placeId = correcting?.id,
            name = name.trim(),
            kind = kind,
            location = pin,
            accessPoint = accessPoint,
            addressLabel = address.ifEmpty { null },
            accessNotes = notes.ifEmpty { null },
            insideVenue = if (kind == PlaceKind.VenueDesignated) insideVenue else null,
            basis = basis.trim(),
        )
        try {
            acknowledgement = state.api.submit(payload, key.forPayload(ApiJson.encodeToString(SubmissionRequest.serializer(), payload)))
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            error = if ((failure as? ApiException)?.code == "likely_duplicate") {
                "A nearby place already exists. Open that place and suggest a correction. ${failure.message}"
            } else failure.message
        }
        busy = false
    }

    val done = acknowledgement
    GuidePage(title, onClose = navigator::pop, actions = {
        if (done == null) GuideToolbarAction(if (busy) "Sending…" else "Submit", enabled = canSubmit) { submit() }
    }) { padding ->
        if (done != null) {
            GuideColumn(padding) {
                GuideCard(spacing = 18.dp) {
                    Box(Modifier.background(Guide.colors.tealPale, RoundedCornerShape(8.dp)).padding(16.dp)) {
                        Icon(Icons.Outlined.PendingActions, contentDescription = null, tint = Guide.colors.teal, modifier = Modifier.size(36.dp))
                    }
                    GuideEyebrow("Thank you for contributing")
                    GuideHeading("Proposal received.", size = 32, editorial = true)
                    GuideMuted("Your proposal will stay pending until an administrator reviews it.", 16)
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        GuideFact("Reference", done.id, Icons.Outlined.Description)
                    }
                    GuideNotice("A submission is not permission to smoke. Check the current status and access note before visiting.")
                    GuideButton(onClick = navigator::pop) { Text("Done") }
                }
            }
            return@GuidePage
        }
        GuideColumn(padding) {
            GuideEyebrow(if (correcting == null) "Share local knowledge" else "Help keep the guide current")
            GuideHeading(if (correcting == null) "What did you find?" else "What has changed?", size = 28, editorial = true)
            GuideMuted("Share what you observed. A community report does not establish permission to smoke.", 15)

            GuideCard(spacing = 10.dp) {
                Text("Pin locations", style = Guide.heading(13.sp), color = Guide.colors.muted)
                GuideMuted("Tap the map to place the ${if (selectingEntrance) "public entrance" else "reported area"} pin.", 13)
                TileMap(
                    camera = camera,
                    items = listOfNotNull(
                        MapItem("area", pin) { SelectionPin(GuideDiamond, Guide.colors.amber, Guide.colors.amberPale) },
                        accessPoint?.let { MapItem("entrance", it) { SelectionPin(Icons.Filled.DoorFront, Guide.colors.teal, Guide.colors.tealPale) } },
                    ),
                    description = "Map for selecting the reported area or public entrance pin",
                    onTap = { point -> if (selectingEntrance) accessPoint = point else pin = point },
                    modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Guide.colors.line, RoundedCornerShape(8.dp)),
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(false to "Reported area", true to "Public entrance").forEachIndexed { index, (entrance, label) ->
                        SegmentedButton(
                            selected = selectingEntrance == entrance,
                            onClick = { selectingEntrance = entrance },
                            shape = SegmentedButtonDefaults.itemShape(index, 2),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = Guide.colors.ink, activeContentColor = Guide.colors.paper,
                                inactiveContainerColor = Guide.colors.surface, inactiveContentColor = Guide.colors.ink,
                            ),
                            icon = {},
                        ) { Text(label, style = Guide.heading(13.sp)) }
                    }
                }
                if (accessPoint != null) TextButton(onClick = { accessPoint = null }) { Text("Remove entrance pin") }
                Text(
                    "Area: %.5f, %.5f".format(java.util.Locale.US, pin.lat, pin.lon),
                    style = Guide.body(12.sp), color = Guide.colors.muted,
                )
            }

            GuideCard(spacing = 12.dp) {
                Text("Place", style = Guide.heading(13.sp), color = Guide.colors.muted)
                GuideTextField(name, { name = it }, "Name or landmark", limit = 120,
                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words)
                Text("Type", style = Guide.heading(13.sp), color = Guide.colors.muted)
                ChoiceGroup(PlaceKind.entries.map { it to it.title }, kind) { kind = it }
                if (kind == PlaceKind.VenueDesignated) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .toggleable(insideVenue, role = Role.Switch) { insideVenue = it },
                    ) {
                        Text("Inside establishment", style = Guide.body(16.sp), modifier = Modifier.weight(1f))
                        Switch(insideVenue, onCheckedChange = null,
                            colors = SwitchDefaults.colors(checkedTrackColor = Guide.colors.tealInk))
                    }
                }
                GuideTextField(address, { address = it }, "Address label (optional)", limit = 240)
                GuideTextField(notes, { notes = it }, "Access note (optional)", singleLine = false, minLines = 2, limit = 500)
                GuideTextField(basis, { basis = it }, "How do you know about this place?", singleLine = false, minLines = 3, limit = 1000)
                GuideMuted("Every proposal stays pending until an administrator reviews it. You cannot set evidence-reviewed designation.", 13)
            }

            GuideCard(spacing = 10.dp) {
                Text("Nearby possible duplicates", style = Guide.heading(13.sp), color = Guide.colors.muted)
                val nearbyFailure = nearbyError
                when {
                    nearbyFailure != null -> GuideMuted(nearbyFailure, 13)
                    nearby.isEmpty() -> GuideMuted("No published places found within 200 m.", 15)
                }
                nearby.forEach { candidate ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(candidate.kind.icon, contentDescription = null, tint = Guide.colors.muted, modifier = Modifier.size(18.dp))
                        Text(candidate.name, style = Guide.body(15.sp), color = Guide.colors.ink)
                    }
                }
                if (closeDuplicates.isNotEmpty() && correcting == null) {
                    GuideNotice("A place is within 50 m. Open that place and suggest a correction instead.", GuideDiamond, warning = true)
                }
            }
            error?.let { GuideNotice(it, Icons.Outlined.Warning, warning = true) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SelectionPin(icon: ImageVector, color: Color, background: Color) {
    Box(
        Modifier.size(40.dp).background(background, CircleShape).border(2.dp, color, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp)) }
}
