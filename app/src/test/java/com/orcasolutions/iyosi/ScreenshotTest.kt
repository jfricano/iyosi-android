package com.orcasolutions.iyosi

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.orcasolutions.iyosi.data.GeoPoint
import com.orcasolutions.iyosi.ui.AccountScreen
import com.orcasolutions.iyosi.ui.GuideTheme
import com.orcasolutions.iyosi.ui.MapHomeScreen
import com.orcasolutions.iyosi.ui.Navigator
import com.orcasolutions.iyosi.ui.PlaceDetailScreen
import com.orcasolutions.iyosi.ui.Route
import com.orcasolutions.iyosi.ui.SubmissionScreen
import com.orcasolutions.iyosi.ui.WelcomeScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders key screens with fictional fixtures for visual review. Images land in app/build/outputs/roborazzi. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h880dp-xhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun capture(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { GuideTheme(dark = dark) { content() } }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test fun welcome() = capture("welcome") { WelcomeScreen(Fixtures.state(adult = false), onPrivacy = {}) }

    @Test fun list() = capture("list") { MapHomeScreen(Fixtures.state(), Navigator(), initiallyShowingList = true) }

    @Test fun map() = capture("map") { MapHomeScreen(Fixtures.state(), Navigator()) }

    @Test fun reviewedDetail() = detail("reviewed", "detail-reviewed")

    @Test fun communityDetail() = detail("community", "detail-community")

    @Test fun prohibitedDetailDark() = detail("prohibited", "detail-prohibited-dark", dark = true)

    @Test fun submission() = capture("submission") {
        SubmissionScreen(Fixtures.state(signedIn = true), Navigator(), GeoPoint(14.594, 120.985), null)
    }

    @Test fun account() = capture("account") {
        AccountScreen(Fixtures.state(signedIn = true), Navigator())
    }

    @Test fun signIn() = capture("sign-in") { AccountScreen(Fixtures.state(), Navigator()) }

    private fun detail(id: String, name: String, dark: Boolean = false) {
        val navigator = Navigator().apply { push(Route.Detail(id)) }
        capture(name, dark) { PlaceDetailScreen(id, Fixtures.state(), navigator) }
    }
}
