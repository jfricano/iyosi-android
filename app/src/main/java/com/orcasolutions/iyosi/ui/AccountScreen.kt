package com.orcasolutions.iyosi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orcasolutions.iyosi.AppState
import com.orcasolutions.iyosi.data.DateText
import com.orcasolutions.iyosi.data.SubmissionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Sign-in and registration, shown wherever a contribution needs an account. */
@Composable
fun AuthForm(state: AppState, navigator: Navigator, padding: PaddingValues) {
    val scope = rememberCoroutineScope()
    var registering by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var adultAttested by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val colors = Guide.colors
    val canSubmit = !busy && email.trim().length >= 5 && password.isNotEmpty() &&
        (!registering || (adultAttested && password.length in 12..128))

    GuideColumn(padding) {
        GuideEyebrow("A practical city guide")
        GuideHeading(if (registering) "Help keep the guide current." else "A little local knowledge goes a long way.", size = 28, editorial = true)
        GuideMuted("Browse places without an account. Sign in to share a report, review, or correction.", 15)
        GuideCard(spacing = 12.dp) {
            Text(if (registering) "Create an adult account" else "Sign in to contribute", style = Guide.heading(13.sp), color = colors.muted)
            GuideTextField(email, { email = it }, "Email", keyboardType = KeyboardType.Email, capitalization = KeyboardCapitalization.None)
            GuideTextField(password, { password = it }, "Password", password = true)
            if (registering) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .toggleable(adultAttested, role = Role.Checkbox) { adultAttested = it },
                ) {
                    Checkbox(adultAttested, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = colors.tealInk))
                    Spacer(Modifier.size(12.dp))
                    Text("I am 18 or older", style = Guide.body(16.sp))
                }
                GuideMuted("Use a password of 12–128 characters. Age self-attestation is used for the private pilot.", 13)
            }
            GuideButton(
                onClick = {
                    scope.launch {
                        busy = true; error = null
                        try {
                            state.authenticate(email.trim(), password, registering)
                        } catch (failure: CancellationException) {
                            throw failure
                        } catch (failure: Exception) {
                            error = failure.message
                        }
                        busy = false
                    }
                },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.paper)
                    Spacer(Modifier.size(8.dp))
                }
                Text(if (registering) "Create account" else "Sign in")
            }
        }
        GuideCard(spacing = 0.dp) {
            TextButton(onClick = { registering = !registering; error = null }) {
                Text(if (registering) "Already have an account? Sign in" else "Create an account")
            }
            TextButton(onClick = { navigator.push(Route.ServerSettings) }) { Text("API server settings") }
        }
        error?.let { GuideNotice(it, Icons.Outlined.Warning, warning = true) }
    }
}

@Composable
fun AccountScreen(state: AppState, navigator: Navigator) {
    if (!state.isSignedIn) {
        GuidePage("Account", onClose = navigator::pop) { padding -> AuthForm(state, navigator, padding) }
        return
    }
    val scope = rememberCoroutineScope()
    val colors = Guide.colors
    var submissions by remember { mutableStateOf(emptyList<SubmissionStatus>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var deletionRequested by remember { mutableStateOf(false) }
    var confirmingDeletion by remember { mutableStateOf(false) }

    LaunchedEffect(state.token) {
        try {
            submissions = state.api.submissions()
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            error = failure.message
        }
    }

    GuidePage("Account", onClose = navigator::pop) { padding ->
        GuideColumn(padding) {
            GuideCard(spacing = 10.dp) {
                GuideEyebrow("Your account")
                GuideHeading("Local knowledge. Shared with care.", size = 27, editorial = true)
                val account = state.account
                val accountError = state.accountError
                if (account != null) {
                    SelectionContainer { Text(account.email, style = Guide.body(16.sp), color = colors.ink) }
                    GuideMuted("Role: ${account.role}", 13)
                } else if (accountError != null) {
                    GuideNotice(accountError, Icons.Outlined.Warning, warning = true)
                }
                TextButton(onClick = state::signOut) { Text("Sign out") }
            }
            GuideCard(spacing = 10.dp) {
                Text("Your proposals", style = Guide.heading(13.sp), color = colors.muted)
                if (submissions.isEmpty()) GuideMuted("No proposals yet.", 16)
                submissions.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(color = colors.line)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(item.name, style = Guide.heading(17.sp), color = colors.ink)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Description, contentDescription = null, tint = colors.muted, modifier = Modifier.size(16.dp))
                            GuideMuted("${item.intent.capitalized()} · ${item.state.capitalized()}")
                        }
                        GuideMuted(DateText.display(item.createdAt), 12)
                        item.decisionReason?.let { Text(it, style = Guide.body(13.sp), color = colors.ink) }
                    }
                }
            }
            GuideCard(spacing = 4.dp) {
                Text("Privacy", style = Guide.heading(13.sp), color = colors.muted)
                TextButton(onClick = { navigator.push(Route.Privacy) }) { Text("Privacy notice") }
                if (deletionRequested) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = colors.teal)
                        Text("Deletion request received.", color = colors.teal)
                    }
                } else {
                    TextButton(onClick = { confirmingDeletion = true }) {
                        Text("Request account deletion", color = colors.warning)
                    }
                }
            }
            GuideCard(spacing = 4.dp) {
                Text("API server", style = Guide.heading(13.sp), color = colors.muted)
                TextButton(onClick = { navigator.push(Route.ServerSettings) }) { Text("Server settings") }
            }
            error?.let { GuideNotice(it, Icons.Outlined.Warning, warning = true) }
        }
    }

    if (confirmingDeletion) {
        AlertDialog(
            onDismissRequest = { confirmingDeletion = false },
            title = { Text("Request account deletion?") },
            text = { Text("An administrator will review your request.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDeletion = false
                    scope.launch {
                        try {
                            state.api.deletionRequest()
                            deletionRequested = true
                        } catch (failure: CancellationException) {
                            throw failure
                        } catch (failure: Exception) {
                            error = failure.message
                        }
                    }
                }) { Text("Submit deletion request", color = colors.warning) }
            },
            dismissButton = { TextButton(onClick = { confirmingDeletion = false }) { Text("Cancel") } },
            containerColor = colors.surface,
        )
    }
}

private fun String.capitalized() = replaceFirstChar { it.titlecase() }

@Composable
fun ServerSettingsScreen(state: AppState, onBack: () -> Unit) {
    var draft by remember { mutableStateOf(state.baseUrl) }
    var message by remember { mutableStateOf<String?>(null) }
    GuidePage("API server", onClose = onBack, closeIsBack = true) { padding ->
        GuideColumn(padding) {
            GuideCard(spacing = 12.dp) {
                Text("Base URL", style = Guide.heading(13.sp), color = Guide.colors.muted)
                GuideTextField(draft, { draft = it; message = null }, "https://api.example.com",
                    keyboardType = KeyboardType.Uri, capitalization = KeyboardCapitalization.None)
                GuideButton(
                    onClick = {
                        message = if (state.updateBaseUrl(draft)) "Saved. Changing servers signs you out." else com.orcasolutions.iyosi.data.ApiClient.INVALID_SERVER
                    },
                    enabled = draft.isNotBlank(),
                ) { Text("Save server") }
                GuideMuted(message ?: "Changing servers signs you out.", 13)
            }
            GuideNotice(
                "Use http://10.0.2.2:8000 in the Android emulator with the local Compose API. Use an HTTPS URL for a remote server.",
                Icons.Outlined.Lan,
            )
        }
    }
}
