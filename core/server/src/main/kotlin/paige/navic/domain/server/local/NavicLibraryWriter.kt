/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.server.local

import androidx.room3.withWriteTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.toList
import org.oxycblt.musikr.LibraryWriter
import org.oxycblt.musikr.tag.interpret.PreSong
import paige.navic.database.CacheDatabase
import paige.navic.database.dao.AlbumDao
import paige.navic.database.dao.ArtistDao
import paige.navic.database.dao.GenreDao
import paige.navic.database.dao.LocalScanDao
import paige.navic.database.dao.ScanAlbumFirstRow
import paige.navic.database.dao.ScanArtistFirstRow
import paige.navic.database.dao.SongDao
import paige.navic.database.entity.LocalScanAlbumMapEntity
import paige.navic.database.entity.LocalScanArtistRoleMapEntity
import paige.navic.domain.server.local.mappers.ROLE_ALBUM
import paige.navic.domain.server.local.mappers.ROLE_TRACK
import paige.navic.domain.server.local.mappers.entityId
import paige.navic.domain.server.local.mappers.toEntity
import paige.navic.domain.server.local.mappers.toLocalScanArtist
import paige.navic.domain.server.local.mappers.toLocalScanGenre
import paige.navic.domain.server.local.mappers.toLocalScanSong

