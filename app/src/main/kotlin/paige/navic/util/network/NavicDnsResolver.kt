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
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.Interceptor
import paige.navic.domain.manager.session.account.AccountStore
import paige.navic.preferences.AccountSettings
import paige.navic.domain.manager.session.connectivity.ConnectivityManager
import paige.navic.domain.manager.session.connectivity.NetworkStatus
import paige.navic.util.Logger
import paige.navic.util.TTLLruCache
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

class NavicDnsResolver(
	val accountSettings: AccountSettings,
	val accountStore: AccountStore,
	val connectivityManager: ConnectivityManager
) : Dns {
	private companion object {
		const val TAG = "NavicDnsResolver"
		val lookupCache = TTLLruCache<String, List<InetAddress>>(
			maxSize = 50,
			defaultTtlMillis = TimeUnit.MINUTES.toMillis(/* duration = */ 10)
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

	/* shorthand for invalidating the lookup cache */
	fun invalidate() = lookupCache.clear()

	override fun lookup(hostname: String): List<InetAddress> {
		val fromCache = lookupCache.get(hostname)
		if (fromCache == null) {
			val toCache = resolve(hostname)
			lookupCache.put(hostname, toCache)
			return toCache
		}
		return fromCache
	}

	@OptIn(ExperimentalCoroutinesApi::class)
	fun resolve(hostname: String): List<InetAddress> {
		val serverDomain = accountStore.activeAccount
			?.baseUrl
			?.toUri()
			?.host
		val systemAddresses = Dns.SYSTEM.lookup(hostname)
		if (hostname != serverDomain) return systemAddresses

		val alternates = runBlocking {
			accountSettings.alternateServerAddresses
				.filter {
					accountSettings.allowInsecureConnections || it.scheme.isSecure
				}
				.asFlow()
				.flatMapMerge { address ->
					flow {
						emit(
							runCatching {
								InetAddress.getByName(address.host)
							}.getOrNull()?.takeIf {
								runCatching {
									Socket().use { socket ->
										socket.connect(
											InetSocketAddress(address.host, address.port)
										)
									}
								}.isSuccess
							}
						)
					}
				}
				.filterNotNull()
				.flowOn(Dispatchers.IO)
				.toList()
		}
		if (alternates.isNotEmpty()) {
			Logger.i(TAG, "candidates for alternate addresses (not ranked by local or private): $alternates")
		}

		return (systemAddresses + alternates).sortedByDescending { it.isLocalOrPrivate() }
	}

	val requestInterceptor: Interceptor
		get() = Interceptor { chain ->
			var request = chain.request()
			val host = request.url.host

			val lookupResult = runBlocking {
				withContext(Dispatchers.IO) {
					try {
						this@NavicDnsResolver.lookup(host)
					} catch (_: Exception) {
						emptyList()
					}
				}
			}

			val localResolution = lookupResult.firstOrNull { it.isLocalOrPrivate() }

			val alternateAddress =
				accountSettings.alternateServerAddresses.find { it.host == localResolution?.hostAddress }
					?: return@Interceptor chain.proceed(request)

			Logger.i(TAG, "currently using $alternateAddress as the address")

			val newUrl = request.url.newBuilder()
				.apply {
					host(alternateAddress.host)

					val basePath = alternateAddress.basePath.takeIf { !it.isNullOrBlank() }

					if (basePath != null) {
						encodedPath(basePath)
					}
				}
				.build(
					scheme = alternateAddress.scheme.name,
					alternateAddress.port
				)

			if (localResolution != null) {
				request = request
					.newBuilder()
					.url(newUrl)
					.build()
			}

			chain.proceed(request)
		}

	private fun HttpUrl.Builder.build(scheme: String, port: Int? = null): HttpUrl {
		this.scheme(scheme)
		if (port != null) {
			this.port(port)
		}

		return this.build()
	}

	fun InetAddress.isLocalOrPrivate(): Boolean {
		return this.isLoopbackAddress || this.isSiteLocalAddress || this.isLinkLocalAddress
	}
}
