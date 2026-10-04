package com.orcasolutions.iyosi

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.orcasolutions.iyosi.data.GeoPoint

/**
 * One foreground fix, requested only after the person taps Near me. No Play Services needed.
 * The result is delivered at most once; cancel the returned signal when the caller goes away.
 */
object ForegroundLocation {
    private const val TIMEOUT_MS = 30_000L

    @SuppressLint("MissingPermission")
    fun request(context: Context, callback: (GeoPoint?) -> Unit): CancellationSignal {
        val signal = CancellationSignal()
        var delivered = false
        val onResult = { point: GeoPoint? ->
            if (!delivered && !signal.isCanceled) {
                delivered = true
                callback(point)
            }
        }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        if (provider == null) {
            onResult(null)
            return signal
        }
        val deliver = { location: Location? -> onResult(location?.let { GeoPoint(it.latitude, it.longitude) }) }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                manager.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context)) { deliver(it) }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) = deliver(location)
                    @Deprecated("Required on older platforms")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) = deliver(null)
                }
                // requestSingleUpdate has no timeout of its own; match getCurrentLocation's.
                val handler = Handler(Looper.getMainLooper())
                val timeout = Runnable { manager.removeUpdates(listener); deliver(null) }
                signal.setOnCancelListener { manager.removeUpdates(listener); handler.removeCallbacks(timeout) }
                handler.postDelayed(timeout, TIMEOUT_MS)
                @Suppress("DEPRECATION")
                manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            }
        } catch (_: Exception) {
            onResult(null)
        }
        return signal
    }
}
