package com.orcasolutions.iyosi.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.core.content.ContextCompat
import com.orcasolutions.iyosi.AppState
import com.orcasolutions.iyosi.ForegroundLocation
import com.orcasolutions.iyosi.data.AddressSuggestion
import com.orcasolutions.iyosi.data.AdvisoryStatus
import com.orcasolutions.iyosi.data.ApiException
import com.orcasolutions.iyosi.data.DateText
import com.orcasolutions.iyosi.data.GeoPoint
import com.orcasolutions.iyosi.data.Place
import com.orcasolutions.iyosi.data.PlaceKind
import com.orcasolutions.iyosi.data.PlaceSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.max

private const val NEAR_ZOOM = 15.0

data class MapCluster(val id: String, val point: GeoPoint, val places: List<PlaceSummary>)

/** Simple viewport-grid clustering, matching the iOS client's behavior. */
fun cluster(places: List<PlaceSummary>, bounds: MapBounds): List<MapCluster> {
    val cell = max(bounds.latSpan, bounds.lonSpan) / 8
    if (cell <= 0.005) return places.map { MapCluster(it.id, it.location, listOf(it)) }
    return places.groupBy { "${floor(it.location.lat / cell).toInt()}:${floor(it.location.lon / cell).toInt()}" }
        .map { (key, items) ->
            val point = GeoPoint(items.sumOf { it.location.lat } / items.size, items.sumOf { it.location.lon } / items.size)
            MapCluster(if (items.size == 1) items[0].id else "cluster-$key", point, items)
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapHomeScreen(state: AppState, navigator: Navigator, initiallyShowingList: Boolean = false) {
    val colors = Guide.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val camera = remember { MapCamera(MapCamera.MetroManila, 12.0) }
    var bounds by remember { mutableStateOf<MapBounds?>(null) }
    var places by remember { mutableStateOf(emptyList<PlaceSummary>()) }
    var selected by remember { mutableStateOf<PlaceSummary?>(null) }
    var showingList by remember { mutableStateOf(initiallyShowingList) }
    var showingFilters by remember { mutableStateOf(false) }
    var showingLegend by remember { mutableStateOf(false) }
    var kinds by remember { mutableStateOf(PlaceKind.entries.toSet()) }
    var statuses by remember {
        mutableStateOf(setOf(AdvisoryStatus.VerifiedDesignated, AdvisoryStatus.CommunityReported, AdvisoryStatus.Disputed))
    }
    var search by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf(emptyList<AddressSuggestion>()) }
    var suggestionProvider by remember { mutableStateOf<String?>(null) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var mapError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var moreAvailable by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }

    val includeProhibited = AdvisoryStatus.Prohibited in statuses
    val visiblePlaces = places.filter { it.kind in kinds && it.advisoryStatus in statuses }
    val zoomLimited = bounds?.let { it.latSpan > 0.5 || it.lonSpan > 0.5 } ?: false

    LaunchedEffect(state.baseUrl, bounds?.apiBbox(), includeProhibited, reload) {
        val viewport = bounds ?: return@LaunchedEffect
        loading = true
        mapError = null
        try {
            val all = mutableListOf<PlaceSummary>()
            var cursor: String? = null
            var hasMore = false
            repeat(5) {
                if (it > 0 && cursor == null) return@repeat
                val result = state.api.places(viewport.apiBbox(), cursor, includeProhibited)
                all += result.items
                cursor = result.nextCursor
                hasMore = cursor != null
            }
            places = all
            moreAvailable = hasMore
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            mapError = error.message
            places = emptyList()
        }
        loading = false
    }

    LaunchedEffect(search) {
        suggestions = emptyList(); suggestionProvider = null; searchError = null
        val query = search.trim()
        if (query.length < 3) return@LaunchedEffect
        delay(350)
        try {
            val result = state.api.suggestions(query)
            suggestions = result.items
            suggestionProvider = result.provider
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            searchError = if ((error as? ApiException)?.code == "geocoder_unavailable") "Address search is not configured on this server." else error.message
        }
    }

    fun locate() {
        searchError = null
        ForegroundLocation.request(context) { point ->
            if (point == null) searchError = "Location is unavailable. Search and map browsing still work."
            else {
                showingList = false
                camera.moveTo(point, NEAR_ZOOM)
            }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) locate()
        else searchError = "Location access is off. Search and map browsing still work."
    }
    fun nearMe() {
        val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (granted) locate()
        else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    Column(Modifier.fillMaxSize().background(colors.paper)) {
        Header(
            search = search,
            onSearch = { search = it },
            searchError = searchError,
            suggestions = suggestions,
            provider = suggestionProvider,
            onSuggestion = {
                showingList = false
                camera.moveTo(it.location, NEAR_ZOOM)
                search = ""
            },
            showingList = showingList,
            onShowList = { showingList = it },
            onFilters = { showingFilters = true },
            onAccount = { navigator.push(Route.Account) },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (showingList) {
                PlaceList(visiblePlaces) { selected = it }
            } else {
                val clusters = bounds?.let { cluster(visiblePlaces, it) } ?: visiblePlaces.map { MapCluster(it.id, it.location, listOf(it)) }
                TileMap(
                    camera = camera,
                    items = clusters.map { group ->
                        MapItem(group.id, group.point) {
                            if (group.places.size == 1) {
                                PlaceMarker(group.places[0]) { selected = group.places[0] }
                            } else {
                                ClusterMarker(group.places.size) {
                                    camera.moveTo(group.point, camera.zoom + 1)
                                }
                            }
                        }
                    },
                    description = "Map of Metro Manila. Drag to pan, pinch to zoom. Places are also available in the List view.",
                    onViewportSettled = { bounds = it },
                    modifier = Modifier.fillMaxSize(),
                )
                IconButton(
                    onClick = ::nearMe,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).size(48.dp)
                        .background(colors.surface, RoundedCornerShape(9.dp))
                        .border(1.dp, colors.line, RoundedCornerShape(9.dp)),
                ) { Icon(Icons.Filled.MyLocation, "Near me, use my location", tint = colors.tealInk) }
            }
            if (loading) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(14.dp).background(colors.surface, RoundedCornerShape(8.dp)).padding(12.dp)
                        .semantics(mergeDescendants = true) { contentDescription = "Loading places" },
                ) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.tealInk)
                    Text("Loading places…", style = Guide.body(13.sp))
                }
            }
        }
        BottomBar(
            mapError = mapError,
            zoomLimited = zoomLimited,
            empty = !loading && visiblePlaces.isEmpty() && bounds != null,
            moreAvailable = moreAvailable,
            count = places.size,
            onRetry = { reload++ },
            onLegend = { showingLegend = true },
            onAdd = { navigator.push(Route.Submit(camera.center)) },
        )
    }

    selected?.let { summary ->
        ModalBottomSheet(
            onDismissRequest = { selected = null },
            sheetState = rememberModalBottomSheetState(),
            containerColor = colors.surface,
        ) {
            PlacePeek(summary, state, onClose = { selected = null }) {
                selected = null
                navigator.push(Route.Detail(summary.id))
            }
        }
    }
    if (showingFilters) {
        ModalBottomSheet(onDismissRequest = { showingFilters = false }, containerColor = colors.paper) {
            FiltersSheet(
                kinds = kinds, onKinds = { kinds = it },
                statuses = statuses, onStatuses = { statuses = it },
            )
        }
    }
    if (showingLegend) {
        ModalBottomSheet(onDismissRequest = { showingLegend = false }, containerColor = colors.paper) { LegendSheet() }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun Header(
    search: String,
    onSearch: (String) -> Unit,
    searchError: String?,
    suggestions: List<AddressSuggestion>,
    provider: String?,
    onSuggestion: (AddressSuggestion) -> Unit,
    showingList: Boolean,
    onShowList: (Boolean) -> Unit,
    onFilters: () -> Unit,
    onAccount: () -> Unit,
) {
    val colors = Guide.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.statusBarsPadding().padding(start = 20.dp, end = 12.dp, bottom = 12.dp, top = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GuideWordmark(size = 29.sp, showPin = false)
            Spacer(Modifier.weight(1f))
            Text("Metro Manila", style = Guide.body(13.sp), color = colors.muted)
            IconButton(onClick = onAccount, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.AccountCircle, "Account", tint = colors.tealInk, modifier = Modifier.size(26.dp))
            }
        }
        Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(
                value = search,
                onValueChange = onSearch,
                placeholder = { Text("Search an area or landmark", style = Guide.body(15.sp)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = colors.teal) },
                trailingIcon = {
                    if (search.isNotEmpty()) {
                        IconButton(onClick = { onSearch("") }) { Icon(Icons.Filled.Cancel, "Clear search", tint = colors.muted) }
                    }
                },
                singleLine = true,
                textStyle = Guide.body(15.sp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, autoCorrectEnabled = false),
                shape = RoundedCornerShape(7.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colors.surface, unfocusedContainerColor = colors.surface,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    cursorColor = colors.tealInk, focusedTextColor = colors.ink, unfocusedTextColor = colors.ink,
                    focusedPlaceholderColor = colors.muted, unfocusedPlaceholderColor = colors.muted,
                ),
                modifier = Modifier.fillMaxWidth().border(1.dp, colors.line, RoundedCornerShape(7.dp))
                    .semantics { contentDescription = "Search Metro Manila area or landmark" },
            )
            searchError?.let { Text(it, style = Guide.body(13.sp), color = colors.warning) }
            if (suggestions.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(7.dp)).padding(12.dp)) {
                    suggestions.forEach { suggestion ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onSuggestion(suggestion) },
                        ) {
                            Icon(Icons.Filled.Place, contentDescription = null, tint = colors.tealInk, modifier = Modifier.size(18.dp))
                            Text(suggestion.label, style = Guide.body(14.sp), color = colors.ink)
                        }
                    }
                    if (provider == "geoapify") {
                        val uri = LocalUriHandler.current
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                            Text("Powered by Geoapify", style = Guide.body(11.sp), color = colors.tealInk,
                                modifier = Modifier.clickable { uri.openUri("https://www.geoapify.com/") })
                            Text("© OpenStreetMap contributors", style = Guide.body(11.sp), color = colors.tealInk,
                                modifier = Modifier.clickable { uri.openUri("https://www.openstreetmap.org/copyright") })
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SingleChoiceSegmentedButtonRow(Modifier.widthIn(max = 200.dp)) {
                    listOf(false to "Map", true to "List").forEachIndexed { index, (list, label) ->
                        SegmentedButton(
                            selected = showingList == list,
                            onClick = { onShowList(list) },
                            shape = SegmentedButtonDefaults.itemShape(index, 2),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = colors.ink, activeContentColor = colors.paper,
                                inactiveContainerColor = colors.surface, inactiveContentColor = colors.ink,
                                activeBorderColor = colors.line, inactiveBorderColor = colors.line,
                            ),
                            icon = {},
                        ) { Text(label, style = Guide.heading(13.sp)) }
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onFilters, modifier = Modifier.heightIn(min = 48.dp)
                    .semantics { contentDescription = "Place type and status filters" }) {
                    Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Filters", style = Guide.heading(13.sp))
                }
            }
        }
    }
}

