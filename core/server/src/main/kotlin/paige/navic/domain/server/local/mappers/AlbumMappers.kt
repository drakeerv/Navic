/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.server.local.mappers

import org.oxycblt.musikr.Album
import org.oxycblt.musikr.tag.Name
import paige.navic.database.entity.AlbumEntity
import paige.navic.domain.server.local.getOrNull
import paige.navic.util.xxHash32
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

fun Album.toEntity(): AlbumEntity {
	val name = this.name.getOrNull()
	val artist = this.artists.firstOrNull()
		?.name
		?.getOrNull()

	return AlbumEntity(
		albumId = this.uid.toString(),
		name = name?.raw,
		artistName = artist?.raw,
		artistId = artist?.raw?.xxHash32() ?: "",
		displayArtist = this.artists.joinToString { artistItem ->
			(artistItem.name as? Name.Known)?.raw.orEmpty()
		},
		year = this.dates?.min?.year,
		coverArtId = this.covers.covers.firstOrNull()?.id,
		genre = this.songs.flatMap { it.genres }.firstOrNull()
			?.let { (it.name as? Name.Known)?.raw },
		genres = this.songs.flatMap { it.genres }.mapNotNull {
			(it.name as? Name.Known)?.raw
		}.distinct(),
		songCount = this.songs.size,
		duration = this.durationMs.milliseconds,
		createdAt = Instant.fromEpochMilliseconds(this.addedMs),
		starredAt = null,
		lastPlayedAt = null,
		userRating = null,
		version = null,
		musicBrainzId = null,
		isExternal = false
	)
}
