/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.repository

import androidx.room3.concurrent.AtomicInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import paige.navic.R
import paige.navic.database.dao.AlbumDao
import paige.navic.database.dao.ArtistDao
import paige.navic.database.dao.GenreDao
import paige.navic.database.dao.LyricDao
import paige.navic.database.dao.PlaylistDao
import paige.navic.database.dao.RadioDao
import paige.navic.database.dao.SongDao
import paige.navic.database.dao.SyncActionDao
import paige.navic.database.entity.AlbumEntity
import paige.navic.database.entity.ArtistEntity
import paige.navic.database.entity.PlaylistEntity
import paige.navic.database.entity.PlaylistSongCrossRef
import paige.navic.database.entity.SongEntity
import paige.navic.database.mapper.toDomainModel
import paige.navic.database.mapper.toEntity
import paige.navic.domain.manager.session.SessionManager
import paige.navic.domain.model.DomainAlbum
import paige.navic.domain.model.DomainAlbumListType
import paige.navic.domain.model.DomainArtist
import paige.navic.domain.server.MusicServer
import paige.navic.domain.server.ServerException
import paige.navic.util.Logger
import kotlin.coroutines.cancellation.CancellationException

class DbRepository(
	private val albumDao: AlbumDao,
	private val playlistDao: PlaylistDao,
	private val songDao: SongDao,
	private val genreDao: GenreDao,
	private val artistDao: ArtistDao,
	private val radioDao: RadioDao,
	private val lyricDao: LyricDao,
	private val syncDao: SyncActionDao,
	private val sessionManager: SessionManager
) {
	private val syncWorkerCount = 8
	private val dbChunkSize = 500
	private val songChunkSize = 1500

	private data class LibraryAlbumData(
		val album: AlbumEntity,
		val songs: List<SongEntity>
	)

	private data class PlaylistSyncData(
		val playlistId: String,
		val songs: List<SongEntity>
	)

	private val server: MusicServer
		get() = sessionManager.server
			?: throw ServerException("This operation requires a remote music server")

	private suspend fun <T> runDbOp(block: suspend () -> T): Result<T> =
		withContext(Dispatchers.IO) {
			try {
				Result.success(block())
			} catch (e: Exception) {
				if (e is CancellationException) throw e
				Result.failure(e)
			}
		}

	private fun mapLibraryAlbum(
		summary: DomainAlbum,
		album: DomainAlbum
	): LibraryAlbumData {
		val albumEntity = album.toEntity(
			artistIdOverride = summary.artistId,
			artistNameOverride = summary.artistName
		)

		val songEntities = album.songs.map { song ->
			song.toEntity(
				artistIdOverride = albumEntity.artistId.takeIf { song.artistId.isBlank() },
				artistNameOverride = albumEntity.artistName.takeIf {
					song.artistName.isNullOrBlank()
				}
			)
		}

		return LibraryAlbumData(
			album = albumEntity,
			songs = songEntities
		)
	}

	private suspend fun fetchLibraryAlbum(summary: DomainAlbum): LibraryAlbumData? {
		val album = try {
			server.getAlbum(summary.id)
		} catch (e: CancellationException) {
			throw e
		} catch (e: SerializationException) {
			Logger.e(
				"DbRepository",
				"Could not deserialize album ${summary.id} (${summary.name}); skipping it",
				e
			)
			return null
		} catch (e: Exception) {
			if (e.message?.contains("DATA_NOT_FOUND") == true) {
				Logger.e(
					"DbRepository",
					"Album ${summary.id} (${summary.name}) not found; skipping it",
					e
				)
				return null
			}

			throw e
		}

		return mapLibraryAlbum(summary, album)
	}

	private suspend fun syncLibraryStream(
		onProgress: suspend (Float) -> Unit
	): Pair<Set<String>, Set<String>> = coroutineScope {
		val input = Channel<DomainAlbum>(capacity = syncWorkerCount)
		val output = Channel<LibraryAlbumData>(capacity = 1)
		val completedFetches = AtomicInt(0)
		val progressMutex = Mutex()
		var lastReportedProgress = 0f

		suspend fun reportProgress(progress: Float) {
			progressMutex.withLock {
				lastReportedProgress = maxOf(
					lastReportedProgress,
					progress.coerceIn(0f, 1f)
				)
				onProgress(lastReportedProgress)
			}
		}

		val producer = launch {
			try {
				val pageSize = 500
				var offset = 0

				while (true) {
					val page = server.getAlbums(
						DomainAlbumListType.AlphabeticalByName,
						pageSize,
						offset
					)

					if (page.isEmpty()) break

					for (summary in page) {
						input.send(summary)
					}

					if (page.size < pageSize) break
					offset += pageSize
				}
			} finally {
				input.close()
			}
		}

		val workers = List(syncWorkerCount) {
			launch {
				for (summary in input) {
					val albumData = fetchLibraryAlbum(summary)
					if (albumData != null) {
						output.send(albumData)
					}

					val done = completedFetches.incrementAndGet()
					if (done == 1 || done % 10 == 0) {
						val estimate = 0.1f + 0.8f * (done.toFloat() / (done.toFloat() + 500f))
						reportProgress(estimate)
					}
				}
			}
		}

		val closer = launch {
			try {
				producer.join()
				workers.joinAll()
			} finally {
				output.close()
			}
		}

		val validAlbumIds = mutableSetOf<String>()
		val validSongIds = mutableSetOf<String>()
		val albumBatch = ArrayList<AlbumEntity>(dbChunkSize)
		val songBatch = ArrayList<SongEntity>(songChunkSize)
		val artistBatch = LinkedHashMap<String, ArtistEntity>()

		var persistedAlbumCount = 0
		var persistedSongCount = 0L

		fun addArtist(artist: ArtistEntity) {
			if (artist.artistId.isNotBlank()) {
				artistBatch[artist.artistId] = artist
			}
		}

		suspend fun flushBatches() {
			if (albumBatch.isEmpty() && songBatch.isEmpty() && artistBatch.isEmpty()) {
				return
			}

			if (albumBatch.isNotEmpty()) {
				albumDao.insertAlbums(albumBatch)
			}

			if (songBatch.isNotEmpty()) {
				songDao.insertSongs(songBatch)
			}

			if (artistBatch.isNotEmpty()) {
				artistDao.insertArtistsIgnoringConflicts(artistBatch.values.toList())
			}

			persistedSongCount += songBatch.size
			albumBatch.clear()
			songBatch.clear()
			artistBatch.clear()
		}

		for ((album, songs) in output) {
			albumBatch.add(album)
			validAlbumIds.add(album.albumId)
			persistedAlbumCount++

			if (album.artistId.isNotBlank()) {
				addArtist(
					ArtistEntity(
						artistId = album.artistId,
						name = album.artistName ?: album.artistId,
						coverArtId = album.coverArtId
					)
				)
			}

			for (song in songs) {
				songBatch.add(song)
				validSongIds.add(song.songId)

				if (song.artistId.isNotBlank()) {
					addArtist(
						ArtistEntity(
							artistId = song.artistId,
							name = song.artistName ?: song.artistId,
							albumCount = 0,
							coverArtId = song.coverArtId
						)
					)
				}

				for ((id, name) in song.artists) {
					if (id.isNotBlank()) {
						addArtist(
							ArtistEntity(
								artistId = id,
								name = name,
								albumCount = 0,
								coverArtId = song.coverArtId
							)
						)
					}
				}

				for ((id, name) in song.albumArtists) {
					if (id.isNotBlank()) {
						addArtist(
							ArtistEntity(
								artistId = id,
								name = name,
								albumCount = 0,
								coverArtId = song.coverArtId
							)
						)
					}
				}

				if (songBatch.size >= songChunkSize) {
					flushBatches()
				}
			}

			if (albumBatch.size >= dbChunkSize) {
				flushBatches()
			}

			if (persistedAlbumCount == 1 || persistedAlbumCount % 10 == 0) {
				val estimate = 0.1f + 0.8f * (
					persistedAlbumCount.toFloat() /
						(persistedAlbumCount.toFloat() + 500f)
					)
				reportProgress(estimate)
			}
		}

		closer.join()
		flushBatches()

		Logger.i(
			"DbRepository",
			"- Sync result: $persistedAlbumCount albums, $persistedSongCount songs"
		)

		reportProgress(1f)
		validAlbumIds to validSongIds
	}

	private suspend fun fetchPlaylistData(playlistId: String): PlaylistSyncData? {
		val playlist = try {
			server.getPlaylist(playlistId)
		} catch (e: CancellationException) {
			throw e
		} catch (e: SerializationException) {
			Logger.e(
				"DbRepository",
				"Could not deserialize playlist $playlistId; skipping it",
				e
			)
			return null
		}

		return PlaylistSyncData(
			playlistId = playlistId,
			songs = playlist.songs.map { it.toEntity() }
		)
	}

	private suspend fun persistPlaylist(data: PlaylistSyncData) {
		data.songs.chunked(dbChunkSize).forEach { chunk ->
			songDao.insertSongs(chunk)
		}

		val crossRefs = data.songs.mapIndexed { index, song ->
			PlaylistSongCrossRef(
				playlistId = data.playlistId,
				songId = song.songId,
				position = index
			)
		}

		if (crossRefs.isEmpty()) {
			playlistDao.deletePlaylistSongCrossRefs(data.playlistId)
		} else {
			playlistDao.replacePlaylistSongs(data.playlistId, crossRefs)
		}
	}

	private suspend fun syncAllPlaylistSongs(
		playlists: List<PlaylistEntity>,
		onProgress: suspend (Float) -> Unit
	): Set<String> = coroutineScope {
		if (playlists.isEmpty()) {
			onProgress(1f)
			return@coroutineScope emptySet()
		}

		val input = Channel<PlaylistEntity>(capacity = syncWorkerCount)
		val output = Channel<PlaylistSyncData>(capacity = 1)
		val fetchedCount = AtomicInt(0)
		val progressMutex = Mutex()
		var reportedProgress = 0f

		suspend fun reportProgress(progress: Float) {
			progressMutex.withLock {
				reportedProgress = maxOf(reportedProgress, progress.coerceIn(0f, 1f))
				onProgress(reportedProgress)
			}
		}

		val producer = launch {
			try {
				playlists.forEach { input.send(it) }
			} finally {
				input.close()
			}
		}

		val workers = List(minOf(syncWorkerCount, playlists.size)) {
			launch {
				for ((playlistId) in input) {
					val data = fetchPlaylistData(playlistId)
					val done = fetchedCount.incrementAndGet()
					reportProgress(0.5f * done.toFloat() / playlists.size)

					if (data != null) {
						output.send(data)
					}
				}
			}
		}

		val closer = launch {
			try {
				producer.join()
				workers.joinAll()
			} finally {
				output.close()
			}
		}

		val validSongIds = mutableSetOf<String>()
		var persistedCount = 0

		for (data in output) {
			persistPlaylist(data)
			data.songs.forEach { validSongIds.add(it.songId) }
			persistedCount++
			reportProgress(
				0.5f + 0.5f * persistedCount.toFloat() / playlists.size
			)
		}

		closer.join()
		reportProgress(1f)
		validSongIds
	}

	suspend fun removeEverything(): Result<Unit> = runDbOp {
		albumDao.clearAllAlbums()
		playlistDao.clearAllPlaylists()
		songDao.clearAllSongs()
		genreDao.clearAllGenres()
		artistDao.clearAllArtists()
		radioDao.clearAllRadios()
		lyricDao.clearAllLyrics()
		syncDao.clearAllActions()
		Logger.i("DbRepository", "Database wiped completely.")
	}

	suspend fun syncEverything(
		onProgress: suspend (Float, Int) -> Unit = { _, _ -> }
	): Result<Unit> = runDbOp {
		coroutineScope {
			val callbackMutex = Mutex()
			var lastReportedProgress = 0f

			val progressCallback: suspend (Float, Int) -> Unit = { progress, message ->
				callbackMutex.withLock {
					lastReportedProgress = maxOf(
						lastReportedProgress,
						progress.coerceIn(0f, 1f)
					)
					Logger.i(
						"DbRepository",
						"$lastReportedProgress message=$message"
					)
					onProgress(lastReportedProgress, message)
				}
			}

			progressCallback(0f, R.string.info_syncing)

			progressCallback(0.01f, R.string.info_syncing_genres)
			progressCallback(0.02f, R.string.info_syncing_radios)
			progressCallback(0.04f, R.string.info_syncing_artists)
			progressCallback(0.07f, R.string.info_syncing_playlists)

			val playlists = coroutineScope {
				val genresDeferred = async { syncGenres().getOrThrow() }

				val radiosDeferred = async {
					try {
						syncRadios().getOrThrow()
					} catch (e: ServerException) {
						Logger.e(
							tag = "DbRepository",
							msg = "Could not sync radio stations; this server may not support radios",
							tr = e
						)
					}
				}

				val artistsDeferred = async { syncArtists().getOrThrow() }
				val playlistsDeferred = async { syncPlaylists().getOrThrow() }

				genresDeferred.await()
				radiosDeferred.await()
				artistsDeferred.await()
				playlistsDeferred.await()
			}

			progressCallback(0.10f, R.string.info_syncing_albums)

			val contentProgressMutex = Mutex()
			var libraryProgress = 0f
			var playlistProgress = 0f

			suspend fun reportContentProgress(
				isLibrary: Boolean,
				progress: Float,
				message: Int
			) {
				contentProgressMutex.withLock {
					if (isLibrary) {
						libraryProgress = maxOf(libraryProgress, progress)
					} else {
						playlistProgress = maxOf(playlistProgress, progress)
					}

					val combinedProgress =
						(libraryProgress * 0.65f) + (playlistProgress * 0.35f)

					progressCallback(
						0.10f + combinedProgress * 0.90f,
						message
					)
				}
			}

			val (libraryResult, playlistSongIds) = coroutineScope {
				val libraryDeferred = async {
					syncLibrarySongs { progress, message ->
						reportContentProgress(
							isLibrary = true,
							progress = progress,
							message = message
						)
					}.getOrThrow()
				}

				val playlistSongsDeferred = async {
					syncAllPlaylistSongs(playlists) { progress ->
						reportContentProgress(
							isLibrary = false,
							progress = progress,
							message = R.string.info_syncing_playlists
						)
					}
				}

				libraryDeferred.await() to playlistSongsDeferred.await()
			}

			val validAlbumIds = libraryResult.first
			val validSongIds = libraryResult.second + playlistSongIds

			albumDao.deleteObsoleteAlbums(validAlbumIds)
			songDao.deleteObsoleteSongs(validSongIds)

			progressCallback(1f, R.string.info_syncing_finished)
		}
	}

	suspend fun syncLibrarySongs(
		onProgress: suspend (Float, Int) -> Unit = { _, _ -> }
	): Result<Pair<Set<String>, Set<String>>> = runDbOp {
		onProgress(0f, R.string.info_syncing_albums)

		val (validAlbumIds, validSongIds) = syncLibraryStream { progress ->
			onProgress(progress, R.string.info_syncing_albums)
		}

		onProgress(1f, R.string.info_syncing_saved)
		validAlbumIds to validSongIds
	}

	suspend fun syncPlaylists(): Result<List<PlaylistEntity>> = runDbOp {
		val remotePlaylists = server.getPlaylists()
		val playlistEntities = remotePlaylists.map { it.toEntity() }
		val validPlaylistIds = playlistEntities.map { it.playlistId }.toSet()

		playlistEntities.chunked(dbChunkSize).forEach { chunk ->
			playlistDao.insertPlaylists(chunk)
		}
		playlistDao.deleteObsoletePlaylists(validPlaylistIds)

		Logger.i(
			"DbRepository",
			"- Playlist sync result: ${playlistEntities.size} playlists found"
		)

		playlistEntities
	}

	suspend fun syncPlaylistSongs(playlistId: String): Result<Set<String>> = runDbOp {
		val data = fetchPlaylistData(playlistId)
			?: return@runDbOp emptySet()
		persistPlaylist(data)

		Logger.i(
			"DbRepository",
			"- Playlist [${data.playlistId}] synced: ${data.songs.size} songs"
		)

		data.songs.mapTo(mutableSetOf()) { it.songId }
	}

	suspend fun syncGenres(): Result<Unit> = runDbOp {
		val remoteGenres = server.getGenres()
		val entities = remoteGenres.map { it.toEntity() }

		entities.chunked(dbChunkSize).forEach { chunk ->
			genreDao.insertGenres(chunk)
		}
		genreDao.deleteObsoleteGenres(entities.map { it.genreName }.toSet())
		Logger.i("DbRepository", "- Genres Synced: ${entities.size} genres found")
	}

	suspend fun syncArtists(): Result<Unit> = runDbOp {
		val artists = server.getArtists().map { it.toEntity() }

		artists.chunked(dbChunkSize).forEach { chunk ->
			artistDao.insertArtists(chunk)
		}
		artistDao.deleteObsoleteArtists(artists.map { it.artistId }.toSet())
		Logger.i("DbRepository", "- Artists Synced: ${artists.size} artists found")
	}

	suspend fun syncRadios(): Result<Unit> = runDbOp {
		val radioServer = sessionManager.radio ?: return@runDbOp
		val remoteRadios = radioServer.getRadioStations()
		val entities = remoteRadios.map { it.toEntity() }

		entities.chunked(dbChunkSize).forEach { chunk ->
			radioDao.insertRadios(chunk)
		}
		radioDao.deleteObsoleteRadios(entities.map { it.radioId }.toSet())
		Logger.i("DbRepository", "- Radios Synced: ${entities.size} stations found")
	}

	suspend fun fetchArtistMetadata(artistId: String): Result<DomainArtist> = runDbOp {
		val currentEntity = artistDao.getArtistById(artistId)
			?: throw Exception("Artist not found in local DB")
		if (sessionManager.server == null) return@runDbOp currentEntity.toDomainModel()

		val artistInfo = server.getArtistInfo(artistId)
		val simIds = artistInfo.similarArtists.map { it.id }

		val updatedEntity = currentEntity.copy(
			biography = artistInfo.biography,
			similarArtistIds = simIds,
			lastFmUrl = artistInfo.lastFmUrl
		)

		artistDao.insertArtist(updatedEntity)

		updatedEntity.toDomainModel()
	}
}