@Composable
private fun PlaceList(places: List<PlaceSummary>, onSelect: (PlaceSummary) -> Unit) {
    val colors = Guide.colors
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        modifier = Modifier.fillMaxSize().background(colors.paper),
    ) {
        item { GuideEyebrow("Places in this map area") }
        items(places.sortedBy { it.name.lowercase() }, key = { it.id }) { place ->
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.guideCard(colors.surface, colors.line)
                    .clickable(onClickLabel = "Open place card") { onSelect(place) },
            ) {
                Row {
                    Text(place.name, style = Guide.heading(21.sp), color = colors.ink, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.CallMade, contentDescription = null, tint = colors.teal)
                }
                KindLabel(place.kind)
                GuideStatusBadge(place.advisoryStatus)
                GuideFact("Last confirmation", DateText.display(place.lastConfirmedAt), Icons.Outlined.Schedule)
            }
        }
    }
}

@Composable
fun KindLabel(kind: PlaceKind, size: Int = 13) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(kind.icon, contentDescription = null, tint = Guide.colors.muted, modifier = Modifier.size((size + 3).dp))
        Text(kind.title, style = Guide.body(size.sp), color = Guide.colors.muted)
    }
}

@Composable
private fun BottomBar(
    mapError: String?,
    zoomLimited: Boolean,
    empty: Boolean,
    moreAvailable: Boolean,
    count: Int,
    onRetry: () -> Unit,
    onLegend: () -> Unit,
    onAdd: () -> Unit,
) {
    val colors = Guide.colors
    HorizontalDivider(color = colors.line)
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().background(colors.surface).navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        val message = when {
            mapError != null -> null
            zoomLimited -> "Zoom in for a closer look and more places."
            empty -> "No places in this area. Try another view or adjust your filters."
            moreAvailable -> "Showing $count places. Zoom in to see more."
            else -> null
        }
        if (mapError != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.WifiOff, contentDescription = null, tint = colors.warning, modifier = Modifier.size(18.dp))
                Text("Places could not load. Check your connection or API server.", style = Guide.body(13.sp),
                    color = colors.warning, modifier = Modifier.weight(1f))
                TextButton(onClick = onRetry) { Text("Retry", style = Guide.heading(13.sp)) }
            }
        }
        message?.let { Text(it, style = Guide.body(13.sp), color = colors.muted) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                GuideEyebrow("Find. Read. Check.")
                TextButton(onClick = onLegend, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Know the status", style = Guide.body(13.sp))
                }
            }
            GuideButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Add place")
            }
        }
    }
}

