/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.model.settings

import kotlinx.serialization.Serializable

@Serializable
enum class ServerScheme(val isSecure: Boolean) {
	HTTP(false), HTTPS(true);

	fun getKnownPort() = when (this) {
		HTTP -> 80 // there is also 8080 but
		HTTPS -> 443
	}
}

@Serializable
data class ServerAddress(
	val scheme: ServerScheme,
	val host: String,
	val basePath: String? = null,
	private val configuredPort: Int? = null
) {
	val port: Int
		get() = configuredPort ?: scheme.getKnownPort()
}
