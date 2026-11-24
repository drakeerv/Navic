/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.server

enum class ServerFeature(
	val prettyName: String
) {
	TRANSCODE_DECISION("Transcode decision"),
	TRANSCODE_OFFSET("Transcode offset"),
	PLAYBACK_REPORT("Playback report"),
	CHAT("Chat")
}

enum class ServerType(
	val prettyName: String
) {
	SUBSONIC("Subsonic"),
	NAVIDROME("Navidrome"),
	JELLYFIN("Jellyfin"),
	LOCAL("Local")
}

data class ServerInfo(
	val type: ServerType,
	val version: String? = null,
	val features: Set<ServerFeature> = emptySet(),
	/** Provider-specific diagnostics that do not belong in the generic model. */
	val details: Map<String, String> = emptyMap()
)
