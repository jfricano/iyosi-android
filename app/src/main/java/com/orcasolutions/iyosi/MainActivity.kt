package com.orcasolutions.iyosi

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import com.orcasolutions.iyosi.ui.IYosiApp
import com.orcasolutions.iyosi.ui.GuideTheme

class MainActivity : ComponentActivity() {
    private val model: AppViewModel by viewModels()
    private val deepLinkPlace = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) deepLinkPlace.value = placeId(intent)
        setContent {
            GuideTheme {
                IYosiApp(
                    state = model.state,
                    deepLinkPlace = deepLinkPlace.value,
                    onDeepLinkHandled = { deepLinkPlace.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        placeId(intent)?.let { deepLinkPlace.value = it }
    }

    /** `iyosi://places/PLACE_ID` opens a place directly. */
    private fun placeId(intent: Intent?): String? {
        val uri = intent?.data ?: return null
        if (uri.scheme != "iyosi" || uri.host != "places") return null
        return uri.pathSegments.singleOrNull()?.takeIf { it.isNotBlank() }
    }
}
