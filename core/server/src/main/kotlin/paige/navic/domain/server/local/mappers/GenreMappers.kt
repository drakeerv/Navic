/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.server.local.mappers

import org.oxycblt.musikr.Genre
import paige.navic.database.entity.GenreEntity
import paige.navic.domain.server.local.getOrNull

fun Genre.toEntity(): GenreEntity? {
	return GenreEntity(
		genreName = this.name.getOrNull()?.raw ?: return null,
		albumCount = this.artists.sumOf { it.explicitAlbums.size },
		songCount = this.songs.size
	)
}
