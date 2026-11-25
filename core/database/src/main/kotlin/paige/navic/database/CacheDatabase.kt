/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.database

import androidx.room3.AutoMigration
import androidx.room3.ColumnTypeConverters
import androidx.room3.DaoReturnTypeConverters
import androidx.room3.Database
import androidx.room3.RewriteQueriesToDropUnusedColumns
import androidx.room3.RoomDatabase
import androidx.room3.paging.PagingSourceDaoReturnTypeConverter
import paige.navic.database.dao.AlbumDao
import paige.navic.database.dao.ArtistDao
import paige.navic.database.dao.DownloadDao
import paige.navic.database.dao.GenreDao
import paige.navic.database.dao.LocalScanDao
import paige.navic.database.dao.LyricDao
import paige.navic.database.dao.PlaylistDao
import paige.navic.database.dao.RadioDao
import paige.navic.database.dao.SongDao
import paige.navic.database.dao.SyncActionDao
import paige.navic.database.entity.AlbumEntity
import paige.navic.database.entity.ArtistEntity
import paige.navic.database.entity.DownloadEntity
import paige.navic.database.entity.GenreEntity
import paige.navic.database.entity.LocalScanAlbumKeyEntity
import paige.navic.database.entity.LocalScanAlbumMapEntity
import paige.navic.database.entity.LocalScanArtistEntity
import paige.navic.database.entity.LocalScanArtistKeyEntity
import paige.navic.database.entity.LocalScanArtistMapEntity
import paige.navic.database.entity.LocalScanArtistRoleMapEntity
import paige.navic.database.entity.LocalScanGenreEntity
import paige.navic.database.entity.LocalScanGenreMapEntity
import paige.navic.database.entity.LocalScanSongEntity
import paige.navic.database.entity.LyricEntity
import paige.navic.database.entity.PlaylistEntity
import paige.navic.database.entity.PlaylistSongCrossRef
import paige.navic.database.entity.PendingPlaylistEntryEntity
import paige.navic.database.entity.RadioEntity
import paige.navic.database.entity.SongEntity
import paige.navic.database.entity.SyncActionEntity

@Database(
	version = 35,
	entities = [
		AlbumEntity::class,
		ArtistEntity::class,
		DownloadEntity::class,
		GenreEntity::class,
		LocalScanAlbumKeyEntity::class,
		LocalScanAlbumMapEntity::class,
		LocalScanArtistEntity::class,
		LocalScanArtistKeyEntity::class,
		LocalScanArtistMapEntity::class,
		LocalScanArtistRoleMapEntity::class,
		LocalScanGenreEntity::class,
		LocalScanGenreMapEntity::class,
		LocalScanSongEntity::class,
		LyricEntity::class,
		PlaylistEntity::class,
		PlaylistSongCrossRef::class,
		PendingPlaylistEntryEntity::class,
		RadioEntity::class,
		SongEntity::class,
		SyncActionEntity::class
	],
	autoMigrations = [
		AutoMigration(from = 24, to = 25),
		AutoMigration(from = 25, to = 26),
		AutoMigration(from = 26, to = 27),
		AutoMigration(from = 27, to = 28),
		AutoMigration(from = 28, to = 29),
		AutoMigration(from = 29, to = 30),
		AutoMigration(from = 30, to = 31),
		AutoMigration(from = 31, to = 32),
		AutoMigration(from = 32, to = 33),
		AutoMigration(from = 33, to = 34),
		AutoMigration(from = 34, to = 35)
	]
)
@ColumnTypeConverters(Converters::class)
@DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)
@RewriteQueriesToDropUnusedColumns
abstract class CacheDatabase : RoomDatabase() {
	abstract fun albumDao(): AlbumDao
	abstract fun artistDao(): ArtistDao
	abstract fun downloadDao(): DownloadDao
	abstract fun genreDao(): GenreDao
	abstract fun localScanDao(): LocalScanDao
	abstract fun lyricDao(): LyricDao
	abstract fun playlistDao(): PlaylistDao
	abstract fun radioDao(): RadioDao
	abstract fun songDao(): SongDao
	abstract fun syncActionDao(): SyncActionDao
}
