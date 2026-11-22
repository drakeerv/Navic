/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.server.local

import android.content.Context
import android.net.Uri
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.oxycblt.musikr.Config
import org.oxycblt.musikr.Interpretation
import org.oxycblt.musikr.Library
import org.oxycblt.musikr.LibraryResult
import org.oxycblt.musikr.Musikr
import org.oxycblt.musikr.Storage
import org.oxycblt.musikr.cache.MutableCache
import org.oxycblt.musikr.covers.embedded.CoverIdentifier
import org.oxycblt.musikr.covers.embedded.EmbeddedCovers
import org.oxycblt.musikr.covers.stored.CoverStorage
import org.oxycblt.musikr.covers.stored.MutableStoredCovers
import org.oxycblt.musikr.covers.stored.NoTranscoding
import org.oxycblt.musikr.fs.Location
import org.oxycblt.musikr.fs.saf.SAF
import org.oxycblt.musikr.playlist.db.StoredPlaylists
import org.oxycblt.musikr.tag.interpret.Naming
import org.oxycblt.musikr.tag.interpret.Separators
import paige.navic.domain.model.DomainAlbum
import paige.navic.domain.model.DomainAlbumInfo
import paige.navic.domain.model.DomainAlbumListType
import paige.navic.domain.model.DomainArtist
import paige.navic.domain.model.DomainArtistInfo
import paige.navic.domain.model.DomainGenre
import paige.navic.domain.model.DomainPlaybackState
import paige.navic.domain.model.DomainPlaylist
import paige.navic.domain.model.DomainSearchResult
import paige.navic.domain.model.DomainSong
import paige.navic.domain.model.DomainSongArtist
import paige.navic.domain.model.DomainUser
import paige.navic.domain.model.ServerLyrics
import paige.navic.domain.server.MusicServer
import paige.navic.domain.server.LocalSyncServer
import paige.navic.domain.server.SyncProgressMessage
import paige.navic.database.dao.AlbumDao
import paige.navic.database.dao.ArtistDao
import paige.navic.database.dao.GenreDao
import paige.navic.database.dao.SongDao
import paige.navic.domain.server.ServerCapabilities
import paige.navic.domain.server.ServerException
import paige.navic.domain.server.LocalNoOpException
import paige.navic.domain.server.ServerInfo
import paige.navic.domain.server.ServerStatus
import kotlin.time.Instant
import kotlin.time.Duration.Companion.milliseconds
import paige.navic.database.entity.AlbumEntity
import paige.navic.database.entity.ArtistEntity
import paige.navic.database.entity.GenreEntity
import paige.navic.database.entity.SongEntity
import paige.navic.domain.model.DomainExplicitStatus
import paige.navic.domain.model.DomainReplayGain
import paige.navic.util.xxHash32

