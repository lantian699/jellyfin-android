package org.jellyfin.mobile.controller

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jellyfin.apiclient.Jellyfin
import org.jellyfin.apiclient.interaction.ApiClient
import org.jellyfin.apiclient.model.system.PublicSystemInfo
import org.jellyfin.mobile.AppPreferences
import org.jellyfin.mobile.R
import org.jellyfin.mobile.model.sql.dao.ServerDao
import org.jellyfin.mobile.model.sql.dao.UserDao
import org.jellyfin.mobile.model.state.CheckUrlState
import org.jellyfin.mobile.model.state.LoginState
import org.jellyfin.mobile.utils.PRODUCT_NAME_SUPPORTED_SINCE
import org.jellyfin.mobile.utils.authenticateUser
import org.jellyfin.mobile.utils.getPublicSystemInfo

class ServerController(
    private val jellyfin: Jellyfin,
    private val apiClient: ApiClient,
    private val appPreferences: AppPreferences,
    private val serverDao: ServerDao,
    private val userDao: UserDao,
) {
    private val scope = CoroutineScope(Dispatchers.Main)

    var loginState by mutableStateOf(LoginState.PENDING)

    init {
        scope.launch {
            // Migrate from preferences if necessary
            appPreferences.instanceUrl?.let { url ->
                val serverId = withContext(Dispatchers.IO) {
                    serverDao.insert(url)
                }
                appPreferences.currentServerId = serverId
                appPreferences.instanceUrl = null
            }

            val serverUser = withContext(Dispatchers.IO) {
                val serverId = appPreferences.currentServerId ?: return@withContext null
                val userId = appPreferences.currentUserId ?: return@withContext null
                userDao.getServerUser(serverId, userId)
            }
            loginState = if (serverUser != null) {
                val (user, server) = serverUser
                apiClient.ChangeServerLocation(server.hostname)
                apiClient.SetAuthenticationInfo(user.accessToken, user.id)
                LoginState.LOGGED_IN
            } else {
                LoginState.NOT_LOGGED_IN
            }
        }
    }

    suspend fun checkServerUrl(hostname: String): CheckUrlState {
        val urls = jellyfin.discovery.addressCandidates(hostname)
        var httpUrl: HttpUrl? = null
        var serverInfo: PublicSystemInfo? = null
        loop@ for (url in urls) {
            httpUrl = url.toHttpUrlOrNull()

            if (httpUrl == null) {
                return CheckUrlState.Error(R.string.connection_error_invalid_format)
            }

            // Set API client address
            apiClient.ChangeServerLocation(httpUrl.toString())

            serverInfo = apiClient.getPublicSystemInfo()
            if (serverInfo != null)
                break@loop
        }

        if (httpUrl == null || serverInfo == null) {
            return CheckUrlState.Error()
        }

        val version = serverInfo.version
            .split('.')
            .mapNotNull(String::toIntOrNull)

        val isValidInstance = when {
            version.size != 3 -> false
            version[0] == PRODUCT_NAME_SUPPORTED_SINCE.first && version[1] < PRODUCT_NAME_SUPPORTED_SINCE.second -> true // Valid old version
            else -> true // FIXME: check ProductName once API client supports it
        }

        return if (isValidInstance) CheckUrlState.Success else CheckUrlState.Error()
    }

    suspend fun authenticate(username: String, password: String): Boolean {
        requireNotNull(apiClient.serverAddress) { "Server address not set" }
        val authResult = apiClient.authenticateUser(username, password)
        if (authResult != null) {
            val user = authResult.user
            withContext(Dispatchers.IO) {
                val serverId = serverDao.insert(apiClient.serverAddress)
                userDao.insert(serverId, user.id, authResult.accessToken)
                appPreferences.currentServerId = serverId
                appPreferences.currentUserId = user.id
            }
            loginState = LoginState.LOGGED_IN
            return true
        }
        return false
    }
}
