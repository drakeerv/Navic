/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.server.local.mappers

import paige.navic.database.entity.GenreEntity
import paige.navic.database.entity.LocalScanGenreMapEntity

fun LocalScanGenreMapEntity.toEntity(songCount: Int, albumCount: Int): GenreEntity? {
	val name = displayName ?: return null
	return GenreEntity(genreName = name, albumCount = albumCount, songCount = songCount)
}
