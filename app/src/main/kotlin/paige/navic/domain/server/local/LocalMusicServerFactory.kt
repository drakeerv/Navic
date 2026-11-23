/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.server.local

import android.content.Context
import paige.navic.domain.server.AuthorizationData
import paige.navic.domain.server.MusicServer
import paige.navic.domain.server.MusicServerFactory
import paige.navic.domain.server.UserCredentials
import paige.navic.domain.server.ServerException
import paige.navic.database.CacheDatabase
import paige.navic.database.dao.AlbumDao
import paige.navic.database.dao.ArtistDao
import paige.navic.database.dao.GenreDao
import paige.navic.database.dao.LocalScanDao
import paige.navic.database.dao.PlaylistDao
import paige.navic.database.dao.SongDao
import paige.navic.domain.manager.runtime.StorageManager

class LocalMusicServerFactory(
	private val context: Context,
	private val database: CacheDatabase,
	private val localScanDao: LocalScanDao,
	private val songDao: SongDao,
	private val albumDao: AlbumDao,
	private val artistDao: ArtistDao,
	private val genreDao: GenreDao,
	private val playlistDao: PlaylistDao,
	private val storageManager: StorageManager
) : MusicServerFactory {
	override fun create(baseUrl: String, authorization: AuthorizationData): MusicServer {
		val folders = authorization as? AuthorizationData.LocalFolders
			?: throw ServerException("Local server requires folder authorization")
		return LocalMusicServer(
			context = context,
			folderUris = folders.folderUris,
			mutableCache = NavicMutableCache(context, songDao),
			storedPlaylists = NavicStoredPlaylists(playlistDao),
			playHistoryRecorder = LocalPlayHistoryRecorder(songDao, albumDao),
			libraryWriter = NavicLibraryWriter(
				cacheDatabase = database,
				localScanDao = localScanDao,
				songDao = songDao,
				albumDao = albumDao,
				artistDao = artistDao,
				genreDao = genreDao
			),
			songDao = songDao,
			coverDir = storageManager.musikrCoverDir()
		)
	}

	override suspend fun authenticate(
		baseUrl: String,
		credentials: UserCredentials
	): AuthorizationData = throw ServerException("Local server does not use credentials")
}
