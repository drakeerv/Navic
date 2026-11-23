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
import org.oxycblt.musikr.IndexingProgress
import org.oxycblt.musikr.Interpretation
import org.oxycblt.musikr.LibraryWriter
import org.oxycblt.musikr.Musikr
import org.oxycblt.musikr.Storage
import org.oxycblt.musikr.cache.MutableCache
import org.oxycblt.musikr.covers.MemoryCover
import org.oxycblt.musikr.covers.chained.MutableChainedCovers
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
import paige.navic.domain.model.DomainUser
import paige.navic.domain.model.ServerLyrics
import paige.navic.domain.server.MusicServer
import paige.navic.domain.server.LocalSyncServer
import paige.navic.domain.server.PlayHistoryRecorder
import paige.navic.domain.server.SyncProgressMessage
import paige.navic.database.dao.SongDao
import paige.navic.domain.server.ServerCapabilities
import paige.navic.domain.server.ServerException
import paige.navic.domain.server.LocalNoOpException
import paige.navic.domain.server.ServerInfo
import paige.navic.domain.server.ServerType
import java.io.File
import kotlin.time.Clock
import kotlin.time.Instant

class LocalMusicServer(
	private val context: Context,
	folderUris: List<String>,
	private val mutableCache: MutableCache,
	private val storedPlaylists: StoredPlaylists,
	private val libraryWriter: LibraryWriter,
	private val playHistoryRecorder: PlayHistoryRecorder,
	private val songDao: SongDao,
	private val coverDir: File
) : MusicServer, LocalSyncServer {
	private val folderUris = folderUris.distinct()

	override suspend fun sync(onProgress: suspend (Float, SyncProgressMessage) -> Unit) {
		onProgress(0.05f, SyncProgressMessage.Indexing)
		Musikr.new(context, buildConfig()).run { progress ->
			if (progress is IndexingProgress.Songs && progress.explored > 0) {
				val fraction = (progress.loaded.toFloat() / progress.explored).coerceIn(0f, 1f)
				onProgress(0.05f + fraction * 0.85f, SyncProgressMessage.Indexing)
			}
		}
		onProgress(0.95f, SyncProgressMessage.Saved)
		onProgress(1f, SyncProgressMessage.Finished)
	}

	private suspend fun buildConfig(): Config {
		val locations = folderUris.mapNotNull { uri ->
			Location.Unopened.from(context, Uri.parse(uri)).open(context)
		}
		check(locations.isNotEmpty()) { "No readable local music folders configured" }

		withContext(Dispatchers.IO) {
			coverDir.mkdirs()
		}
		val coverStorage = CoverStorage.at(coverDir)
		val embeddedCovers = EmbeddedCovers(CoverIdentifier.md5())
		val folderCovers = FolderCovers(context)
		val covers = MutableStoredCovers(
			src = MutableChainedCovers<MemoryCover, MemoryCover>(embeddedCovers, folderCovers),
			coverStorage = coverStorage,
			transcoding = NoTranscoding
		)
		return Config(
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
				storedPlaylists = storedPlaylists,
				libraryWriter = libraryWriter
			),
			interpretation = Interpretation(
				naming = Naming.simple(),
				separators = Separators.from(";/,&")
			)
		)
	}

	override suspend fun ping() = Unit

	override suspend fun serverInfo(): ServerInfo = ServerInfo(
		type = ServerType.LOCAL,
		capabilities = ServerCapabilities(
			isRemote = false,
			supportsDownloads = false,
			supportsTranscoding = false,
			supportsPlaySubmission = true,
			supportsRatings = false,
			supportsShares = false,
			supportsRadio = false,
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
	override suspend fun scrobble(id: String, submission: Boolean, time: Instant?) {
		if (!submission) return
		playHistoryRecorder.recordPlay(id, time ?: Clock.System.now())
	}
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
		return songDao.getSongById(id)?.filePath
			?: throw ServerException("Local song not found: $id")
	}

	override fun getCoverArtUrl(id: String, size: String?): String =
		"content://paige.navic.cover-art/$id" + (size?.let { "?size=$it" }.orEmpty())

	override suspend fun getCoverArt(id: String, size: String?): ByteArray =
		withContext(Dispatchers.IO) {
			val coverFile = if (id.startsWith("mcs:")) {
				coverDir.resolve(id.removePrefix("mcs:"))
			} else {
				coverDir.listFiles()?.firstOrNull { it.name.startsWith(id) }
			}
			val bytes = coverFile?.takeIf { it.isFile }?.readBytes() ?: return@withContext ByteArray(0)
			val maxSize = size?.toIntOrNull()?.coerceIn(MIN_COVER_SIZE, MAX_COVER_SIZE)
				?: DEFAULT_COVER_SIZE
			bytes.transcodeCover(maxSize)
		}

	override fun getAvatarUrl(username: String): String? = null
	override fun parseResponseError(contentType: String?, body: String): ServerException? = null

	private fun unsupported(feature: String = "This operation"): LocalNoOpException =
		LocalNoOpException("$feature is maintained by the local database")

	private companion object {
		const val DEFAULT_COVER_SIZE = 1024
		const val MIN_COVER_SIZE = 128
		const val MAX_COVER_SIZE = 4096
	}
}
