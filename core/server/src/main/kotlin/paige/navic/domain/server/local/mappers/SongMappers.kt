/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.server.local.mappers

import paige.navic.database.dao.ScanAlbumArtistRow
import paige.navic.database.dao.ScanArtistRow
import paige.navic.database.dao.ScanGenreRow
import paige.navic.database.dao.ScanSongAlbumRow
import paige.navic.database.entity.LocalScanSongEntity
import paige.navic.database.entity.SongEntity
import paige.navic.domain.model.DomainExplicitStatus
import paige.navic.domain.model.DomainReplayGain
import paige.navic.domain.model.DomainSongArtist
import paige.navic.util.xxHash32
import kotlin.time.Instant

fun LocalScanSongEntity.toEntity(
	album: ScanSongAlbumRow,
	trackArtists: List<ScanArtistRow>,
	albumArtists: List<ScanAlbumArtistRow>,
	genres: List<ScanGenreRow>,
	existing: SongEntity? = null
): SongEntity? {
	val firstArtist = trackArtists.firstOrNull() ?: return null
	val firstName = firstArtist.displayName ?: return null
	val genreNames = genres.mapNotNull { it.displayName }.distinct()
	return SongEntity(
		songId = songId,
		title = title,
		artistName = firstName,
		artistId = firstName.xxHash32(),
		albumTitle = album.displayName,
		belongsToAlbumId = album.albumId,
		parentId = album.albumId,
		comment = null,
		trackNumber = track,
		discNumber = discNumber,
		isrc = emptyList(),
		year = year,
		genre = genreNames.firstOrNull(),
		genres = genreNames,
		moods = emptyList(),
		duration = duration,
		bpm = null,
		contributors = emptyList(),
		playCount = existing?.playCount ?: 0,
		userRating = existing?.userRating,
		averageRating = null,
		bitRate = bitrateKbps,
		bitDepth = bitDepth,
		sampleRate = sampleRateHz,
		audioChannelCount = audioChannelCount,
		replayGain = DomainReplayGain(
			albumGain = replayGainAlbum,
			albumPeak = replayGainAlbumPeak,
			trackGain = replayGainTrack,
			trackPeak = replayGainTrackPeak
		),
		fileSize = fileSize,
		fileExtension = fileExtension,
		mimeType = mimeType,
		filePath = filePath,
		addedAt = Instant.fromEpochMilliseconds(addedMs),
		starredAt = existing?.starredAt,
		coverArtId = coverId,
		musicBrainzId = null,
		explicitStatus = DomainExplicitStatus.Unknown,
		artists = trackArtists.distinctBy { it.artistKey }.mapNotNull { it.toDomainArtist() },
		albumArtists = albumArtists.distinctBy { it.artistKey }.mapNotNull { it.toDomainArtist() },
		isExternal = false
	)
}

fun ScanArtistRow.toDomainArtist(): DomainSongArtist? {
	val name = displayName ?: return null
	if (name.isBlank()) return null
	return DomainSongArtist(id = name.xxHash32(), name = name)
}

fun ScanAlbumArtistRow.toDomainArtist(): DomainSongArtist? {
	val name = displayName ?: return null
	if (name.isBlank()) return null
	return DomainSongArtist(id = name.xxHash32(), name = name)
}
