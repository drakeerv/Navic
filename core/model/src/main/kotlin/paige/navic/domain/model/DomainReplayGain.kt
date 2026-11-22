/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class DomainReplayGain(
	val albumGain: Float? = null,
	val albumPeak: Float? = null,
	val trackGain: Float? = null,
	val trackPeak: Float? = null,
	val baseGain: Float? = null,
	val fallbackGain: Float? = null
)
