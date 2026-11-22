/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.server.local

import kotlin.time.Instant
import kotlin.time.Duration.Companion.milliseconds
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.Song
import org.oxycblt.musikr.playlist.PlaylistFile
import org.oxycblt.musikr.playlist.PlaylistHandle
import org.oxycblt.musikr.playlist.SongPointer
import org.oxycblt.musikr.playlist.db.StoredPlaylists
import paige.navic.database.dao.PlaylistDao
import paige.navic.database.entity.PlaylistEntity
import paige.navic.database.entity.PlaylistSongCrossRef

class NavicStoredPlaylists(
	private val playlistDao: PlaylistDao
) : StoredPlaylists() {
	override suspend fun new(name: String, songs: List<Song>): PlaylistHandle {
		val uid = Music.UID.auxio(Music.UID.Item.PLAYLIST)
		val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
		playlistDao.insertPlaylist(
			PlaylistEntity(
				playlistId = uid.toString(),
				name = name,
				comment = null,
				owner = "Local",
				coverArtId = null,
				songCount = songs.size,
				duration = songs.sumOf { it.durationMs }.milliseconds,
				public = false,
				readOnly = false,
				createdAt = now,
				modifiedAt = now,
				validUntil = null,
				allowedUsers = emptyList()
			)
		)
		playlistDao.replacePlaylistSongs(
			uid.toString(),
			songs.mapIndexed { position, song ->
				PlaylistSongCrossRef(uid.toString(), song.uid.toString(), position)
			}
		)
		return NavicPlaylistHandle(uid, playlistDao)
	}

	override suspend fun read(): List<PlaylistFile> =
		playlistDao.getPlaylistsByIds(playlistDao.getAllPlaylistIds()).map { relation ->
			val uid = Music.UID.fromString(relation.playlist.playlistId)
				?: return@map null
			PlaylistFile(
				name = relation.playlist.name.orEmpty(),
				songPointers = relation.songs.mapNotNull { song ->
					Music.UID.fromString(song.crossRef.songId)?.let(SongPointer::UID)
				},
				handle = NavicPlaylistHandle(uid, playlistDao)
			)
		}.filterNotNull()
}

private class NavicPlaylistHandle(
	override val uid: Music.UID,
	private val playlistDao: PlaylistDao
) : PlaylistHandle {
	override suspend fun rename(name: String) {
		val existing = playlistDao.getPlaylistEntityById(uid.toString()) ?: return
		playlistDao.insertPlaylist(
			existing.copy(
				name = name,
				modifiedAt = Instant.fromEpochMilliseconds(System.currentTimeMillis())
			)
		)
	}

	override suspend fun add(songs: List<Song>) {
		val existing = playlistDao.getPlaylistById(uid.toString()) ?: return
		val start = existing.songs.size
		playlistDao.insertPlaylistSongCrossRefs(
			songs.mapIndexed { index, song ->
				PlaylistSongCrossRef(uid.toString(), song.uid.toString(), start + index)
			}
		)
	}

	override suspend fun rewrite(songs: List<Song>) {
		val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
		val existing = playlistDao.getPlaylistEntityById(uid.toString()) ?: return
		playlistDao.replacePlaylistSongs(
			uid.toString(),
			songs.mapIndexed { position, song ->
				PlaylistSongCrossRef(uid.toString(), song.uid.toString(), position)
			}
		)
		playlistDao.insertPlaylist(
			existing.copy(
				songCount = songs.size,
				duration = songs.sumOf { it.durationMs }.milliseconds,
				modifiedAt = now
			)
		)
	}

	override suspend fun delete() {
		playlistDao.deletePlaylist(uid.toString())
	}
}
