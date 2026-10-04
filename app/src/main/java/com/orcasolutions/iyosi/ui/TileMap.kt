package com.orcasolutions.iyosi.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import com.orcasolutions.iyosi.BuildConfig
import com.orcasolutions.iyosi.data.GeoPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.tan

/** Geographic bounds of the visible map, in degrees. */
data class MapBounds(val west: Double, val south: Double, val east: Double, val north: Double) {
    val latSpan get() = north - south
    val lonSpan get() = east - west
    val center get() = GeoPoint((north + south) / 2, (east + west) / 2)
    fun contains(point: GeoPoint) = point.lat in south..north && point.lon in west..east
}

/** Web Mercator camera. Coordinates are in "world pixels" at the current zoom, using a 256dp tile. */
@Stable
class MapCamera(center: GeoPoint, zoom: Double) {
    var center by mutableStateOf(center)
        private set
    var zoom by mutableStateOf(zoom)
        private set
    var size by mutableStateOf(IntSize.Zero)
        internal set
    internal var tilePx = 256f

    fun moveTo(point: GeoPoint, newZoom: Double = zoom) {
        center = GeoPoint(point.lat.coerceIn(-85.0, 85.0), point.lon.coerceIn(-180.0, 180.0))
        zoom = newZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
    }

    private fun worldSize(z: Double = zoom) = tilePx * 2.0.pow(z)
    fun worldX(lon: Double, z: Double = zoom) = (lon + 180) / 360 * worldSize(z)
    fun worldY(lat: Double, z: Double = zoom): Double {
        val radians = Math.toRadians(lat.coerceIn(-85.0, 85.0))
        return (1 - ln(tan(radians) + 1 / cos(radians)) / PI) / 2 * worldSize(z)
    }
    private fun lonAt(x: Double) = x / worldSize() * 360 - 180
    private fun latAt(y: Double) = Math.toDegrees(atan(sinh(PI * (1 - 2 * y / worldSize()))))

    fun toScreen(point: GeoPoint) = Offset(
        (size.width / 2.0 + worldX(point.lon) - worldX(center.lon)).toFloat(),
        (size.height / 2.0 + worldY(point.lat) - worldY(center.lat)).toFloat(),
    )

    fun toGeo(screen: Offset) = GeoPoint(
        latAt(worldY(center.lat) + screen.y - size.height / 2.0),
        lonAt(worldX(center.lon) + screen.x - size.width / 2.0),
    )

    fun panBy(dx: Float, dy: Float) {
        val point = GeoPoint(latAt(worldY(center.lat) - dy), lonAt(worldX(center.lon) - dx))
        moveTo(point)
    }

    /** Zooms by [factor] keeping the geographic point under [focus] fixed on screen. */
    fun zoomBy(factor: Float, focus: Offset) {
        val anchor = toGeo(focus)
        zoom = (zoom + ln(factor.toDouble()) / ln(2.0)).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val shifted = toScreen(anchor)
        panBy(focus.x - shifted.x, focus.y - shifted.y)
    }

    val bounds: MapBounds
        get() {
            val northWest = toGeo(Offset.Zero)
            val southEast = toGeo(Offset(size.width.toFloat(), size.height.toFloat()))
            return MapBounds(northWest.lon, southEast.lat, southEast.lon, northWest.lat)
        }

    companion object {
        const val MIN_ZOOM = 9.0
        const val MAX_ZOOM = 18.0
        val MetroManila = GeoPoint(14.595, 120.984)
    }
}

/** API viewport string clamped like the iOS client: at most 0.48° and at least 0.005° per side. */
fun MapBounds.apiBbox(): String {
    val halfLat = latSpan.coerceIn(0.005, 0.48) / 2
    val halfLon = lonSpan.coerceIn(0.005, 0.48) / 2
    val c = center
    return listOf(
        (c.lon - halfLon).coerceAtLeast(-180.0), (c.lat - halfLat).coerceAtLeast(-90.0),
        (c.lon + halfLon).coerceAtMost(180.0), (c.lat + halfLat).coerceAtMost(90.0),
    ).joinToString(",") { "%.6f".format(java.util.Locale.US, it) }
}

/** An item drawn at a geographic point above the tiles. */
class MapItem(val key: String, val point: GeoPoint, val content: @Composable () -> Unit)

private object TileCache {
    private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    val memory = object : LruCache<String, ImageBitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private val failedUntil = HashMap<String, Long>()
    private val inFlight = HashSet<String>()
    // OpenStreetMap's tile policy limits clients to two concurrent downloads.
    private val downloads = Semaphore(2)
    @Volatile private var pruned = false

    fun request(context: Context, scope: CoroutineScope, z: Int, x: Int, y: Int, onLoaded: () -> Unit) {
        val key = "$z-$x-$y"
        synchronized(this) {
            if (memory.get(key) != null || key in inFlight || (failedUntil[key] ?: 0) > System.currentTimeMillis()) return
            inFlight += key
        }
        val job = scope.launch {
            // Cancellation (the map left the screen) leaves the tile retryable rather than failed.
            val bitmap = withContext(Dispatchers.IO) { load(context, key, z, x, y) }
            synchronized(this@TileCache) {
                if (bitmap == null) failedUntil[key] = System.currentTimeMillis() + 30_000
            }
            if (bitmap != null) {
                memory.put(key, bitmap)
                onLoaded()
            }
        }
        // Also runs when the job is cancelled before it starts.
        job.invokeOnCompletion { synchronized(this@TileCache) { inFlight -= key } }
    }

