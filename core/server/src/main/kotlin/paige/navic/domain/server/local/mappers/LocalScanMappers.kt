/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.server.local.mappers

import android.webkit.MimeTypeMap
import org.oxycblt.musikr.tag.Name
import org.oxycblt.musikr.tag.interpret.PreArtist
import org.oxycblt.musikr.tag.interpret.PreArtistsFrom
import org.oxycblt.musikr.tag.interpret.PreGenre
import org.oxycblt.musikr.tag.interpret.PreSong
import paige.navic.database.entity.LocalScanArtistEntity
import paige.navic.database.entity.LocalScanGenreEntity
import paige.navic.database.entity.LocalScanSongEntity
import kotlin.time.Duration.Companion.milliseconds

internal const val ROLE_TRACK = 0
internal const val ROLE_ALBUM = 1

fun PreSong.toLocalScanSong(): LocalScanSongEntity {
	val album = preAlbum
	return LocalScanSongEntity(
		songId = v363Uid.toString(),
		v400Id = v400Uid.toString(),
		v401Id = v401Uid.toString(),
		title = name.raw,
		track = track,
		discNumber = disc?.number,
		discSubtitle = disc?.name,
		year = date?.year,
		duration = durationMs.milliseconds,
		bitrateKbps = bitrateKbps,
		sampleRateHz = sampleRateHz,
		bitDepth = bitDepth,
		audioChannelCount = channelCount,
		replayGainAlbum = replayGainAdjustment.album,
		replayGainTrack = replayGainAdjustment.track,
		replayGainAlbumPeak = replayGainAdjustment.albumPeak,
		replayGainTrackPeak = replayGainAdjustment.trackPeak,
		fileSize = size,
		mimeType = format.mimeType,
		fileExtension = MimeTypeMap.getSingleton().getExtensionFromMimeType(format.mimeType),
		filePath = uri.toString(),
		addedMs = addedMs,
		coverId = cover?.id,
		albumOccurrenceKey = album.identityKey,
		albumOccurrenceKeyNoMbid = album.identityKeyNoMbid,
		albumNameKey = album.rawName?.lowercase().orEmpty(),
		albumMbid = album.musicBrainzId?.toString(),
		albumArtistSource = if (album.preArtists is PreArtistsFrom.Album) 1 else 0,
		albumArtistRawNamesKey = album.albumArtistRawNamesKey,
		albumArtistIdentityKey = album.preArtists.preArtists.joinToString("\u001f") {
			it.identityKey
		},
		albumId = album.uid.toString(),
		albumIdNoMbid = album.uidNoMbid.toString(),
		albumDisplayName = album.name.knownRaw(),
		albumRawName = album.rawName
	)
}

fun PreArtist.toLocalScanArtist(scanSongId: Long, role: Int, position: Int) =
	LocalScanArtistEntity(
		scanSongId = scanSongId,
		role = role,
		position = position,
		occurrenceKey = identityKey,
		nameKey = rawName?.lowercase().orEmpty(),
		mbid = musicBrainzId?.toString(),
		rawName = rawName,
		displayName = name.knownRaw(),
		sortName = name.knownSort(),
		uid = uid.toString(),
		uidNoMbid = uidNoMbid.toString()
	)

fun PreGenre.toLocalScanGenre(scanSongId: Long, position: Int) =
	LocalScanGenreEntity(
		scanSongId = scanSongId,
		position = position,
		genreKey = rawName?.lowercase().orEmpty(),
		rawName = rawName,
		displayName = name.knownRaw()
	)

internal fun Name.knownRaw(): String? = (this as? Name.Known)?.raw

internal fun Name.knownSort(): String? = (this as? Name.Known)?.sort
