/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.server.jellyfin.mapper

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType

fun BaseItemDto.withCoverVersion(fallbackId: String): String =
	imageTags?.get(ImageType.PRIMARY)
		?.takeIf { it.isNotBlank() }
		?.let { "$fallbackId#$it" }
		?: fallbackId