@Composable
fun PlaceMarker(place: PlaceSummary, onClick: () -> Unit) {
    val colors = Guide.colors
    Box(
        Modifier.size(52.dp)
            .clickable(onClickLabel = "Open place card", role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "${place.name}, ${place.kind.title}, ${place.advisoryStatus.title}"
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(44.dp).shadow(4.dp, CircleShape).background(place.advisoryStatus.color(), CircleShape)
                .border(3.dp, colors.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(place.advisoryStatus.icon, contentDescription = null, tint = colors.paper, modifier = Modifier.size(20.dp))
        }
        Box(
            Modifier.align(Alignment.BottomEnd).offset(x = (-2).dp, y = (-2).dp).size(19.dp)
                .background(colors.surface, CircleShape).border(1.dp, colors.line, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(place.kind.icon, contentDescription = null, tint = colors.ink, modifier = Modifier.size(11.dp))
        }
    }
}

@Composable
private fun ClusterMarker(count: Int, onClick: () -> Unit) {
    val colors = Guide.colors
    Box(
        Modifier.size(48.dp).background(colors.ink, CircleShape).border(3.dp, colors.surface, CircleShape)
            .clickable(onClickLabel = "Zoom in", role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$count places, double tap to zoom" },
        contentAlignment = Alignment.Center,
    ) { Text("$count", style = Guide.heading(18.sp), color = colors.paper) }
}

@Composable
private fun FiltersSheet(
    kinds: Set<PlaceKind>,
    onKinds: (Set<PlaceKind>) -> Unit,
    statuses: Set<AdvisoryStatus>,
    onStatuses: (Set<AdvisoryStatus>) -> Unit,
) {
    val colors = Guide.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        GuideEyebrow("A map with context")
        GuideHeading("Choose what you see.", size = 25)
        GuideMuted("Place type and evidence status are separate. A report does not establish permission.")
        GuideCard(spacing = 4.dp) {
            Text("Place type", style = Guide.heading(13.sp), color = colors.muted)
            PlaceKind.entries.forEach { kind ->
                FilterToggle(kind.title, kind.icon, colors.ink, kind in kinds) { enabled ->
                    onKinds(if (enabled) kinds + kind else kinds - kind)
                }
            }
        }
        GuideCard(spacing = 4.dp) {
            Text("Evidence & status", style = Guide.heading(13.sp), color = colors.muted)
            AdvisoryStatus.entries.filter { it != AdvisoryStatus.Closed }.forEach { status ->
                FilterToggle(status.title, status.icon, status.textColor(), status in statuses) { enabled ->
                    onStatuses(if (enabled) statuses + status else statuses - status)
                }
            }
            Text("Prohibited places appear as warnings. Directions are unavailable.", style = Guide.body(13.sp), color = colors.muted)
        }
    }
}

@Composable
private fun FilterToggle(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val colors = Guide.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, style = Guide.body(15.sp), color = tint, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = colors.tealInk, checkedThumbColor = colors.paper),
        )
    }
}

