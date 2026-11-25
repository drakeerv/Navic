/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.server.local

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.oxycblt.musikr.Config
import org.oxycblt.musikr.IndexingProgress
import org.oxycblt.musikr.Interpretation
import org.oxycblt.musikr.LibraryWriter
import org.oxycblt.musikr.Musikr
import org.oxycblt.musikr.Storage
import org.oxycblt.musikr.cache.MutableCache
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
import paige.navic.database.dao.SongDao
import paige.navic.domain.model.DomainUser
import paige.navic.domain.server.LibraryIndexer
import paige.navic.domain.server.MusicProvider
import paige.navic.domain.server.PlayHistoryRecorder
import paige.navic.domain.server.ServerException
import paige.navic.domain.server.ServerInfo
import paige.navic.domain.server.ServerType
import paige.navic.domain.server.SyncProgressMessage
import java.io.File
import androidx.core.net.toUri

class LocalMusicLibrary(
	private val context: Context,
	folderUris: List<String>,
	private val mutableCache: MutableCache,
	private val storedPlaylists: StoredPlaylists,
	private val libraryWriter: LibraryWriter,
	private val playHistoryRecorder: PlayHistoryRecorder,
	private val songDao: SongDao,
	private val coverDir: File
) : MusicProvider, LibraryIndexer, PlayHistoryRecorder by playHistoryRecorder {
	private val folderUris = folderUris.distinct()

	override suspend fun index(onProgress: suspend (Float, SyncProgressMessage) -> Unit) {
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
			Location.Unopened.from(context, uri.toUri()).open(context)
		}
		check(locations.isNotEmpty()) { "No readable local music folders configured" }

		withContext(Dispatchers.IO) {
			coverDir.mkdirs()
		}
		val coverStorage = CoverStorage.at(coverDir)
		val embeddedCovers = EmbeddedCovers(CoverIdentifier.md5())
		val folderCovers = FolderCovers(context)
		val covers = MutableStoredCovers(
			src = MutableChainedCovers(embeddedCovers, folderCovers),
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

	override suspend fun serverInfo(): ServerInfo = ServerInfo(type = ServerType.LOCAL)

	override suspend fun currentUser(): DomainUser = DomainUser(username = "Local library")

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
		"content://${context.packageName}.cover-art/$id" + (size?.let { "?size=$it" }.orEmpty())

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

	private companion object {
		const val DEFAULT_COVER_SIZE = 1024
		const val MIN_COVER_SIZE = 128
		const val MAX_COVER_SIZE = 4096
	}
}
