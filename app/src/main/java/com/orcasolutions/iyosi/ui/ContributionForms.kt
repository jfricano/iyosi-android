package com.orcasolutions.iyosi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orcasolutions.iyosi.AppState
import com.orcasolutions.iyosi.data.ApiJson
import com.orcasolutions.iyosi.data.ConfirmationRequest
import com.orcasolutions.iyosi.data.DateText
import com.orcasolutions.iyosi.data.ReportRequest
import com.orcasolutions.iyosi.data.ReviewRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Keeps one idempotency key while the payload stays the same, so a retry after a timeout
 * cannot create a duplicate, and issues a new key once the person edits the payload.
 */
class IdempotencyKey {
    private var key = UUID.randomUUID().toString()
    private var lastPayload: String? = null
    fun forPayload(payload: String): String {
        if (payload != lastPayload) {
            key = UUID.randomUUID().toString()
            lastPayload = payload
        }
        return key
    }
}

@Composable
fun AuthRequired(state: AppState, navigator: Navigator, content: @Composable () -> Unit) {
    if (state.isSignedIn) content()
    else GuidePage("Sign in to contribute", onClose = navigator::pop) { padding -> AuthForm(state, navigator, padding) }
}

@Composable
fun <T> ChoiceGroup(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val colors = Guide.colors
    androidx.compose.foundation.layout.Column(Modifier.selectableGroup()) {
        options.forEach { (value, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .selectable(selected = value == selected, role = Role.RadioButton) { onSelect(value) },
            ) {
                RadioButton(
                    selected = value == selected, onClick = null,
                    colors = RadioButtonDefaults.colors(selectedColor = colors.tealInk, unselectedColor = colors.muted),
                )
                Text(label, style = Guide.body(16.sp), color = colors.ink, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}

@Composable
private fun Receipt(title: String, message: String, onDone: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDone,
        confirmButton = { TextButton(onClick = onDone) { Text("Done") } },
        title = { Text(title) },
        text = { Text(message) },
        containerColor = Guide.colors.surface,
    )
}

@Composable
private fun FormError(error: String?) {
    error?.let { GuideNotice(it, Icons.Outlined.Warning, warning = true) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmationForm(placeId: String, state: AppState, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var observation by remember { mutableStateOf("still_there") }
    var notes by remember { mutableStateOf("") }
    // Fixed when chosen, so a retried send reuses the same payload and idempotency key.
    var observedAt by remember { mutableStateOf(Instant.now().truncatedTo(ChronoUnit.MINUTES)) }
    var choosingDate by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var receipt by remember { mutableStateOf<String?>(null) }
    val key = remember { IdempotencyKey() }

    fun send() = scope.launch {
        busy = true; error = null
        try {
            val payload = ConfirmationRequest(observation, observedAt.toString(), notes.ifEmpty { null })
            receipt = state.api.confirm(placeId, payload, key.forPayload(ApiJson.encodeToString(ConfirmationRequest.serializer(), payload))).id
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            error = failure.message
        }
        busy = false
    }

    GuidePage("Confirm place", onClose = onDone, actions = {
        GuideToolbarAction(if (busy) "Sending…" else "Send", enabled = !busy && notes.length <= 500) { send() }
    }) { padding ->
        GuideColumn(padding) {
            GuideEyebrow("Community observation")
            GuideHeading("What did you see?", editorial = true)
            GuideNotice("Confirmations describe existence and access, not permission or quality.")
            GuideCard(spacing = 6.dp) {
                Text("Your observation", style = Guide.heading(13.sp), color = Guide.colors.muted)
                ChoiceGroup(listOf("still_there" to "Still there", "changed" to "Changed", "gone" to "Gone"), observation) { observation = it }
            }
            GuideCard(spacing = 6.dp) {
                Text("Observed", style = Guide.heading(13.sp), color = Guide.colors.muted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(DateText.display(observedAt.toString()), style = Guide.body(16.sp), color = Guide.colors.ink, modifier = Modifier.weight(1f))
                    TextButton(onClick = { choosingDate = true }) { Text("Change date") }
                }
            }
            GuideTextField(notes, { notes = it }, "Suggested access-note correction (optional)", singleLine = false, minLines = 3, limit = 500)
            FormError(error)
        }
    }
    if (choosingDate) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        // The server accepts observations from the last 30 days to the instant; a noon time on the
        // 30th day back can fall outside it, so the oldest selectable day is 29 days ago.
        val earliest = today.minusDays(29)
        // The Material date picker works in UTC milliseconds at midnight.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = observedAt.atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val date = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    return !date.isBefore(earliest) && !date.isAfter(today)
                }
                override fun isSelectableYear(year: Int) = year in earliest.year..today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { choosingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        observedAt = if (date == today) Instant.now().truncatedTo(ChronoUnit.MINUTES)
                        else date.atTime(12, 0).atZone(zone).toInstant()
                    }
                    choosingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { choosingDate = false }) { Text("Cancel") } },
        ) { DatePicker(pickerState) }
    }
    receipt?.let { Receipt("Confirmation recorded", "Reference $it. This does not change the place's advisory status.", onDone) }
}

