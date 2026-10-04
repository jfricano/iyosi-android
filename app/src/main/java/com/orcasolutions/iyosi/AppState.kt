package com.orcasolutions.iyosi

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.orcasolutions.iyosi.data.Account
import com.orcasolutions.iyosi.data.ApiClient
import com.orcasolutions.iyosi.data.ApiException
import com.orcasolutions.iyosi.data.HttpTransport
import com.orcasolutions.iyosi.data.TokenStore
import com.orcasolutions.iyosi.data.Transport

/** Persistent session state shared by every screen: server, adult access, and account. */
class AppState(
    private val prefs: Preferences,
    private val tokens: TokenSink,
    private val transport: Transport = HttpTransport,
) {
    interface Preferences {
        fun string(key: String): String?
        fun putString(key: String, value: String)
        fun boolean(key: String): Boolean
        fun putBoolean(key: String, value: Boolean)
    }

    interface TokenSink {
        fun read(): String?
        fun save(token: String?)
    }

    var baseUrl by mutableStateOf(prefs.string(KEY_BASE_URL) ?: BuildConfig.API_BASE_URL)
        private set
    var token by mutableStateOf(tokens.read())
        private set
    var account by mutableStateOf<Account?>(null)
        private set
    var accountError by mutableStateOf<String?>(null)
        private set
    var adultAccessAccepted by mutableStateOf(prefs.boolean(KEY_ADULT_ACCEPTED))
        private set
    var adultAccessDeclined by mutableStateOf(prefs.boolean(KEY_ADULT_DECLINED))
        private set

    val isSignedIn get() = token != null
    val api get() = ApiClient(baseUrl, token, transport) { signOut() }

    fun acceptAdultAccess() {
        adultAccessAccepted = true
        prefs.putBoolean(KEY_ADULT_ACCEPTED, true)
    }

    fun declineAdultAccess() {
        adultAccessDeclined = true
        prefs.putBoolean(KEY_ADULT_DECLINED, true)
    }

    /** Saves a new server; changing servers signs out the current account. */
    fun updateBaseUrl(value: String): Boolean {
        val trimmed = value.trim().trimEnd('/')
        if (!ApiClient.isAllowedBaseUrl(trimmed)) return false
        if (trimmed != baseUrl) {
            baseUrl = trimmed
            prefs.putString(KEY_BASE_URL, trimmed)
            signOut()
        }
        return true
    }

    /** Signs in; the account loads when [token] changes. A token is dropped if the server changed meanwhile. */
    suspend fun authenticate(email: String, password: String, registering: Boolean) {
        val server = baseUrl
        val client = ApiClient(server, null, transport)
        val credentials = if (registering) client.register(email, password) else client.login(email, password)
        if (server != baseUrl) throw ApiException(0, "The API server changed while signing in. Try again.", "server_changed")
        token = credentials.accessToken
        tokens.save(credentials.accessToken)
    }

    suspend fun refreshAccount() {
        if (!isSignedIn) {
            account = null
            return
        }
        try {
            account = api.me()
            accountError = null
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            accountError = error.message
            if (error is ApiException && (error.code == "unauthenticated" || error.status == 401)) signOut()
        }
    }

    fun signOut() {
        tokens.save(null)
        token = null
        account = null
        accountError = null
    }

    companion object {
        private const val KEY_BASE_URL = "apiBaseUrl"
        private const val KEY_ADULT_ACCEPTED = "adultAccessAccepted"
        private const val KEY_ADULT_DECLINED = "adultAccessDeclined"

        fun create(context: Context): AppState {
            val shared = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val store = TokenStore(context)
            return AppState(
                prefs = object : Preferences {
                    override fun string(key: String) = shared.getString(key, null)
                    override fun putString(key: String, value: String) = shared.edit().putString(key, value).apply()
                    override fun boolean(key: String) = shared.getBoolean(key, false)
                    override fun putBoolean(key: String, value: Boolean) = shared.edit().putBoolean(key, value).apply()
                },
                tokens = object : TokenSink {
                    override fun read() = store.read()
                    override fun save(token: String?) = store.save(token)
                },
            )
        }
    }
}

/** Keeps one [AppState] across configuration changes. */
class AppViewModel(application: Application) : AndroidViewModel(application) {
    val state = AppState.create(application)
    val navigator = com.orcasolutions.iyosi.ui.Navigator()
}
