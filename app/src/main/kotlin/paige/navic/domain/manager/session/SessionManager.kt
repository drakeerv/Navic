/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.manager.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import paige.navic.BuildConfig
import paige.navic.domain.manager.session.account.Account
import paige.navic.domain.manager.session.account.AccountStore
import paige.navic.domain.model.DomainUser
import paige.navic.domain.server.AuthMethod
import paige.navic.domain.server.AuthorizationData
import paige.navic.domain.server.ChatServer
import paige.navic.domain.server.CommandFlowServer
import paige.navic.domain.server.InvalidCredentialsException
import paige.navic.domain.server.MusicServer
import paige.navic.domain.server.MusicServerFactory
import paige.navic.domain.server.PlaylistCoverServer
import paige.navic.domain.server.RadioServer
import paige.navic.domain.server.ServerFeature
import paige.navic.domain.server.ServerInfo
import paige.navic.domain.server.ServerType
import paige.navic.domain.server.ShareServer
import paige.navic.domain.server.UserCredentials
import paige.navic.preferences.PreferenceManager
import paige.navic.util.Logger
import paige.navic.util.TTLLruCache
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit

sealed interface SessionState {
	data object Unauthenticated : SessionState
	data object Restoring : SessionState
	data class Authenticated(
		val currentUser: DomainUser?,
		val serverInfo: ServerInfo?
	) : SessionState
}

class SessionManager(
	private val accountStore: AccountStore,
	private val preferenceManager: PreferenceManager,
	private val serverFactory: MusicServerFactory
) {
	val ttlLruCache = TTLLruCache<String, String>(
		maxSize = 500,
		defaultTtlMillis = 30.minutes.toLong(DurationUnit.MILLISECONDS)
	)

	private companion object {
		const val TAG = "SessionManager"
	}

	val accounts: StateFlow<List<Account>> = accountStore.accounts

	val activeAccountId: String?
		get() = accountStore.activeAccountId

	private val hasStoredCredentials: Boolean
		get() = accountStore.activeAccount?.hasCredentials == true

	private val _state = MutableStateFlow(
		if (hasStoredCredentials) SessionState.Restoring else SessionState.Unauthenticated
	)
	val sessionState: StateFlow<SessionState> = _state.asStateFlow()

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

	val username: String
		get() = accountStore.activeAccount?.username.orEmpty()

	val instanceUrl: String
		get() = accountStore.activeAccount?.baseUrl.orEmpty()

	private fun createClient(account: Account?): MusicServer = serverFactory.create(
		baseUrl = account?.baseUrl.orEmpty(),
		authorization = account?.authorization() ?: AuthorizationData.SubsonicToken("", "")
	)

	var api: MusicServer = createClient(accountStore.activeAccount)
		private set

	val chat: ChatServer?
		get() = api as? ChatServer

	val shares: ShareServer?
		get() = api as? ShareServer

	val radio: RadioServer?
		get() = api as? RadioServer

	val playlistCovers: PlaylistCoverServer?
		get() = api as? PlaylistCoverServer

	val commandFlow: CommandFlowServer?
		get() = api as? CommandFlowServer

	init {
		val account = accountStore.activeAccount
		if (account != null && account.hasCredentials) {
			scope.launch {
				try {
					restore(account)
				} catch (e: InvalidCredentialsException) {
					// keep the account so its cache and downloads survive a rejected login
					Logger.e(TAG, "Credentials invalid, signing out", e)
					signOut()
				} catch (e: Exception) {
					Logger.w(TAG, "Failed to reach server during restoration, treating as offline authenticated", e)
					_state.value = SessionState.Authenticated(
						currentUser = null,
						serverInfo = null
					)
				}
			}
		}
	}

	private suspend fun restore(account: Account) {
		val authorization = account.authorization() ?: run {
			signOut()
			return
		}
		val server = serverFactory.create(
			baseUrl = account.baseUrl,
			authorization = authorization
		)
		val serverInfo = server.serverInfo()
		Logger.i(TAG, "Ping result: $serverInfo")
		val user = server.currentUser()

		api = server
		_state.value = SessionState.Authenticated(
			currentUser = user,
			serverInfo = serverInfo
		)
	}

	suspend fun login(instanceUrl: String, credentials: UserCredentials): Account {
		val authorization = serverFactory.authenticate(instanceUrl, credentials)
		val server = serverFactory.create(instanceUrl, authorization)

		val serverInfo = server.serverInfo()
		Logger.i(TAG, "Ping result: $serverInfo")

		val user = server.currentUser()

		val account = accountStore.upsert(
			Account(
				id = UUID.randomUUID().toString(),
				baseUrl = instanceUrl,
				serverType = when (authorization) {
					is AuthorizationData.JellyfinToken -> ServerType.JELLYFIN
					else -> ServerType.NAVIDROME
				},
				authMethod = when (authorization) {
					is AuthorizationData.SubsonicApiKey -> AuthMethod.ApiKey
					else -> AuthMethod.Token
				},
				username = when (authorization) {
					is AuthorizationData.SubsonicToken -> authorization.username
					else -> user.username
				},
				password = (authorization as? AuthorizationData.SubsonicToken)?.password.orEmpty(),
				apiKey = (authorization as? AuthorizationData.SubsonicApiKey)?.apiKey.orEmpty(),
				jellyfinToken = (authorization as? AuthorizationData.JellyfinToken)?.accessToken.orEmpty(),
				jellyfinUserId = (authorization as? AuthorizationData.JellyfinToken)?.userId.orEmpty()
			)
		)

		// never change the active account here: a different account means different
		// databases and directories, which the caller applies by switching and restarting
		if (account.id == accountStore.activeAccountId) {
			api = server
			_state.value = SessionState.Authenticated(
				currentUser = user,
				serverInfo = serverInfo
			)
		}

		return account
	}

	suspend fun login(instanceUrl: String, authorization: AuthorizationData, serverType: ServerType): Account {
		val server = serverFactory.create(instanceUrl, authorization)
		val serverInfo = server.serverInfo()
		val user = server.currentUser()

		val localFolders = (authorization as? AuthorizationData.LocalFolders)?.folderUris.orEmpty()
		val account = accountStore.upsert(
			Account(
				id = UUID.randomUUID().toString(),
				baseUrl = instanceUrl,
				serverType = serverType,
				localFolderUris = localFolders
			)
		)

		if (account.id == accountStore.activeAccountId) {
			api = server
			_state.value = SessionState.Authenticated(
				currentUser = user,
				serverInfo = serverInfo
			)
		}

		return account
	}

	fun signOut() {
		ttlLruCache.clear()
		_state.value = SessionState.Unauthenticated
	}

	/**
	 * Points the session at another saved account. Data paths only change on the
	 * next process start, so callers restart the app afterwards.
	 */
	fun setActiveAccount(id: String) {
		accountStore.setActive(id)
		api = createClient(accountStore.activeAccount)
		ttlLruCache.clear()
	}

	fun removeAccount(id: String) {
		val wasActive = accountStore.activeAccountId == id
		accountStore.remove(id)
		if (!wasActive) return

		api = createClient(accountStore.activeAccount)
		ttlLruCache.clear()

		val active = accountStore.activeAccount
		_state.value = if (active != null && active.hasCredentials) {
			SessionState.Restoring
		} else {
			SessionState.Unauthenticated
		}
	}

	fun refreshClient() {
		api = createClient(accountStore.activeAccount)
	}

	fun updateLocalFolders(folderUris: List<String>) {
		val account = accountStore.activeAccount ?: return
		if (account.serverType != ServerType.LOCAL) return
		accountStore.updateLocalFolders(account.id, folderUris)
		refreshClient()
	}

	fun getAvatarUrl(account: Account): String? {
		val username = account.username?.takeIf(String::isNotBlank) ?: return null
		val authorization = account.authorization() ?: return null
		return serverFactory.create(account.baseUrl, authorization).getAvatarUrl(username)
	}

	fun updateInstanceUrl(url: String) {
		val rawUrl = url.trim().removeSuffix("/")
		val formattedUrl = if (rawUrl.startsWith("https://") || rawUrl.startsWith("http://")) {
			rawUrl
		} else {
			"https://$rawUrl"
		}
		val account = accountStore.activeAccount ?: return
		if (formattedUrl.isNotBlank() && formattedUrl != account.baseUrl) {
			accountStore.updateBaseUrl(account.id, formattedUrl)
			refreshClient()
		}
	}

	fun getCoverArtUrl(coverArtId: String): String {
		val serverInfo = getServerInfo()
		if (serverInfo?.type == ServerType.LOCAL) {
			return "content://${BuildConfig.APPLICATION_ID}.cover-art/$coverArtId" +
				"?size=${preferenceManager.coverArtQuality.value}"
		}
		return api.getCoverArtUrl(
			coverArtId,
			size = "${preferenceManager.coverArtQuality.value}"
		)
	}

}

