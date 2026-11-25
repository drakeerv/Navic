/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.server.local

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import org.oxycblt.musikr.fs.Path
import org.oxycblt.musikr.fs.Volume
import org.oxycblt.musikr.playlist.ExternalPlaylistManager
import org.oxycblt.musikr.playlist.M3UPlaylistImport
import paige.navic.database.dao.PlaylistDao
import paige.navic.database.dao.SongDao
import paige.navic.database.entity.PendingPlaylistEntryEntity
import paige.navic.database.entity.PlaylistEntity
import paige.navic.database.entity.PlaylistSongCrossRef
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class NavicM3uPlaylistPersistence(
	private val context: Context,
	private val playlistDao: PlaylistDao,
	private val songDao: SongDao
) {
	private val manager = ExternalPlaylistManager.from(context)

	suspend fun import(uri: Uri): Boolean {
		val imported = manager.import(uri) ?: return false
		val pathToSong = songDao.getAllSongPaths().mapNotNull { song ->
			song.filePath?.let { it.toPlaylistPathKey(context) to song }
		}.toMap()
		val name = imported.name?.takeIf(String::isNotBlank)
			?: uri.lastPathSegment.orEmpty().substringBeforeLast('.').ifBlank { "Imported playlist" }
		val playlistId = "local_m3u_import_${UUID.randomUUID()}"
		persist(
			playlistId = playlistId,
			name = name,
			entries = imported.paths,
			pathToSongId = pathToSong.mapValues { it.value.songId },
			songDurations = pathToSong.values.associate { it.songId to it.duration }
		)
		return true
	}

	suspend fun syncScanImports(
		imports: List<M3UPlaylistImport>,
		scannedPathToSongId: Map<String, String>
	) {
		val existingScanIds = playlistDao.getPlaylistIdsByPrefix(SCAN_PLAYLIST_PREFIX)
		val activeIds = imports.mapTo(mutableSetOf()) { scanPlaylistId(it.sourceUri) }
		val songsById = songDao.getAllSongPaths().associateBy { it.songId }
		val durations = songsById.mapValues { it.value.duration }

		for (import in imports.filter { it.parsed }) {
			persist(
				playlistId = scanPlaylistId(import.sourceUri),
			name = import.name?.takeIf(String::isNotBlank)
				?: import.sourceUri.toUri().lastPathSegment.orEmpty().substringBeforeLast('.').ifBlank { "Imported playlist" },
				entries = import.entries,
				pathToSongId = scannedPathToSongId,
				songDurations = durations
			)
		}

		for (playlistId in existingScanIds - activeIds) {
			playlistDao.deletePlaylist(playlistId)
		}

		resolvePendingEntries(scannedPathToSongId, durations)
	}

	private suspend fun persist(
		playlistId: String,
		name: String,
		entries: List<List<Path>>,
		pathToSongId: Map<String, String>,
		songDurations: Map<String, Duration>
	) {
		val resolved = mutableListOf<PlaylistSongCrossRef>()
		val pending = mutableListOf<PendingPlaylistEntryEntity>()
		entries.forEachIndexed { position, candidates ->
			val keys = candidates.map(Path::toPlaylistPathKey).distinct()
			val songId = keys.firstNotNullOfOrNull(pathToSongId::get)
			if (songId == null) {
				keys.forEachIndexed { index, key ->
					pending += PendingPlaylistEntryEntity(playlistId, position, index, key)
				}
			} else {
				resolved += PlaylistSongCrossRef(playlistId, songId, position)
			}
		}
		val now = Clock.System.now()
		val duration = resolved.sumOf { songDurations[it.songId]?.inWholeMilliseconds ?: 0L }
		val playlist = PlaylistEntity(
			playlistId = playlistId,
			name = name,
			comment = null,
			owner = null,
			coverArtId = null,
			songCount = resolved.size,
			duration = duration.milliseconds,
			public = false,
			readOnly = false,
			createdAt = playlistDao.getPlaylistEntityById(playlistId)?.createdAt ?: now,
			modifiedAt = now,
			validUntil = null,
			allowedUsers = emptyList()
		)
		playlistDao.replaceImportedPlaylist(playlist, resolved, pending)
	}

	private suspend fun resolvePendingEntries(
		pathToSongId: Map<String, String>,
		songDurations: Map<String, Duration>
	) {
		val pendingByPlaylist = playlistDao.getAllPendingPlaylistEntries().groupBy { it.playlistId }
		for ((playlistId, entries) in pendingByPlaylist) {
			val current = playlistDao.getPlaylistById(playlistId) ?: continue
			val matched = entries.groupBy { it.position }.mapNotNull { (position, candidates) ->
				candidates.sortedBy { it.candidateIndex }
					.firstNotNullOfOrNull { pathToSongId[it.pathKey] }
					?.let { PlaylistSongCrossRef(playlistId, it, position) }
			}
			if (matched.isEmpty()) continue
			val resolvedRefs = (current.songs.map { it.crossRef } + matched)
				.distinctBy { it.position }
				.sortedBy { it.position }
			val resolvedPositions = matched.mapTo(mutableSetOf()) { it.position }
			val remaining = entries.filter { it.position !in resolvedPositions }
			val ids = resolvedRefs.map { it.songId }
			val duration = ids.sumOf { songDurations[it]?.inWholeMilliseconds ?: 0L }
			val entity = current.playlist.copy(
				songCount = resolvedRefs.size,
				duration = duration.milliseconds,
				modifiedAt = Clock.System.now()
			)
			playlistDao.replacePlaylistContents(entity, resolvedRefs, remaining)
		}
	}

	private fun scanPlaylistId(uri: String) =
		SCAN_PLAYLIST_PREFIX + UUID.nameUUIDFromBytes(uri.toByteArray())

	private companion object {
		const val SCAN_PLAYLIST_PREFIX = "local_m3u_scan_"
	}
}

internal fun Path.toPlaylistPathKey(): String =
	"${volume.playlistVolumeKey()}|${components.components.joinToString("/")}"

private fun Volume.playlistVolumeKey(): String = when (this) {
	is Volume.Internal -> "primary"
	is Volume.External -> id?.lowercase() ?: "external"
	is Volume.ThirdParty -> "thirdparty:${uri.authority.orEmpty()}"
}

private fun String.toPlaylistPathKey(context: Context): String {
	val uri = toUri()
	val documentId = runCatching {
		if (DocumentsContract.isDocumentUri(context, uri)) DocumentsContract.getDocumentId(uri) else null
	}.getOrNull()
	if (documentId != null) {
		val volume = documentId.substringBefore(':')
		val path = documentId.substringAfter(':', "")
		return "${volume.lowercase()}|$path"
	}
	val path = uri.path.orEmpty().trimStart('/')
	val internalPrefix = "storage/emulated/0/"
	if (path.startsWith(internalPrefix)) return "primary|${path.removePrefix(internalPrefix)}"
	return "thirdparty:${uri.authority.orEmpty()}|$path"
}
