/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.server.local.mappers

import paige.navic.database.dao.ScanAlbumAggregate
import paige.navic.database.dao.ScanAlbumArtistRow
import paige.navic.database.entity.AlbumEntity
import paige.navic.util.xxHash32
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

fun ScanAlbumAggregate.toEntity(
	artists: List<ScanAlbumArtistRow>,
	genres: List<String>,
	coverArtId: String?,
	existing: AlbumEntity? = null
): AlbumEntity {
	val distinctArtists = artists.distinctBy { it.artistKey }
	val firstArtist = distinctArtists.firstOrNull()?.displayName
	return AlbumEntity(
		albumId = albumId,
		name = displayName,
		artistName = firstArtist,
		artistId = firstArtist?.xxHash32() ?: "",
		displayArtist = distinctArtists.map { it.displayName.orEmpty() }.joinToString(),
		year = year,
		coverArtId = coverArtId,
		genre = genres.firstOrNull(),
		genres = genres,
		songCount = songCount,
		duration = durationMs.milliseconds,
		createdAt = Instant.fromEpochMilliseconds(addedMs),
		starredAt = existing?.starredAt,
		lastPlayedAt = existing?.lastPlayedAt,
		playCount = existing?.playCount ?: 0,
		userRating = existing?.userRating,
		version = null,
		musicBrainzId = null,
		isExternal = false
	)
}