fun DomainUser.hasRole(role: String): Boolean {
	return this.roles.contains(role)
}

fun DomainUser.canShare(): Boolean {
	return this.hasRole("share")
}

fun SessionManager.canUserShare(): Boolean {
	return (sessionState.value as? SessionState.Authenticated)?.currentUser?.canShare() == true
}

fun SessionManager.getUser(): DomainUser? {
	return (sessionState.value as? SessionState.Authenticated)?.currentUser
}

fun SessionManager.getServerInfo(): ServerInfo? {
	return (sessionState.value as? SessionState.Authenticated)?.serverInfo
}

fun SessionManager.isNavidrome(): Boolean {
	return this.getServerInfo()?.type == ServerType.NAVIDROME
}

fun SessionManager.isJellyfin(): Boolean {
	return this.getServerInfo()?.type == ServerType.JELLYFIN
}

fun SessionManager.isLocal(): Boolean {
	return this.getServerInfo()?.type == ServerType.LOCAL
}

fun SessionManager.supportsFeature(feature: ServerFeature): Boolean {
	return this.getServerInfo()?.features?.contains(feature) == true
}

fun SessionManager.supportsTranscoding(): Boolean {
	return this.getServerInfo()?.capabilities?.supportsTranscoding != false
}

fun SessionManager.supportsDownloads(): Boolean {
	return this.getServerInfo()?.capabilities?.supportsDownloads != false
}

fun SessionManager.supportsPlaySubmission(): Boolean {
	return this.getServerInfo()?.capabilities?.supportsPlaySubmission != false
}

fun SessionManager.supportsRatings(): Boolean {
	return this.getServerInfo()?.capabilities?.supportsRatings != false
}

fun SessionManager.supportsShares(): Boolean {
	return this.getServerInfo()?.capabilities?.supportsShares != false
}
