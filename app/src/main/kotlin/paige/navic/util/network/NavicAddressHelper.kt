/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.util.network

import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import okhttp3.Interceptor
import paige.navic.domain.manager.session.account.AccountStore
import paige.navic.domain.model.settings.ServerAddress
import paige.navic.preferences.AccountSettings
import paige.navic.domain.manager.session.connectivity.ConnectivityManager
import paige.navic.domain.manager.session.connectivity.NetworkStatus
import paige.navic.util.Logger
import paige.navic.util.TTLLruCache
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

class NavicAddressHelper(
	val accountSettings: AccountSettings,
	val accountStore: AccountStore,
	val connectivityManager: ConnectivityManager
) : Dns {
	private companion object {
		const val TAG = "NavicAddressHelper"
		const val PROBE_TIMEOUT_MILLIS = 1000

		val lookupCache = TTLLruCache<String, List<InetAddress>>(
			maxSize = 50,
			defaultTtlMillis = TimeUnit.MINUTES.toMillis(/* duration = */ 10)
		)

		// a failed probe is cached too (empty list), so being off-lan doesn't cost a probe per request
		val alternateCache = TTLLruCache<String, List<ServerAddress>>(
			maxSize = 10,
			defaultTtlMillis = TimeUnit.MINUTES.toMillis(/* duration = */ 1)
		)

		val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	}

	init {
		scope.launch {
			connectivityManager.status
				.filter { it is NetworkStatus.NetworkAvailable || it is NetworkStatus.NetworkLost }
				.collect {
					invalidate()
				}
		}
	}

	fun invalidate() {
		lookupCache.clear()
		alternateCache.clear()
	}

	override fun lookup(hostname: String): List<InetAddress> {
		lookupCache.get(hostname)?.let { return it }
		return Dns.SYSTEM.lookup(hostname).also { lookupCache.put(hostname, it) }
	}

	@OptIn(ExperimentalCoroutinesApi::class)
	private fun selectAlternate(serverHost: String): ServerAddress? {
		alternateCache.get(serverHost)?.let { return it.firstOrNull() }

		val reachable = runBlocking {
			accountSettings.alternateServerAddresses
				.filter { it.host.isNotBlank() }
				.filter { accountSettings.allowInsecureConnections || it.scheme.isSecure }
				.asFlow()
				.flatMapMerge { address ->
					flow { emit(address.probe()) }
				}
				.filterNotNull()
				.flowOn(Dispatchers.IO)
				.toList()
		}

		val selected = reachable
			.maxByOrNull { (_, resolved) -> resolved.isLocalOrPrivate() }
			?.first

		alternateCache.put(serverHost, listOfNotNull(selected))
		return selected
	}

	private fun ServerAddress.probe(): Pair<ServerAddress, InetAddress>? {
		val resolved = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return null
		val reachable = runCatching {
			Socket().use { socket ->
				socket.connect(InetSocketAddress(resolved, port), PROBE_TIMEOUT_MILLIS)
			}
		}.isSuccess
		return if (reachable) this to resolved else null
	}

	val requestInterceptor: Interceptor
		get() = Interceptor { chain ->
			val request = chain.request()
			val baseUri = accountStore.activeAccount?.baseUrl?.toUri()
			val baseHost = baseUri?.host

			if (baseHost == null || !request.url.host.equals(baseHost, ignoreCase = true)) {
				return@Interceptor chain.proceed(request)
			}

			val alternate = selectAlternate(request.url.host)
				?: return@Interceptor chain.proceed(request)

			Logger.d(TAG, "currently using $alternate as the address")

			val originalBasePath = baseUri.path.orEmpty().trimEnd('/')
			val remainingPath = request.url.encodedPath
				.removePrefix(originalBasePath)
				.ifEmpty { "/" }
			val alternateBasePath = alternate.basePath.orEmpty().trimEnd('/')

			val targetScheme = if (request.isHttps && !accountSettings.allowInsecureConnections) {
				"https"
			} else {
				alternate.scheme.name.lowercase()
			}

			val newUrl = request.url.newBuilder()
				.scheme(targetScheme)
				.host(alternate.host)
				.port(alternate.port)
				.encodedPath(alternateBasePath + remainingPath)
				.build()
			Logger.d(TAG, "rewrote url successfully: $newUrl")

			chain.proceed(request.newBuilder().url(newUrl).build())
		}

	fun InetAddress.isLocalOrPrivate(): Boolean {
		return this.isLoopbackAddress || this.isSiteLocalAddress || this.isLinkLocalAddress
	}
}
