/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.server.local

import android.content.Context
import paige.navic.domain.server.AuthorizationData
import paige.navic.domain.server.MusicProvider
import paige.navic.domain.server.MusicProviderFactory
import paige.navic.domain.server.ServerException
import paige.navic.database.CacheDatabase
import paige.navic.database.dao.AlbumDao
import paige.navic.database.dao.ArtistDao
import paige.navic.database.dao.GenreDao
import paige.navic.database.dao.LocalScanDao
import paige.navic.database.dao.PlaylistDao
import paige.navic.database.dao.SongDao
import paige.navic.domain.manager.runtime.StorageManager

class LocalMusicLibraryFactory(
	private val context: Context,
	private val database: CacheDatabase,
	private val localScanDao: LocalScanDao,
	private val songDao: SongDao,
	private val albumDao: AlbumDao,
	private val artistDao: ArtistDao,
	private val genreDao: GenreDao,
	private val playlistDao: PlaylistDao,
	private val m3uPlaylistPersistence: NavicM3uPlaylistPersistence,
	private val storageManager: StorageManager
) : MusicProviderFactory {
	override fun create(baseUrl: String, authorization: AuthorizationData): MusicProvider {
		val folders = authorization as? AuthorizationData.LocalFolders
			?: throw ServerException("Local server requires folder authorization")
		return LocalMusicLibrary(
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
				genreDao = genreDao,
				m3uPlaylistPersistence = m3uPlaylistPersistence,
				syncM3uPlaylists = folders.syncM3uPlaylists
			),
			songDao = songDao,
			coverDir = storageManager.musikrCoverDir()
		)
	}
}
