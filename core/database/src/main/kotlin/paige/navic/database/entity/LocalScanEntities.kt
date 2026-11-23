/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlin.time.Duration

@Entity(
	tableName = "LocalScanSong",
	indices = [
		Index(value = ["songId"]),
		Index(value = ["albumOccurrenceKey"]),
		Index(value = ["albumNameKey"]),
		Index(value = ["filePath"], unique = true)
	]
)
data class LocalScanSongEntity(
	@PrimaryKey(autoGenerate = true) val id: Long = 0,
	val songId: String,
	val v400Id: String,
	val v401Id: String,
	val title: String,
	val track: Int?,
	val discNumber: Int?,
	val discSubtitle: String?,
	val year: Int?,
	val duration: Duration,
	val bitrateKbps: Int?,
	val sampleRateHz: Int?,
	val bitDepth: Int?,
	val audioChannelCount: Int?,
	val replayGainAlbum: Float?,
	val replayGainTrack: Float?,
	val replayGainAlbumPeak: Float?,
	val replayGainTrackPeak: Float?,
	val fileSize: Long,
	val mimeType: String?,
	val fileExtension: String?,
	val filePath: String,
	val addedMs: Long,
	val coverId: String?,
	val albumOccurrenceKey: String,
	@ColumnInfo(defaultValue = "") val albumOccurrenceKeyNoMbid: String,
	val albumNameKey: String,
	val albumMbid: String?,
	val albumArtistSource: Int,
	val albumArtistRawNamesKey: String,
	@ColumnInfo(defaultValue = "") val albumArtistIdentityKey: String,
	val albumId: String,
	val albumIdNoMbid: String,
	val albumDisplayName: String?,
	val albumRawName: String?
)

@Entity(
	tableName = "LocalScanArtist",
	primaryKeys = ["scanSongId", "role", "position"],
	indices = [
		Index(value = ["occurrenceKey"]),
		Index(value = ["nameKey"])
	]
)
data class LocalScanArtistEntity(
	val scanSongId: Long,
	val role: Int,
	val position: Int,
	val occurrenceKey: String,
	val nameKey: String,
	val mbid: String?,
	val rawName: String?,
	val displayName: String?,
	val sortName: String?,
	val uid: String,
	val uidNoMbid: String
)

@Entity(
	tableName = "LocalScanGenre",
	primaryKeys = ["scanSongId", "position"],
	indices = [Index(value = ["genreKey"])]
)
data class LocalScanGenreEntity(
	val scanSongId: Long,
	val position: Int,
	val genreKey: String,
	val rawName: String?,
	val displayName: String?
)

@Entity(tableName = "LocalScanArtistKey")
data class LocalScanArtistKeyEntity(
	@PrimaryKey val occurrenceKey: String,
	val artistKey: String
)

@Entity(tableName = "LocalScanArtistMap")
data class LocalScanArtistMapEntity(
	@PrimaryKey val artistKey: String,
	val displayName: String?,
	val rawName: String?,
	val sortName: String?,
	val mbid: String?
)

@Entity(tableName = "LocalScanArtistRoleMap", primaryKeys = ["artistKey", "role"])
data class LocalScanArtistRoleMapEntity(
	val artistKey: String,
	val role: Int,
	val displayName: String?,
	val rawName: String?,
	val sortName: String?,
	val mbid: String?
)

@Entity(tableName = "LocalScanAlbumKey")
data class LocalScanAlbumKeyEntity(
	@PrimaryKey val albumOccurrenceKey: String,
	val albumKey: String
)

@Entity(tableName = "LocalScanAlbumMap")
data class LocalScanAlbumMapEntity(
	@PrimaryKey val albumKey: String,
	val albumId: String,
	val displayName: String?,
	val rawName: String?,
	val songCount: Int,
	@ColumnInfo(defaultValue = "0") val sampleScanId: Long
)

@Entity(tableName = "LocalScanGenreMap")
data class LocalScanGenreMapEntity(
	@PrimaryKey val genreKey: String,
	val displayName: String?,
	val rawName: String?
)