@Composable
private fun LegendSheet() {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        GuideEyebrow("Clear by design")
        GuideHeading("Every place,\nin context.", size = 30)
        GuideMuted("Read the type, evidence status, latest check, and access note before deciding where to go.", 16)
        AdvisoryStatus.entries.forEach { status ->
            GuideCard(spacing = 10.dp) {
                GuideStatusBadge(status)
                Text(status.explanation, style = Guide.body(15.sp), color = Guide.colors.ink)
            }
        }
        GuideMuted("Evidence review is not official certification. Follow posted signs, venue rules, and local law; respect shared air.")
    }
}

/** The status-first place card from the landing page, backed by live API details. */
@Composable
private fun PlacePeek(summary: PlaceSummary, state: AppState, onClose: () -> Unit, onDetails: () -> Unit) {
    val colors = Guide.colors
    var place by remember(summary.id) { mutableStateOf<Place?>(null) }
    var error by remember(summary.id) { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(summary.id, attempt) {
        error = null
        try {
            place = state.api.place(summary.id)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            error = "Latest access information could not load. Read the details or retry."
        }
    }
    val loadError = error
    val status = place?.advisoryStatus ?: summary.advisoryStatus
    val kind = place?.kind ?: summary.kind
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, bottom = 28.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GuideEyebrow("Every place, in context", Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, "Close place card", tint = colors.muted)
            }
        }
        Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            GuideHeading(place?.name ?: summary.name, size = 23)
            KindLabel(kind)
            GuideStatusBadge(status)
            when (status) {
                AdvisoryStatus.Prohibited -> GuideNotice("Smoking is reported as prohibited. Directions are unavailable.", Icons.Filled.Cancel, warning = true)
                AdvisoryStatus.Closed -> GuideNotice("This place is closed. Directions are unavailable.", Icons.Filled.Cancel, warning = true)
                AdvisoryStatus.Disputed -> GuideNotice("Information is disputed. Read the warning and current rules.", AdvisoryStatus.Disputed.icon, warning = true)
                else -> Unit
            }
            GuideFact("Last confirmation", DateText.display(place?.lastConfirmedAt ?: if (place == null) summary.lastConfirmedAt else null), Icons.Outlined.Schedule)
            val loaded = place
            when {
                loaded != null -> if (loaded.directionsAllowed) {
                    GuideFact(
                        "Access note",
                        loaded.accessNotes ?: "No access note on record. Check posted signs and the current entrance.",
                        Icons.AutoMirrored.Filled.DirectionsWalk,
                    )
                }
                loadError != null -> {
                    GuideNotice(loadError, Icons.Filled.WifiOff, warning = true)
                    TextButton(onClick = { attempt++ }) { Text("Retry latest details") }
                }
                else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.tealInk)
                    Text("Checking latest access information…", style = Guide.body(12.sp), color = colors.muted)
                }
            }
            HorizontalDivider(color = colors.line)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onDetails),
            ) {
                Text(
                    if (status == AdvisoryStatus.CommunityReported) "Read report details" else "View place details",
                    style = Guide.heading(15.sp), color = colors.tealInk, modifier = Modifier.weight(1f),
                )
                Icon(Icons.AutoMirrored.Filled.CallMade, contentDescription = null, tint = colors.tealInk)
            }
            if (status == AdvisoryStatus.CommunityReported) {
                Text("A report does not establish permission to smoke.", style = Guide.body(13.sp), color = colors.muted)
            }
        }
    }
}