@Composable
fun ReviewForm(placeId: String, state: AppState, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var stars by remember { mutableIntStateOf(3) }
    var body by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun save() = scope.launch {
        busy = true; error = null
        try {
            state.api.review(placeId, ReviewRequest(stars, body.ifEmpty { null }))
            onDone()
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            error = failure.message
        }
        busy = false
    }

    GuidePage("Rate place", onClose = onDone, actions = {
        GuideToolbarAction(if (busy) "Saving…" else "Save", enabled = !busy && body.length <= 1000) { save() }
    }) { padding ->
        GuideColumn(padding) {
            GuideEyebrow("Community experience")
            GuideHeading("Share your experience.", editorial = true)
            GuideNotice("Your rating is separate from confirmations and evidence review. A rating does not establish permission.")
            GuideCard(spacing = 6.dp) {
                Text("Stars", style = Guide.heading(13.sp), color = Guide.colors.muted)
                ChoiceGroup((1..5).map { it to if (it == 1) "1 star" else "$it stars" }, stars) { stars = it }
            }
            GuideTextField(body, { body = it }, "Your experience (optional)", singleLine = false, minLines = 4, limit = 1000)
            FormError(error)
        }
    }
}

@Composable
fun ReportForm(targetType: String, targetId: String, state: AppState, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var reason by remember { mutableStateOf("wrong_status") }
    var detail by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var receipt by remember { mutableStateOf<String?>(null) }
    val key = remember { IdempotencyKey() }
    val reasons = listOf(
        "wrong_status" to "Wrong status", "closed" to "Closed", "access_notes" to "Access notes", "privacy" to "Privacy",
        "harassment" to "Harassment", "spam" to "Spam", "other" to "Other",
    )

    fun send() = scope.launch {
        busy = true; error = null
        try {
            val payload = ReportRequest(targetType, targetId, reason, detail.ifEmpty { null })
            receipt = state.api.report(payload, key.forPayload(ApiJson.encodeToString(ReportRequest.serializer(), payload))).id
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            error = failure.message
        }
        busy = false
    }

    GuidePage("Report $targetType", onClose = onDone, actions = {
        GuideToolbarAction(if (busy) "Sending…" else "Send", enabled = !busy && detail.length <= 1000) { send() }
    }) { padding ->
        GuideColumn(padding) {
            GuideEyebrow("Guide moderation")
            GuideHeading("Help us check an issue.", editorial = true)
            GuideNotice("Reports go to moderation. Your identity is not shown publicly.", Icons.Outlined.VerifiedUser)
            GuideCard(spacing = 6.dp) {
                Text("Reason", style = Guide.heading(13.sp), color = Guide.colors.muted)
                ChoiceGroup(reasons, reason) { reason = it }
            }
            GuideTextField(detail, { detail = it }, "Details (optional)", singleLine = false, minLines = 4, limit = 1000)
            FormError(error)
        }
    }
    receipt?.let { Receipt("Report received", "Reference $it. Your report is in moderation.", onDone) }
}