class NavicLibraryWriter(
	private val cacheDatabase: CacheDatabase,
	private val localScanDao: LocalScanDao,
	private val songDao: SongDao,
	private val albumDao: AlbumDao,
	private val artistDao: ArtistDao,
	private val genreDao: GenreDao
) : LibraryWriter {
	override suspend fun begin() {
		cacheDatabase.withWriteTransaction {
			clearStaging()
		}
	}

	override suspend fun write(song: PreSong) {
		val scanSongId = localScanDao.insertSong(song.toLocalScanSong())
		val artists = buildList {
			song.preArtists.forEachIndexed { position, artist ->
				add(artist.toLocalScanArtist(scanSongId, ROLE_TRACK, position))
			}
			song.preAlbum.preArtists.preArtists.forEachIndexed { position, artist ->
				add(artist.toLocalScanArtist(scanSongId, ROLE_ALBUM, position))
			}
		}
		if (artists.isNotEmpty()) {
			localScanDao.insertArtists(artists)
		}

		val genres = song.preGenres.mapIndexed { position, genre ->
			genre.toLocalScanGenre(scanSongId, position)
		}
		if (genres.isNotEmpty()) {
			localScanDao.insertGenres(genres)
		}
	}

	override suspend fun finish(): Set<String> {
		val validSongIds = mutableSetOf<String>()
		val validAlbumIds = mutableSetOf<String>()
		val validArtistIds = mutableSetOf<String>()
		val validGenreNames = mutableSetOf<String>()
		val usedCovers = mutableSetOf<String>()

		cacheDatabase.withWriteTransaction {
			localScanDao.deleteDuplicateSongs()
			localScanDao.deleteOrphanArtists()
			localScanDao.deleteOrphanGenres()

			localScanDao.buildGenreMap()
			localScanDao.buildArtistKeys()
			localScanDao.buildAlbumKeys()
			buildArtistMaps()
			buildAlbumMaps()

			materializeSongs(validSongIds, usedCovers)
			materializeArtists(validArtistIds)
			materializeAlbums(validAlbumIds)
			materializeGenres(validGenreNames)

			songDao.deleteObsoleteSongs(validSongIds)
			albumDao.deleteObsoleteAlbums(validAlbumIds)
			artistDao.deleteObsoleteArtists(validArtistIds)
			genreDao.deleteObsoleteGenres(validGenreNames)

			clearStaging()
		}

		return usedCovers
	}

	private suspend fun materializeSongs(
		validSongIds: MutableSet<String>,
		usedCovers: MutableSet<String>
	) {
		var afterId = 0L
		while (true) {
			val songs = localScanDao.getSongBatch(afterId, SONG_BATCH_SIZE)
			if (songs.isEmpty()) break
			afterId = songs.last().id
			val ids = songs.map { it.id }
			val albums = localScanDao.getSongAlbums(ids).associateBy { it.scanSongId }
			val artists = localScanDao.getSongArtists(ids).groupBy { it.scanSongId }
			val genres = localScanDao.getSongGenres(ids).groupBy { it.scanSongId }
			val albumKeys = albums.values.map { it.albumKey }.distinct()
			val albumArtists =
				if (albumKeys.isEmpty()) {
					emptyMap()
				} else {
					localScanDao.getAlbumArtists(albumKeys).groupBy { it.albumKey }
				}
			val existingSongs = songDao.getSongsByIds(songs.map { it.songId })
				.associateBy { it.songId }

			val entities =
				songs.mapNotNull { song ->
					val album = albums[song.id] ?: return@mapNotNull null
					val entity =
						song.toEntity(
							album = album,
							trackArtists = artists[song.id].orEmpty()
								.filter { it.role == ROLE_TRACK },
							albumArtists = albumArtists[album.albumKey].orEmpty(),
							genres = genres[song.id].orEmpty(),
							existing = existingSongs[song.songId]
						) ?: return@mapNotNull null
					song.coverId?.let(usedCovers::add)
					entity
				}
			if (entities.isNotEmpty()) {
				songDao.insertSongs(entities)
				validSongIds.addAll(entities.map { it.songId })
			}
		}
	}

	private suspend fun buildArtistMaps() {
		val clusterCounts = localScanDao.getArtistClusterCounts()
			.associate { Triple(it.finalKey, it.role, it.nameKey) to it.songCount }
		val firstClusters =
			localScanDao.getArtistFirstClusters()
				.groupBy { Triple(it.finalKey, it.role, it.nameKey) }
				.map { (key, rows) ->
					val canonical =
						rows.minWith(
							compareByDescending<ScanArtistFirstRow> { it.popularity }
								.thenBy { it.sampleScanId }
								.thenBy { it.displayName.orEmpty() }
						)
					ArtistFirstCluster(
						finalKey = key.first,
						role = key.second,
						nameKey = key.third,
						songCount = clusterCounts[key] ?: canonical.popularity,
						row = canonical
					)
				}
		val maps =
			firstClusters.groupBy { it.finalKey to it.role }.map { (key, clusters) ->
				val canonical =
					clusters.minWith(
						compareByDescending<ArtistFirstCluster> { it.songCount }
							.thenBy { it.nameKey }
					)
				LocalScanArtistRoleMapEntity(
					artistKey = key.first,
					role = key.second,
					displayName = canonical.row.displayName,
					rawName = canonical.row.rawName,
					sortName = canonical.row.sortName,
					mbid = canonical.row.mbid
				)
			}
		localScanDao.insertArtistRoleMaps(maps)
	}

	private suspend fun buildAlbumMaps() {
		val clusterCounts = localScanDao.getAlbumClusterCounts()
			.associate { (it.finalKey to it.firstClusterKey) to it.songCount }
		val firstClusters =
			localScanDao.getAlbumFirstClusters()
				.groupBy { it.finalKey to it.firstClusterKey }
				.map { (key, rows) ->
					val canonical =
						rows.minWith(
							compareByDescending<ScanAlbumFirstRow> { it.popularity }
								.thenBy { it.sampleScanId }
								.thenBy { it.displayName.orEmpty() }
						)
					AlbumFirstCluster(
						finalKey = key.first,
						firstClusterKey = key.second,
						songCount = clusterCounts[key] ?: canonical.popularity,
						row = canonical
					)
				}
		val maps =
			firstClusters.groupBy { it.finalKey }.map { (finalKey, clusters) ->
				val canonical =
					clusters.minWith(
						compareByDescending<AlbumFirstCluster> { it.songCount }
							.thenBy { it.firstClusterKey }
					)
				val row = canonical.row
				LocalScanAlbumMapEntity(
					albumKey = finalKey,
					albumId = if (finalKey.startsWith("m:")) row.albumId else row.albumIdNoMbid,
					displayName = row.displayName,
					rawName = row.rawName,
					songCount = canonical.songCount,
					sampleScanId = row.sampleScanId
				)
			}
		localScanDao.insertAlbumMaps(maps)
	}

	private data class ArtistFirstCluster(
		val finalKey: String,
		val role: Int,
		val nameKey: String,
		val songCount: Int,
		val row: ScanArtistFirstRow
	)

	private data class AlbumFirstCluster(
		val finalKey: String,
		val firstClusterKey: String,
		val songCount: Int,
		val row: ScanAlbumFirstRow
	)

	@OptIn(ExperimentalCoroutinesApi::class)
	private suspend fun materializeArtists(validArtistIds: MutableSet<String>) {
		val albumCounts =
			localScanDao.getArtistAlbumCounts().associate { it.artistKey to it.albumCount }
		val maps = localScanDao.getArtistRoleMaps()
		val existingArtists = maps.asSequence()
			.map { it.entityId() }
			.filter { it.isNotEmpty() }
			.chunked(LOOKUP_CHUNK_SIZE)
			.asFlow()
			.flatMapConcat {
				flow {
					emit(
						artistDao.getArtistsByIds(it)
					)
				}
			}
			.flowOn(Dispatchers.IO)
			.toList()
			.flatten()
			.associateBy { it.artistId }
		val entities =
			maps.map { artist ->
				artist.toEntity(
					albumCount = albumCounts[artist.artistKey] ?: 0,
					existing = existingArtists[artist.entityId()]
				)
			}.distinctBy { it.artistId }
		artistDao.insertArtists(entities)
		validArtistIds.addAll(entities.map { it.artistId })
	}

	private suspend fun materializeAlbums(validAlbumIds: MutableSet<String>) {
		val aggregates = localScanDao.getAlbumAggregates()
		val artists = localScanDao.getAllAlbumArtists().groupBy { it.albumKey }
		val genres = localScanDao.getAlbumGenres().groupBy { it.albumKey }
		val covers = localScanDao.getAlbumCovers().groupBy { it.albumKey }
		val existingAlbums = aggregates.map { it.albumId }
			.chunked(LOOKUP_CHUNK_SIZE)
			.flatMap { albumDao.getAlbumEntitiesByIds(it) }
			.associateBy { it.albumId }
		val entities =
			aggregates.map { album ->
				album.toEntity(
					artists = artists[album.albumKey].orEmpty(),
					genres = genres[album.albumKey].orEmpty()
						.mapNotNull { it.displayName }
						.distinct(),
					coverArtId = covers[album.albumKey]?.firstOrNull()?.coverId,
					existing = existingAlbums[album.albumId]
				)
			}
		albumDao.insertAlbums(entities)
		validAlbumIds.addAll(entities.map { it.albumId })
	}

	private suspend fun materializeGenres(validGenreNames: MutableSet<String>) {
		val songCounts = localScanDao.getGenreSongCounts().associate { it.genreKey to it.songCount }
		val artistAlbumCounts = localScanDao.getArtistAlbumCounts().associate {
			it.artistKey to it.albumCount
		}
		val genreArtists = localScanDao.getGenreArtistLinks().groupBy { it.genreKey }
		val entities =
			localScanDao.getGenreMaps().mapNotNull { genre ->
				genre.toEntity(
					songCount = songCounts[genre.genreKey] ?: 0,
					albumCount = genreArtists[genre.genreKey].orEmpty().sumOf {
						artistAlbumCounts[it.artistKey] ?: 0
					}
				)
			}
		genreDao.insertGenres(entities)
		validGenreNames.addAll(entities.map { it.genreName })
	}

	private suspend fun clearStaging() {
		localScanDao.clearSongs()
		localScanDao.clearArtists()
		localScanDao.clearGenres()
		localScanDao.clearArtistKeys()
		localScanDao.clearArtistMaps()
		localScanDao.clearArtistRoleMaps()
		localScanDao.clearAlbumKeys()
		localScanDao.clearAlbumMaps()
		localScanDao.clearGenreMaps()
	}

	private companion object {
		const val SONG_BATCH_SIZE = 500
		const val LOOKUP_CHUNK_SIZE = 900
	}
}
