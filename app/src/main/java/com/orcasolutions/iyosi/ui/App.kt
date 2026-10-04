package com.orcasolutions.iyosi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.orcasolutions.iyosi.AppState
import com.orcasolutions.iyosi.data.GeoPoint
import com.orcasolutions.iyosi.data.Place

/** Screens pushed above the map. The map stays composed underneath so its camera and filters survive. */
sealed interface Route {
    data class Detail(val placeId: String) : Route
    data class Submit(val initialPoint: GeoPoint, val correcting: Place? = null) : Route
    data object Account : Route
    data object Privacy : Route
    data object ServerSettings : Route
    data class Confirm(val placeId: String) : Route
    data class Review(val placeId: String) : Route
    data class Report(val targetType: String, val targetId: String) : Route
}

class Navigator {
    val stack = mutableStateListOf<Route>()
    fun push(route: Route) { stack += route }
    fun pop() { stack.removeLastOrNull() }
}

@Composable
fun IYosiApp(state: AppState, deepLinkPlace: String?, onDeepLinkHandled: () -> Unit) {
    val navigator = remember { Navigator() }
    LaunchedEffect(state.token) { state.refreshAccount() }

    if (!state.adultAccessAccepted) {
        val privacyOpen = navigator.stack.lastOrNull() == Route.Privacy
        if (privacyOpen) {
            BackHandler { navigator.pop() }
            PrivacyNoticeScreen(onBack = navigator::pop)
        } else {
            WelcomeScreen(state, onPrivacy = { navigator.push(Route.Privacy) })
        }
        return
    }

    LaunchedEffect(deepLinkPlace) {
        if (deepLinkPlace != null) {
            navigator.push(Route.Detail(deepLinkPlace))
            onDeepLinkHandled()
        }
    }

    Box(Modifier.fillMaxSize()) {
        val covered = navigator.stack.isNotEmpty()
        Box(if (covered) Modifier.clearAndSetSemantics {} else Modifier) {
            MapHomeScreen(state, navigator)
        }
        navigator.stack.forEachIndexed { index, route ->
            val top = index == navigator.stack.lastIndex
            Surface(
                modifier = Modifier.fillMaxSize().then(if (top) Modifier else Modifier.clearAndSetSemantics {}),
                color = Guide.colors.paper,
            ) { RouteScreen(route, state, navigator) }
        }
        if (covered) BackHandler { navigator.pop() }
    }
}

@Composable
private fun RouteScreen(route: Route, state: AppState, navigator: Navigator) {
    when (route) {
        is Route.Detail -> PlaceDetailScreen(route.placeId, state, navigator)
        is Route.Submit -> SubmissionScreen(state, navigator, route.initialPoint, route.correcting)
        Route.Account -> AccountScreen(state, navigator)
        Route.Privacy -> PrivacyNoticeScreen(onBack = navigator::pop)
        Route.ServerSettings -> ServerSettingsScreen(state, onBack = navigator::pop)
        is Route.Confirm -> AuthRequired(state, navigator) { ConfirmationForm(route.placeId, state, navigator::pop) }
        is Route.Review -> AuthRequired(state, navigator) { ReviewForm(route.placeId, state, navigator::pop) }
        is Route.Report -> AuthRequired(state, navigator) {
            ReportForm(route.targetType, route.targetId, state, navigator::pop)
        }
    }
}
