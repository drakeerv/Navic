/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.server.local.mappers

import android.webkit.MimeTypeMap
import org.oxycblt.musikr.Song
import paige.navic.database.entity.SongEntity
import paige.navic.domain.model.DomainExplicitStatus
import paige.navic.domain.model.DomainReplayGain
import paige.navic.domain.model.DomainSongArtist
import paige.navic.domain.server.local.getOrNull
import paige.navic.util.xxHash32
import kotlin.time.Duration.Companion.milliseconds

fun Song.toEntity(): SongEntity? {
	return SongEntity(
		songId = this.uid.toString(),
		title = this.name.raw,
		artistName = this.artists.firstOrNull()
			?.name?.getOrNull()?.raw,
		artistId = artists.firstOrNull()
			?.name
			?.getOrNull()
			?.raw
			?.xxHash32() ?: return null,
		albumTitle = album.name.getOrNull()?.raw,
		belongsToAlbumId = album.uid.toString(),
		parentId = album.uid.toString(),
		comment = null,
		trackNumber = track,
		discNumber = disc?.number,
		isrc = emptyList(),
		year = date?.year,
		genre = genres.firstOrNull()
			?.let { it.name.getOrNull()?.raw },
		genres = genres.mapNotNull { it.name.getOrNull()?.raw },
		moods = emptyList(),
		duration = durationMs.milliseconds,
		bpm = null,
		contributors = emptyList(),
		playCount = 0,
		userRating = null,
		averageRating = null,
		bitRate = bitrateKbps,
		bitDepth = null,
		sampleRate = sampleRateHz,
		audioChannelCount = null,
		replayGain = DomainReplayGain(
			albumGain = replayGainAdjustment.album,
			trackGain = replayGainAdjustment.track
		),
		fileSize = size,
		fileExtension = MimeTypeMap.getSingleton()
			.getExtensionFromMimeType(format.mimeType),
		mimeType = format.mimeType,
		filePath = uri.toString(),
		starredAt = null,
		coverArtId = cover?.id,
		musicBrainzId = null,
		explicitStatus = DomainExplicitStatus.Unknown,
		artists = artists.map { artist ->
			val name = artist.name.getOrNull()?.raw.orEmpty()

			DomainSongArtist(
				id = name.xxHash32(),
				name = name
			)
		}.filter { it.name.isNotBlank() },
		albumArtists = album.artists.map { artist ->
			val name = artist.name.getOrNull()?.raw.orEmpty()

			DomainSongArtist(
				id = name.xxHash32(),
				name = name
			)
		}.filter { it.name.isNotBlank() },
		isExternal = false
	)
}
