package com.orcasolutions.iyosi.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.orcasolutions.iyosi.AppState
import com.orcasolutions.iyosi.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WelcomeScreen(state: AppState, onPrivacy: () -> Unit) {
    val colors = Guide.colors
    Column(Modifier.fillMaxSize().background(colors.paper)) {
        Box(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).statusBarsPadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth().padding(24.dp),
            ) {
                GuideWordmark(size = 34.sp)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GuideEyebrow("A Metro Manila place guide")
                    Column(Modifier.semantics(mergeDescendants = true) {}) {
                        Text("Find the place.", style = Guide.heading(38.sp, tracking = (-0.04).em), color = colors.ink)
                        Text("Check the status.", style = Guide.editorial(36.sp), color = colors.teal)
                    }
                    Text(
                        "A guide to designated smoking areas and community reports, with context before you make the trip.",
                        style = Guide.body(17.sp), color = colors.muted,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Image(
                        painterResource(R.drawable.city_guide_illustration),
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)),
                    )
                    Text(
                        "ILLUSTRATED CITY BLOCK · FICTIONAL",
                        style = Guide.heading(9.sp, tracking = 0.13.em), color = colors.muted,
                    )
                }
                GuideCard(spacing = 16.dp) {
                    GuideEyebrow("Every place, in context")
                    GuideFact("Status", "Reviewed evidence and unverified reports get different labels.", Icons.Outlined.Verified)
                    GuideFact("Latest check", "Read the last confirmation and access note before directions.", Icons.Outlined.Schedule)
                }
                GuideMuted("Respect smoke-free rules and shared air. A report is not permission to smoke. Check posted signs, venue rules, and local law.")
                GuideMuted("For adults 18 and older. Browse without an account or location access. Location is requested only when you choose Near me; an account is needed to contribute.")
            }
        }
        HorizontalDivider(color = colors.line)
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().background(colors.surface).navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
        ) {
            if (state.adultAccessDeclined) {
                GuideNotice("You must be at least 18 to use iYosi.", Icons.Filled.PersonOff, warning = true)
            } else {
                GuideButton(onClick = state::acceptAdultAccess, modifier = Modifier.fillMaxWidth()) {
                    Text("I am 18 or older — continue", modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (!state.adultAccessDeclined) {
                    TextButton(onClick = state::declineAdultAccess, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("I am under 18", style = Guide.body(13.sp), color = colors.muted)
                    }
                }
                TextButton(onClick = onPrivacy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Privacy notice", style = Guide.body(13.sp), color = colors.tealInk)
                }
            }
        }
    }
}

@Composable
fun PrivacyNoticeScreen(onBack: () -> Unit) {
    GuidePage("Privacy", onClose = onBack, closeIsBack = true) { padding ->
        GuideColumn(padding, spacing = 14) {
            GuideEyebrow("Consideration for shared air")
            GuideHeading("Your information.", size = 30)
            Text("Pilot privacy notice", style = Guide.editorial(25.sp), color = Guide.colors.teal)
            val body = Guide.body(16.sp)
            Text("You can browse without signing in. When you choose Near me, Android supplies a foreground location to center the map. The API does not receive a background location trail. Address searches and visible map bounds are sent to the API to return results.", style = body)
            Text("If you create an account, the service stores your email, password credential, adult self-attestation, and your submitted places, confirmations, reviews, and reports. Public place and review responses do not include your account identity. Reports and moderation records are available to administrators.", style = body)
            Text("The app keeps your short-lived sign-in token encrypted with the Android Keystore. You can request account deletion in Account. This pilot notice does not yet set a retention schedule or operator contact; those details must be published before a public release.", style = body)
            Text("Map imagery comes from the configured tile provider (OpenStreetMap by default), which receives requests for the visible map tiles and your IP address under its own policies.", style = body)
            Spacer(Modifier.height(8.dp))
        }
    }
}
