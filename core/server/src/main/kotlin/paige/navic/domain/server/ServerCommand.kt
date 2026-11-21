/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.server

sealed interface ServerCommand {
	data class DisplayMessage(
		val message: String
	) : ServerCommand

	sealed interface PlaybackCommand : ServerCommand {
		data object Stop : PlaybackCommand
		data object Pause : PlaybackCommand
		data object Resume : PlaybackCommand
		data object Toggle : PlaybackCommand
		data object NextTrack : PlaybackCommand
		data object PreviousTrack : PlaybackCommand
		data class Seek(val positionMs: Long) : PlaybackCommand
	}
}