    private suspend fun load(context: Context, key: String, z: Int, x: Int, y: Int): ImageBitmap? = try {
        val directory = File(context.cacheDir, "map-tiles").apply { mkdirs() }
        if (!pruned) {
            pruned = true
            directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > MAX_AGE_MS }?.forEach { it.delete() }
        }
        val file = File(directory, "$key.png")
        val cached = if (file.isFile && System.currentTimeMillis() - file.lastModified() < MAX_AGE_MS) file.readBytes() else null
        cached?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }?.let { return it.asImageBitmap() }
        if (cached != null) file.delete() // Corrupt or truncated; download again.
        val bytes = downloads.withPermit {
            val url = BuildConfig.TILE_URL.replace("{z}", "$z").replace("{x}", "$x").replace("{y}", "$y")
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.setRequestProperty("User-Agent", "iYosi/${BuildConfig.VERSION_NAME} (Android; ${BuildConfig.APPLICATION_ID})")
                connection.connectTimeout = 7_000
                connection.readTimeout = 7_000
                if (connection.responseCode == 200) connection.inputStream.use { it.readBytes() } else null
            } finally {
                connection.disconnect()
            }
        }
        if (bytes == null) null
        else BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also {
            // Write then rename so an interrupted write never leaves a truncated cache entry.
            val partial = File(directory, "$key.part")
            partial.writeBytes(bytes)
            partial.renameTo(file)
        }?.asImageBitmap()
    } catch (error: kotlinx.coroutines.CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }
}

/**
 * A dependency-free OpenStreetMap raster map. Pan with one finger, pinch to zoom, tap to pick a point.
 * Places are drawn as composables so each marker keeps its own accessibility label and click action.
 */
@OptIn(FlowPreview::class)
@Composable
fun TileMap(
    camera: MapCamera,
    items: List<MapItem>,
    modifier: Modifier = Modifier,
    description: String = "Map of Metro Manila",
    onTap: ((GeoPoint) -> Unit)? = null,
    onViewportSettled: (MapBounds) -> Unit = {},
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var tileGeneration by remember { mutableIntStateOf(0) }
    val colors = Guide.colors
    camera.tilePx = with(density) { 256.dp.toPx() }

    LaunchedEffect(camera) {
        snapshotFlow { Triple(camera.center, camera.zoom, camera.size) }
            .distinctUntilChanged()
            .debounce(350)
            .collect { (_, _, size) -> if (size != IntSize.Zero) onViewportSettled(camera.bounds) }
    }

    Box(
        modifier
            // Tiles and markers extend past the viewport; never paint over neighbouring UI.
            .clipToBounds()
            .background(if (colors == DarkGuide) Color(0xFF1C2B33) else Color(0xFFE9EEE8))
            .onSizeChanged { camera.size = it }
            .pointerInput(camera) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    camera.panBy(pan.x, pan.y)
                    if (zoom != 1f) camera.zoomBy(zoom, centroid)
                }
            }
            .pointerInput(camera, onTap) {
                detectTapGestures(
                    onDoubleTap = { camera.zoomBy(2f, it) },
                    onTap = { offset -> onTap?.invoke(camera.toGeo(offset)) },
                )
            }
            .semantics { contentDescription = description },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            check(tileGeneration >= 0) // Redraw when a tile finishes loading.
            val z = camera.zoom.roundToInt().coerceIn(0, 19)
            val scale = 2.0.pow(camera.zoom - z)
            val tileSize = camera.tilePx * scale
            val left = camera.worldX(camera.center.lon) - size.width / 2.0
            val top = camera.worldY(camera.center.lat) - size.height / 2.0
            val count = 1 shl z
            val x0 = floor(left / tileSize).toInt()
            val x1 = floor((left + size.width) / tileSize).toInt()
            val y0 = floor(top / tileSize).toInt().coerceAtLeast(0)
            val y1 = floor((top + size.height) / tileSize).toInt().coerceAtMost(count - 1)
            for (x in x0..x1) for (y in y0..y1) {
                val wrapped = ((x % count) + count) % count
                val bitmap = TileCache.memory.get("$z-$wrapped-$y")
                if (bitmap == null) {
                    TileCache.request(context, scope, z, wrapped, y) { tileGeneration++ }
                    continue
                }
                val px = (x * tileSize - left).roundToInt()
                val py = (y * tileSize - top).roundToInt()
                val next = ((x + 1) * tileSize - left).roundToInt()
                drawImage(
                    bitmap,
                    dstOffset = IntOffset(px, py),
                    dstSize = IntSize(next - px, ((y + 1) * tileSize - top).roundToInt() - py),
                )
            }
        }
        Box(Modifier.fillMaxSize()) {
            items.forEach { item ->
                val position = camera.toScreen(item.point)
                if (position.x < -80 || position.y < -80 || position.x > camera.size.width + 80 || position.y > camera.size.height + 80) return@forEach
                androidx.compose.runtime.key(item.key) {
                    Box(
                        Modifier.offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
                            .layoutCentered(),
                    ) { item.content() }
                }
            }
        }
        val uriHandler = LocalUriHandler.current
        val osm = BuildConfig.TILE_URL.contains("openstreetmap.org")
        Text(
            BuildConfig.TILE_ATTRIBUTION,
            fontSize = 11.sp,
            color = Color(0xFF192B35),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(Color(0xCCFFFEFA))
                .then(if (osm) Modifier.clickable { uriHandler.openUri("https://www.openstreetmap.org/copyright") } else Modifier)
                .padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}

/** Centers a child on its placement point rather than using it as the top-left corner. */
private fun Modifier.layoutCentered() = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(0, 0) { placeable.place(-placeable.width / 2, -placeable.height / 2) }
    },
)
