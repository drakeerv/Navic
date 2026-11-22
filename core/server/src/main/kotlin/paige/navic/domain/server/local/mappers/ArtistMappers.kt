/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.server.local.mappers

import org.oxycblt.musikr.Artist
import paige.navic.database.entity.ArtistEntity
import paige.navic.domain.model.DomainSongArtist
import paige.navic.domain.server.local.getOrNull
import paige.navic.util.xxHash32

fun Artist.toEntity(): ArtistEntity {
	val name = this.name.getOrNull()

	return ArtistEntity(
		artistId = name?.raw?.xxHash32() ?: "",
		name = name?.raw ?: "",
		albumCount = this.explicitAlbums.size,
		coverArtId = null,
		artistImageUrl = null,
		starredAt = null,
		userRating = null,
		sortName = name?.sort,
		musicBrainzId = null,
		lastFmUrl = null,
		roles = emptyList(),
		biography = null,
		similarArtistIds = emptyList()
	)
}

fun Artist.toSongArtistEntity(): DomainSongArtist? {
	val name = this.name.getOrNull() ?: return null

	return DomainSongArtist(
		id = name.raw.xxHash32(),
		name = name.raw
	)
}
