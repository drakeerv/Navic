/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.server

import paige.navic.domain.model.DomainUser

interface MusicProvider {
	suspend fun ping()

	suspend fun serverInfo(): ServerInfo

	suspend fun currentUser(): DomainUser

	suspend fun getPlaybackStreamUrl(
		id: String,
		bitrateKbps: Int = 0,
		format: String? = null,
		skipTranscodeDecisions: Boolean = false
	): String

	fun getHlsStreamUrl(id: String, bitrateKbps: Int, format: String?): String? = null

	fun getCoverArtUrl(id: String, size: String? = null): String

	suspend fun getCoverArt(id: String, size: String? = null): ByteArray

	fun getAvatarUrl(username: String): String? = null
}