class LocalMusicServer(
	private val context: Context,
	folderUris: List<String>,
	private val mutableCache: MutableCache,
	private val storedPlaylists: StoredPlaylists,
	private val songDao: SongDao,
	private val albumDao: AlbumDao,
	private val artistDao: ArtistDao,
	private val genreDao: GenreDao
) : MusicServer, LocalSyncServer {
	private val folderUris = folderUris.distinct()
	private var libraryResult: LibraryResult? = null
	private val library: Library?
		get() = libraryResult?.library

	override suspend fun sync(onProgress: suspend (Float, SyncProgressMessage) -> Unit) {
		libraryResult = null
		onProgress(0.05f, SyncProgressMessage.Indexing)
		ensureLibrary()

		val library = requireNotNull(libraryResult?.library)
		val artists = library.artists.mapNotNull { artist ->
			val name = (artist.name as? org.oxycblt.musikr.tag.Name.Known)?.raw
				?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
			ArtistEntity(
				artistId = "local-artist-${name.xxHash32()}",
				name = name,
				albumCount = artist.explicitAlbums.size,
				coverArtId = artist.covers.covers.firstOrNull()?.id
			)
		}
		val albums = library.albums.mapNotNull { album ->
			val name = (album.name as? org.oxycblt.musikr.tag.Name.Known)?.raw
				?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
			val artist = album.artists.firstOrNull()
			val artistName = (artist?.name as? org.oxycblt.musikr.tag.Name.Known)?.raw
			val artistId = artistName?.takeIf { it.isNotBlank() }
				?.let { "local-artist-${it.xxHash32()}" }
				?: "local-artist-unknown"
			AlbumEntity(
				albumId = album.uid.toString(),
				name = name,
				artistName = artistName,
				artistId = artistId,
				displayArtist = album.artists.joinToString { artistItem ->
					(artistItem.name as? org.oxycblt.musikr.tag.Name.Known)?.raw.orEmpty()
				},
				year = album.dates?.min?.year,
				coverArtId = album.covers.covers.firstOrNull()?.id,
				genre = album.songs.flatMap { it.genres }.firstOrNull()
					?.let { (it.name as? org.oxycblt.musikr.tag.Name.Known)?.raw },
				genres = album.songs.flatMap { it.genres }.mapNotNull {
					(it.name as? org.oxycblt.musikr.tag.Name.Known)?.raw
				}.distinct(),
				songCount = album.songs.size,
				duration = album.durationMs.milliseconds,
				createdAt = Instant.fromEpochMilliseconds(album.addedMs),
				starredAt = null,
				lastPlayedAt = null,
				userRating = null,
				version = null,
				musicBrainzId = null,
				isExternal = false
			)
		}
		val genres = library.genres.mapNotNull { genre ->
			val name = (genre.name as? org.oxycblt.musikr.tag.Name.Known)?.raw
				?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
			GenreEntity(
				genreName = name,
				albumCount = genre.artists.sumOf { it.explicitAlbums.size },
				songCount = genre.songs.size
			)
		}
		val songs = library.songs.map { song ->
			val artists = song.artists.map { artist ->
				val name = (artist.name as? org.oxycblt.musikr.tag.Name.Known)?.raw.orEmpty()
				DomainSongArtist(
					id = "local-artist-${name.xxHash32()}",
					name = name
				)
			}.filter { it.name.isNotBlank() }
			val albumArtists = song.album.artists.map { artist ->
				val name = (artist.name as? org.oxycblt.musikr.tag.Name.Known)?.raw.orEmpty()
				DomainSongArtist(
					id = "local-artist-${name.xxHash32()}",
					name = name
				)
			}.filter { it.name.isNotBlank() }
			SongEntity(
				songId = song.uid.toString(),
				title = song.name.raw,
				artistName = artists.firstOrNull()?.name,
				artistId = artists.firstOrNull()?.id ?: "local-artist-unknown",
				albumTitle = (song.album.name as? org.oxycblt.musikr.tag.Name.Known)?.raw,
				belongsToAlbumId = song.album.uid.toString(),
				parentId = song.album.uid.toString(),
				comment = null,
				trackNumber = song.track,
				discNumber = song.disc?.number,
				isrc = emptyList(),
				year = song.date?.year,
				genre = song.genres.firstOrNull()
					?.let { (it.name as? org.oxycblt.musikr.tag.Name.Known)?.raw },
				genres = song.genres.mapNotNull { (it.name as? org.oxycblt.musikr.tag.Name.Known)?.raw },
				moods = emptyList(),
				duration = song.durationMs.milliseconds,
				bpm = null,
				contributors = emptyList(),
				playCount = 0,
				userRating = null,
				averageRating = null,
				bitRate = song.bitrateKbps,
				bitDepth = null,
				sampleRate = song.sampleRateHz,
				audioChannelCount = null,
				replayGain = DomainReplayGain(
					albumGain = song.replayGainAdjustment.album,
					trackGain = song.replayGainAdjustment.track
				),
				fileSize = song.size,
				fileExtension = android.webkit.MimeTypeMap.getSingleton()
					.getExtensionFromMimeType(song.format.mimeType),
				mimeType = song.format.mimeType,
				filePath = song.uri.toString(),
				starredAt = null,
				coverArtId = song.cover?.id,
				musicBrainzId = null,
				explicitStatus = DomainExplicitStatus.Unknown,
				artists = artists,
				albumArtists = albumArtists,
				isExternal = false
			)
		}

		onProgress(0.75f, SyncProgressMessage.Saved)
		artistDao.insertArtists(artists)
		albumDao.insertAlbums(albums)
		genreDao.insertGenres(genres)
		songDao.insertSongs(songs)

		onProgress(0.95f, SyncProgressMessage.Saved)
		artistDao.deleteObsoleteArtists(artists.map { it.artistId }.toSet())
		albumDao.deleteObsoleteAlbums(albums.map { it.albumId }.toSet())
		genreDao.deleteObsoleteGenres(genres.map { it.genreName }.toSet())
		songDao.deleteObsoleteSongs(songs.map { it.songId }.toSet())
		onProgress(1f, SyncProgressMessage.Finished)
	}

	private suspend fun ensureLibrary() {
		if (libraryResult != null) return

		val locations = folderUris.mapNotNull { uri ->
			Location.Unopened.from(context, Uri.parse(uri)).open(context)
		}
		check(locations.isNotEmpty()) { "No readable local music folders configured" }

		val coverDir = withContext(Dispatchers.IO) {
			context.filesDir.resolve("musikr-covers").apply { mkdirs() }
		}
		val coverStorage = CoverStorage.at(coverDir)
		val embeddedCovers = EmbeddedCovers(CoverIdentifier.md5())
		val covers = MutableStoredCovers(
			src = embeddedCovers,
			coverStorage = coverStorage,
			transcoding = NoTranscoding
		)
		val config = Config(
			fs = SAF.from(
				context,
				SAF.Query(
					source = locations,
					exclude = emptyList(),
					withHidden = false,
					multithread = true
				)
			),
			storage = Storage(
				cache = mutableCache,
				covers = covers,
				storedPlaylists = storedPlaylists
			),
			interpretation = Interpretation(
				naming = Naming.simple(),
				separators = Separators.from(";/,&")
			)
		)

		libraryResult = Musikr.new(context, config).run()
	}

	override suspend fun ping(): ServerStatus = ServerStatus(
		serverVersion = null,
		version = null,
		type = "local",
		isOpenSubsonic = false
	)

	override suspend fun serverInfo(): ServerInfo = ServerInfo(
		serverVersion = null,
		version = null,
		type = "local",
		isOpenSubsonic = false,
		capabilities = ServerCapabilities(
			isRemote = false,
			supportsDownloads = false,
			supportsScrobbling = false,
			supportsRatings = false,
			supportsShares = false,
			supportsRadio = false,
			supportsChat = false,
			supportsPlaylistMutation = true
		)
	)

	override suspend fun currentUser(): DomainUser = DomainUser(username = "Local library")

	override suspend fun getAlbums(
		type: DomainAlbumListType,
		size: Int,
		offset: Int
	): List<DomainAlbum> {
		throw unsupported("Album listing")
	}

	override suspend fun getAlbum(id: String): DomainAlbum {
		throw unsupported("Album lookup")
	}

	override suspend fun getSong(id: String): DomainSong {
		throw unsupported("Song lookup")
	}

	override suspend fun getArtists(): List<DomainArtist> {
		throw unsupported("Artist listing")
	}

	override suspend fun getArtistInfo(id: String): DomainArtistInfo {
		throw unsupported()
	}

	override suspend fun getAlbumInfo(id: String): DomainAlbumInfo {
		throw unsupported()
	}

	override suspend fun getGenres(): List<DomainGenre> {
		throw unsupported("Genre listing")
	}

	override suspend fun getPlaylists(): List<DomainPlaylist> {
		throw unsupported("Playlist listing")
	}

	override suspend fun getPlaylist(id: String): DomainPlaylist {
		throw unsupported("Playlist lookup")
	}

	override suspend fun getSimilarSongs(id: String, count: Int): List<DomainSong> = emptyList()

	override suspend fun search(query: String): DomainSearchResult {
		throw unsupported("Search")
	}

	override suspend fun getLyrics(songId: String): ServerLyrics? = null

	override suspend fun star(id: String) = Unit
	override suspend fun unstar(id: String) = Unit
	override suspend fun setRating(id: String, rating: Int) = Unit
	override suspend fun scrobble(id: String, submission: Boolean, time: Instant?) = Unit
	override suspend fun reportPlayback(
		id: String,
		positionMs: Long,
		state: DomainPlaybackState,
		isDeviceMuted: Boolean,
		deviceVolume: Int,
		@Player.RepeatMode repeatMode: Int,
		shuffleModeEnabled: Boolean,
		playbackRate: Float
	) = Unit

	override suspend fun createPlaylist(name: String, songIds: List<String>): DomainPlaylist {
		throw unsupported("Local playlist creation")
	}

	override suspend fun updatePlaylist(
		id: String,
		name: String?,
		comment: String?,
		public: Boolean?,
		songIdsToAdd: List<String>,
		songIndicesToRemove: List<Int>
	) {
		throw unsupported("Local playlist mutation")
	}

	override suspend fun deletePlaylist(id: String) {
		throw unsupported("Local playlist deletion")
	}

	override suspend fun getPlaybackStreamUrl(
		id: String,
		bitrateKbps: Int,
		format: String?,
		skipTranscodeDecisions: Boolean
	): String {
		ensureLibrary()
		val song = library!!.findSong(org.oxycblt.musikr.Music.UID.fromString(id)!!)
			?: throw ServerException("Local song not found: $id")
		return song.uri.toString()
	}

	override fun getCoverArtUrl(id: String, size: String?): String =
		"content://paige.navic.cover-art/$id" + (size?.let { "?size=$it" }.orEmpty())

	override suspend fun getCoverArt(id: String, size: String?): ByteArray {
		val coverDir = context.filesDir.resolve("musikr-covers")
		val coverFile = if (id.startsWith("mcs:")) {
			coverDir.resolve(id.removePrefix("mcs:"))
		} else {
			coverDir.listFiles()?.firstOrNull { it.name.startsWith(id) }
		}
		return coverFile?.takeIf { it.isFile }?.readBytes() ?: ByteArray(0)
	}

	override fun getAvatarUrl(username: String): String? = null
	override fun parseResponseError(contentType: String?, body: String): ServerException? = null

	private fun unsupported(feature: String = "This operation"): LocalNoOpException =
		LocalNoOpException("$feature is maintained by the local database")
}
