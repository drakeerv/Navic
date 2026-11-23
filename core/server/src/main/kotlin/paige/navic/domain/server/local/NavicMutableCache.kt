/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.server.local

import org.oxycblt.musikr.cache.Audio
import org.oxycblt.musikr.cache.CacheResult
import org.oxycblt.musikr.cache.CachedFile
import org.oxycblt.musikr.cache.MutableCache
import org.oxycblt.musikr.fs.File
import org.oxycblt.musikr.metadata.Properties
import org.oxycblt.musikr.tag.Date
import org.oxycblt.musikr.tag.parse.ParsedTags
import paige.navic.database.dao.SongDao
import paige.navic.database.entity.SongEntity
import paige.navic.domain.model.DomainExplicitStatus
import paige.navic.domain.model.DomainReplayGain
import paige.navic.domain.model.DomainSongArtist
import paige.navic.util.xxHash32
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class NavicMutableCache(
	private val songDao: SongDao
) : MutableCache {
	override suspend fun read(file: File): CacheResult {
		val entity = songDao.getSongByFilePath(file.uri.toString()) ?: return CacheResult.Miss(file)
		val durationMs = entity.duration.inWholeMilliseconds
		val properties = Properties(
			mimeType = entity.mimeType ?: file.mimeType,
			durationMs = durationMs,
			bitrateKbps = entity.bitRate ?: 0,
			sampleRateHz = entity.sampleRate ?: 0,
			channelCount = entity.audioChannelCount ?: 0,
			bitDepth = entity.bitDepth ?: 0
		)
		val tags = ParsedTags(
			durationMs = durationMs,
			replayGainTrackAdjustment = entity.replayGain?.trackGain,
			replayGainAlbumAdjustment = entity.replayGain?.albumGain,
			replayGainTrackPeak = entity.replayGain?.trackPeak,
			replayGainAlbumPeak = entity.replayGain?.albumPeak,
			musicBrainzId = entity.musicBrainzId,
			name = entity.title,
			sortName = null,
			track = entity.trackNumber,
			disc = entity.discNumber,
			subtitle = null,
			date = entity.year?.let { Date.from(it.toString()) },
			albumMusicBrainzId = null,
			albumName = entity.albumTitle,
			albumSortName = null,
			releaseTypes = emptyList(),
			artistMusicBrainzIds = emptyList(),
			artistNames = entity.artists.map { it.name },
			artistSortNames = emptyList(),
			albumArtistMusicBrainzIds = emptyList(),
			albumArtistNames = entity.albumArtists.map { it.name },
			albumArtistSortNames = emptyList(),
			genreNames = entity.genres
		)
		return CacheResult.Hit(
			CachedFile(
				file = file,
				audio = Audio(properties, tags, entity.coverArtId),
				addedMs = entity.addedAt?.toEpochMilliseconds() ?: System.currentTimeMillis()
			)
		)
	}

	override suspend fun write(cachedFile: CachedFile) {
		val audio = cachedFile.audio ?: return
		val file = cachedFile.file
		val title = audio.tags.name ?: file.path.name.orEmpty()
		val artistName = audio.tags.artistNames.firstOrNull()?.takeIf { it.isNotBlank() }
		val albumTitle = audio.tags.albumName?.takeIf { it.isNotBlank() }
		val artistId = artistName?.xxHash32() ?: "unknown"
		val albumId = albumTitle?.let { "${it.xxHash32()}-$artistId" }
		val entity = SongEntity(
			songId = file.uri.toString(),
			title = title,
			artistName = artistName,
			artistId = artistId,
			albumTitle = albumTitle,
			belongsToAlbumId = albumId,
			parentId = null,
			comment = null,
			trackNumber = audio.tags.track,
			discNumber = audio.tags.disc,
			isrc = emptyList(),
			year = audio.tags.date?.year,
			genre = audio.tags.genreNames.firstOrNull(),
			genres = audio.tags.genreNames,
			moods = emptyList(),
			duration = audio.properties.durationMs.milliseconds,
			bpm = null,
			contributors = emptyList(),
			playCount = 0,
			userRating = null,
			averageRating = null,
			bitRate = audio.properties.bitrateKbps,
			bitDepth = audio.properties.bitDepth.takeIf { it > 0 },
			sampleRate = audio.properties.sampleRateHz,
			audioChannelCount = audio.properties.channelCount.takeIf { it > 0 },
			replayGain = DomainReplayGain(
				albumGain = audio.tags.replayGainAlbumAdjustment,
				albumPeak = audio.tags.replayGainAlbumPeak,
				trackGain = audio.tags.replayGainTrackAdjustment,
				trackPeak = audio.tags.replayGainTrackPeak
			),
			fileSize = file.size,
			fileExtension = null,
			mimeType = audio.properties.mimeType,
			filePath = file.uri.toString(),
			addedAt = Instant.fromEpochMilliseconds(cachedFile.addedMs),
			starredAt = null,
			coverArtId = audio.coverId,
			musicBrainzId = audio.tags.musicBrainzId,
			explicitStatus = DomainExplicitStatus.Unknown,
			artists = audio
				.tags
				.artistNames
				.filter { it.isNotBlank() }
				.map { name ->
					DomainSongArtist(
						id = name.xxHash32(),
						name = name
					)
				},
			albumArtists = audio
				.tags
				.albumArtistNames
				.filter { it.isNotBlank() }.map { name ->
					DomainSongArtist(
						id = name.xxHash32(),
						name = name
					)
				},
			isExternal = false
		)
		songDao.insertSong(entity)
	}

	override suspend fun writeAll(cachedFiles: List<CachedFile>) {
		cachedFiles.forEach { write(it) }
	}

	override suspend fun cleanup(excluding: List<CachedFile>) {
		val keep = excluding.mapTo(mutableSetOf()) { it.file.uri.toString() }
		if (keep.isEmpty()) {
			songDao.clearAllSongs()
			return
		}
		songDao.getAllSongPaths()
			.filter { it.filePath == null || it.filePath !in keep }
			.forEach { songDao.deleteSong(it.songId) }
	}
}
