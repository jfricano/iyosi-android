package com.orcasolutions.iyosi.data

import com.orcasolutions.iyosi.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.serializer
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ApiException(val status: Int, message: String, val code: String) : Exception(message)

@OptIn(ExperimentalSerializationApi::class)
val ApiJson = Json {
    namingStrategy = JsonNamingStrategy.SnakeCase
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

/** Seam for tests and the debug design preview; production uses [HttpTransport]. */
fun interface Transport {
    /** Returns the HTTP status code and response body. */
    suspend fun send(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String>
}

object HttpTransport : Transport {
    override suspend fun send(method: String, url: String, headers: Map<String, String>, body: String?) =
        withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.requestMethod = method
                headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
                if (body != null) {
                    connection.doOutput = true
                    connection.outputStream.use { it.write(body.toByteArray()) }
                }
                val status = connection.responseCode
                val stream = if (status < 400) connection.inputStream else connection.errorStream
                status to (stream?.bufferedReader()?.use { it.readText() } ?: "")
            } finally {
                connection.disconnect()
            }
        }
}

class ApiClient(
    private val baseUrl: String,
    private val token: String? = null,
    private val transport: Transport = HttpTransport,
    /** Called when the server rejects [token], so the app can sign out instead of failing every form. */
    private val onUnauthorized: () -> Unit = {},
) {
    private suspend inline fun <reified T> request(
        path: String,
        method: String = "GET",
        query: List<Pair<String, String>> = emptyList(),
        body: String? = null,
        idempotencyKey: String? = null,
    ): T = request(path, method, query, body, idempotencyKey, serializer())

    private suspend fun <T> request(
        path: String,
        method: String,
        query: List<Pair<String, String>>,
        body: String?,
        idempotencyKey: String?,
        deserializer: KSerializer<T>,
    ): T {
        val url = buildUrl(baseUrl, path, query) ?: throw ApiException(0, INVALID_SERVER, "invalid_server_url")
        val headers = buildMap {
            put("Accept", "application/json")
            token?.let { put("Authorization", "Bearer $it") }
            idempotencyKey?.let { put("Idempotency-Key", it) }
            if (body != null) put("Content-Type", "application/json")
        }
        val (status, text) = try {
            transport.send(method, url, headers, body)
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            throw ApiException(0, "Could not reach the API. Check your connection or API server.", "network_error")
        }
        if (status !in 200..299) {
            val error = runCatching { ApiJson.decodeFromString<ApiErrorBody>(text) }.getOrNull()
            if (status == 401 && token != null) onUnauthorized()
            val detail = error?.fieldErrors?.firstOrNull()?.let { field ->
                val name = field.field.substringAfterLast('.').replace('_', ' ')
                if (name.isBlank()) " ${field.message}" else " ($name: ${field.message})"
            } ?: ""
            throw ApiException(status, (error?.message ?: "Server returned $status.") + detail, error?.code ?: "http_error")
        }
        return ApiJson.decodeFromString(deserializer, text.ifEmpty { "{}" })
    }

    private inline fun <reified T> encode(value: T) = ApiJson.encodeToString(serializer(), value)

    suspend fun places(bbox: String, cursor: String? = null, includeProhibited: Boolean = false): Page<PlaceSummary> =
        request("/v1/places", query = buildList {
            add("bbox" to bbox); add("limit" to "100")
            cursor?.let { add("cursor" to it) }
            if (includeProhibited) add("include_prohibited" to "true")
        })

    suspend fun place(id: String): Place = request("/v1/places/${segment(id)}")

    suspend fun nearby(point: GeoPoint): Page<PlaceSummary> = request(
        "/v1/places/nearby",
        query = listOf("lat" to point.lat.toString(), "lon" to point.lon.toString(), "radius_m" to "200"),
    )

    suspend fun suggestions(query: String): AddressSuggestions =
        request("/v1/address-suggestions", query = listOf("q" to query))

    suspend fun reviews(id: String, cursor: String? = null): Page<PublicReview> =
        request("/v1/places/${segment(id)}/reviews", query = listOfNotNull(cursor?.let { "cursor" to it }))

    suspend fun confirmations(id: String, cursor: String? = null): Page<PublicConfirmation> =
        request("/v1/places/${segment(id)}/confirmations", query = listOfNotNull(cursor?.let { "cursor" to it }))

    suspend fun login(email: String, password: String): AuthToken =
        request("/v1/auth/token", "POST", body = encode(Credentials(email, password)))

    suspend fun register(email: String, password: String): AuthToken =
        request("/v1/auth/register", "POST", body = encode(Registration(email, password, adultAttested = true)))

    suspend fun me(): Account = request("/v1/me")

    suspend fun submissions(): List<SubmissionStatus> = request<SubmissionList>("/v1/me/place-submissions").items

    suspend fun submit(payload: SubmissionRequest, key: String): Acknowledgement =
        request("/v1/place-submissions", "POST", body = encode(payload), idempotencyKey = key)

    suspend fun confirm(id: String, payload: ConfirmationRequest, key: String): Acknowledgement =
        request("/v1/places/${segment(id)}/confirmations", "POST", body = encode(payload), idempotencyKey = key)

    suspend fun review(id: String, payload: ReviewRequest): PublicReview =
        request("/v1/places/${segment(id)}/reviews/me", "PUT", body = encode(payload))

    suspend fun report(payload: ReportRequest, key: String): Acknowledgement =
        request("/v1/reports", "POST", body = encode(payload), idempotencyKey = key)

    suspend fun deletionRequest(): Acknowledgement = request("/v1/me/deletion-requests", "POST")

    companion object {
        const val INVALID_SERVER = "Enter a valid HTTPS API URL, or a local HTTP URL for development."
        private val localHosts = setOf("localhost", "127.0.0.1", "10.0.2.2")

        /**
         * HTTPS anywhere; plain HTTP only to local development hosts and only in debug builds,
         * because release builds block cleartext traffic.
         */
        fun isAllowedBaseUrl(value: String, allowLocalHttp: Boolean = BuildConfig.DEBUG): Boolean {
            val uri = runCatching { java.net.URI(value.trim()) }.getOrNull() ?: return false
            val host = uri.host?.lowercase() ?: return false
            return when (uri.scheme?.lowercase()) {
                "https" -> true
                "http" -> allowLocalHttp && (host in localHosts || host.endsWith(".local"))
                else -> false
            } && uri.query == null && uri.fragment == null
        }

        fun buildUrl(baseUrl: String, path: String, query: List<Pair<String, String>>): String? {
            val base = baseUrl.trim().trimEnd('/')
            if (!isAllowedBaseUrl(base)) return null
            val queryText = query.joinToString("&") { (name, value) -> "${percentEncode(name)}=${percentEncode(value)}" }
            return base + path + if (queryText.isEmpty()) "" else "?$queryText"
        }

        private fun segment(id: String) = percentEncode(id)

        /** RFC 3986 encoding of everything except unreserved characters. */
        fun percentEncode(value: String): String = buildString {
            value.toByteArray(Charsets.UTF_8).forEach { byte ->
                val char = (byte.toInt() and 0xFF).toChar()
                if (char.isLetterOrDigit() && char.code < 128 || char in "-._~") append(char)
                else append('%').append("%02X".format(byte.toInt() and 0xFF))
            }
        }
    }
}
