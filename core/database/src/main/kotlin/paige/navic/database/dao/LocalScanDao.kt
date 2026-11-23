/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import paige.navic.database.entity.LocalScanAlbumKeyEntity
import paige.navic.database.entity.LocalScanAlbumMapEntity
import paige.navic.database.entity.LocalScanArtistEntity
import paige.navic.database.entity.LocalScanArtistKeyEntity
import paige.navic.database.entity.LocalScanArtistMapEntity
import paige.navic.database.entity.LocalScanArtistRoleMapEntity
import paige.navic.database.entity.LocalScanGenreEntity
import paige.navic.database.entity.LocalScanGenreMapEntity
import paige.navic.database.entity.LocalScanSongEntity

data class ScanSongAlbumRow(
	val scanSongId: Long,
	val albumKey: String,
	val albumId: String,
	val displayName: String?,
	val rawName: String?
)

data class ScanArtistRow(
	val scanSongId: Long,
	val role: Int,
	val position: Int,
	val artistKey: String,
	val displayName: String?
)

data class ScanAlbumArtistRow(
	val albumKey: String,
	val position: Int,
	val scanSongId: Long,
	val artistKey: String,
	val displayName: String?,
	val canonicalRank: Int
)

data class ScanGenreRow(
	val scanSongId: Long,
	val position: Int,
	val genreKey: String,
	val displayName: String?
)

data class ScanAlbumAggregate(
	val albumKey: String,
	val albumId: String,
	val displayName: String?,
	val rawName: String?,
	val songCount: Int,
	val durationMs: Long,
	val year: Int?,
	val addedMs: Long
)

data class ScanAlbumGenreRow(
	val albumKey: String,
	val displayName: String?,
	val firstSong: Long,
	val firstPosition: Int
)

data class ScanAlbumCoverRow(
	val albumKey: String,
	val coverId: String,
	val uses: Int
)

data class ScanArtistAlbumCountRow(
	val artistKey: String,
	val albumCount: Int
)

data class ScanArtistFirstRow(
	val finalKey: String,
	val role: Int,
	val nameKey: String,
	val displayName: String?,
	val rawName: String?,
	val sortName: String?,
	val mbid: String?,
	val popularity: Int,
	val sampleScanId: Long
)

data class ScanArtistClusterCountRow(
	val finalKey: String,
	val role: Int,
	val nameKey: String,
	val songCount: Int
)

data class ScanAlbumFirstRow(
	val finalKey: String,
	val firstClusterKey: String,
	val albumId: String,
	val albumIdNoMbid: String,
	val displayName: String?,
	val rawName: String?,
	val popularity: Int,
	val sampleScanId: Long
)

data class ScanAlbumClusterCountRow(
	val finalKey: String,
	val firstClusterKey: String,
	val songCount: Int
)

data class ScanGenreSongCountRow(
	val genreKey: String,
	val songCount: Int
)

data class ScanGenreArtistRow(
	val genreKey: String,
	val artistKey: String
)

@Dao
interface LocalScanDao {
	@Query("DELETE FROM LocalScanSong")
	suspend fun clearSongs()

	@Query("DELETE FROM LocalScanArtist")
	suspend fun clearArtists()

	@Query("DELETE FROM LocalScanGenre")
	suspend fun clearGenres()

	@Query("DELETE FROM LocalScanArtistKey")
	suspend fun clearArtistKeys()

	@Query("DELETE FROM LocalScanArtistMap")
	suspend fun clearArtistMaps()

	@Query("DELETE FROM LocalScanArtistRoleMap")
	suspend fun clearArtistRoleMaps()

	@Query("DELETE FROM LocalScanAlbumKey")
	suspend fun clearAlbumKeys()

	@Query("DELETE FROM LocalScanAlbumMap")
	suspend fun clearAlbumMaps()

	@Query("DELETE FROM LocalScanGenreMap")
	suspend fun clearGenreMaps()

	@Insert
	suspend fun insertSong(song: LocalScanSongEntity): Long

	@Insert
	suspend fun insertArtists(artists: List<LocalScanArtistEntity>)

	@Insert
	suspend fun insertGenres(genres: List<LocalScanGenreEntity>)

	@Insert
	suspend fun insertArtistMaps(maps: List<LocalScanArtistMapEntity>)

	@Insert
	suspend fun insertArtistRoleMaps(maps: List<LocalScanArtistRoleMapEntity>)

	@Insert
	suspend fun insertAlbumMaps(maps: List<LocalScanAlbumMapEntity>)

	@Query("DELETE FROM LocalScanSong WHERE id NOT IN (SELECT MIN(id) FROM LocalScanSong GROUP BY songId)")
	suspend fun deleteDuplicateSongs()

	@Query("DELETE FROM LocalScanArtist WHERE scanSongId NOT IN (SELECT id FROM LocalScanSong)")
	suspend fun deleteOrphanArtists()

	@Query("DELETE FROM LocalScanGenre WHERE scanSongId NOT IN (SELECT id FROM LocalScanSong)")
	suspend fun deleteOrphanGenres()

	@Query(
		"""
		INSERT INTO LocalScanGenreMap (genreKey, displayName, rawName)
		SELECT genreKey, displayName, rawName
		FROM (
			SELECT g.genreKey AS genreKey, g.displayName AS displayName, g.rawName AS rawName,
				ROW_NUMBER() OVER (
					PARTITION BY g.genreKey
					ORDER BY COUNT(DISTINCT s.songId) DESC, MIN(s.id), g.displayName
				) AS rn
			FROM LocalScanGenre g
			JOIN LocalScanSong s ON s.id = g.scanSongId
			GROUP BY g.genreKey, g.displayName, g.rawName
		)
		WHERE rn = 1
		"""
	)
	suspend fun buildGenreMap()

	@Query(
		"""
		INSERT INTO LocalScanArtistKey (occurrenceKey, artistKey)
		SELECT occ.occurrenceKey,
			CASE
				WHEN ng.allMbid = 1 THEN 'm:' || COALESCE(occ.mbid, '')
				ELSE 'n:' || occ.nameKey
			END
		FROM (
			SELECT DISTINCT occurrenceKey, nameKey, mbid
			FROM LocalScanArtist
		) occ
		JOIN (
			SELECT nameKey, MIN(mbid IS NOT NULL) AS allMbid
			FROM (
				SELECT DISTINCT nameKey, mbid
				FROM LocalScanArtist
			)
			GROUP BY nameKey
		) ng ON ng.nameKey = occ.nameKey
		"""
	)
	suspend fun buildArtistKeys()

	@Query(
		"""
		SELECT k.artistKey AS finalKey, a.role AS role, a.nameKey AS nameKey,
			a.displayName AS displayName, a.rawName AS rawName, a.sortName AS sortName,
			a.mbid AS mbid, COUNT(DISTINCT a.scanSongId) AS popularity,
			MIN(a.scanSongId) AS sampleScanId
		FROM LocalScanArtist a
		JOIN LocalScanArtistKey k ON k.occurrenceKey = a.occurrenceKey
		GROUP BY k.artistKey, a.role, a.nameKey, a.displayName, a.rawName, a.sortName, a.mbid
		"""
	)
	suspend fun getArtistFirstClusters(): List<ScanArtistFirstRow>

	@Query(
		"""
		SELECT k.artistKey AS finalKey, a.role AS role, a.nameKey AS nameKey,
			COUNT(DISTINCT a.scanSongId) AS songCount
		FROM LocalScanArtist a
		JOIN LocalScanArtistKey k ON k.occurrenceKey = a.occurrenceKey
		GROUP BY k.artistKey, a.role, a.nameKey
		"""
	)
	suspend fun getArtistClusterCounts(): List<ScanArtistClusterCountRow>

	@Query(
		"""
		INSERT INTO LocalScanAlbumKey (albumOccurrenceKey, albumKey)
		SELECT occ.albumOccurrenceKey,
			CASE
				WHEN ng.allMbid = 1 THEN 'm:' || COALESCE(occ.albumMbid, '')
				WHEN ng.allAlbumArtists = 1 THEN 'a:' || occ.albumNameKey || char(31) || occ.albumArtistRawNamesKey
				ELSE 'n:' || occ.albumNameKey
			END
		FROM (
			SELECT DISTINCT albumOccurrenceKey, albumNameKey, albumMbid,
				albumArtistSource, albumArtistRawNamesKey
			FROM LocalScanSong
		) occ
		JOIN (
			SELECT albumNameKey, MIN(albumMbid IS NOT NULL) AS allMbid,
				MIN(albumArtistSource = 1) AS allAlbumArtists
			FROM (
				SELECT DISTINCT albumOccurrenceKey, albumNameKey, albumMbid, albumArtistSource
				FROM LocalScanSong
			)
			GROUP BY albumNameKey
		) ng ON ng.albumNameKey = occ.albumNameKey
		"""
	)
	suspend fun buildAlbumKeys()

	@Query(
		"""
		SELECT k.albumKey AS finalKey,
			CASE
				WHEN substr(k.albumKey, 1, 2) = 'm:' THEN s.albumNameKey
				WHEN substr(k.albumKey, 1, 2) = 'a:' THEN s.albumArtistIdentityKey
				ELSE ''
			END AS firstClusterKey,
			s.albumId AS albumId, s.albumIdNoMbid AS albumIdNoMbid,
			s.albumDisplayName AS displayName, s.albumRawName AS rawName,
			COUNT(DISTINCT s.songId) AS popularity, MIN(s.id) AS sampleScanId
		FROM LocalScanSong s
		JOIN LocalScanAlbumKey k ON k.albumOccurrenceKey = s.albumOccurrenceKey
		GROUP BY k.albumKey,
			CASE
				WHEN substr(k.albumKey, 1, 2) = 'm:' THEN s.albumNameKey
				WHEN substr(k.albumKey, 1, 2) = 'a:' THEN s.albumArtistIdentityKey
				ELSE ''
			END,
			CASE
				WHEN substr(k.albumKey, 1, 2) = 'm:' THEN s.albumOccurrenceKey
				ELSE s.albumOccurrenceKeyNoMbid
			END,
			s.albumId, s.albumIdNoMbid, s.albumDisplayName, s.albumRawName
		"""
	)
	suspend fun getAlbumFirstClusters(): List<ScanAlbumFirstRow>

	@Query(
		"""
		SELECT k.albumKey AS finalKey,
			CASE
				WHEN substr(k.albumKey, 1, 2) = 'm:' THEN s.albumNameKey
				WHEN substr(k.albumKey, 1, 2) = 'a:' THEN s.albumArtistIdentityKey
				ELSE ''
			END AS firstClusterKey,
			COUNT(DISTINCT s.songId) AS songCount
		FROM LocalScanSong s
		JOIN LocalScanAlbumKey k ON k.albumOccurrenceKey = s.albumOccurrenceKey
		GROUP BY k.albumKey,
			CASE
				WHEN substr(k.albumKey, 1, 2) = 'm:' THEN s.albumNameKey
				WHEN substr(k.albumKey, 1, 2) = 'a:' THEN s.albumArtistIdentityKey
				ELSE ''
			END
		"""
	)
	suspend fun getAlbumClusterCounts(): List<ScanAlbumClusterCountRow>

	@Query("SELECT * FROM LocalScanSong WHERE id > :afterId ORDER BY id LIMIT :limit")
	suspend fun getSongBatch(afterId: Long, limit: Int): List<LocalScanSongEntity>

	@Query(
		"""
		SELECT s.id AS scanSongId, k.albumKey AS albumKey, m.albumId AS albumId,
			m.displayName AS displayName, m.rawName AS rawName
		FROM LocalScanSong s
		JOIN LocalScanAlbumKey k ON k.albumOccurrenceKey = s.albumOccurrenceKey
		JOIN LocalScanAlbumMap m ON m.albumKey = k.albumKey
		WHERE s.id IN (:ids)
		"""
	)
	suspend fun getSongAlbums(ids: List<Long>): List<ScanSongAlbumRow>

	@Query(
		"""
		SELECT a.scanSongId AS scanSongId, a.role AS role, a.position AS position,
			k.artistKey AS artistKey, m.displayName AS displayName
		FROM LocalScanArtist a
		JOIN LocalScanArtistKey k ON k.occurrenceKey = a.occurrenceKey
		JOIN LocalScanArtistRoleMap m ON m.artistKey = k.artistKey AND m.role = a.role
		WHERE a.scanSongId IN (:ids)
		ORDER BY a.scanSongId, a.role, a.position
		"""
	)
	suspend fun getSongArtists(ids: List<Long>): List<ScanArtistRow>

	@Query(
		"""
		SELECT g.scanSongId AS scanSongId, g.position AS position, g.genreKey AS genreKey,
			m.displayName AS displayName
		FROM LocalScanGenre g
		JOIN LocalScanGenreMap m ON m.genreKey = g.genreKey
		WHERE g.scanSongId IN (:ids)
		ORDER BY g.scanSongId, g.position
		"""
	)
	suspend fun getSongGenres(ids: List<Long>): List<ScanGenreRow>

	@Query(
		"""
		SELECT DISTINCT k.albumKey AS albumKey, a.position AS position, a.scanSongId AS scanSongId,
			ak.artistKey AS artistKey, m.displayName AS displayName,
			CASE WHEN a.scanSongId = am.sampleScanId THEN 0 ELSE 1 END AS canonicalRank
		FROM LocalScanSong s
		JOIN LocalScanAlbumKey k ON k.albumOccurrenceKey = s.albumOccurrenceKey
		JOIN LocalScanAlbumMap am ON am.albumKey = k.albumKey
		JOIN LocalScanArtist a ON a.scanSongId = s.id AND a.role = 1
		JOIN LocalScanArtistKey ak ON ak.occurrenceKey = a.occurrenceKey
		JOIN LocalScanArtistRoleMap m ON m.artistKey = ak.artistKey AND m.role = a.role
		WHERE k.albumKey IN (:albumKeys)
		ORDER BY k.albumKey, canonicalRank, a.position, a.scanSongId
		"""
	)
	suspend fun getAlbumArtists(albumKeys: List<String>): List<ScanAlbumArtistRow>

	@Query(
		"""
		SELECT DISTINCT k.albumKey AS albumKey, a.position AS position, a.scanSongId AS scanSongId,
			ak.artistKey AS artistKey, m.displayName AS displayName,
			CASE WHEN a.scanSongId = am.sampleScanId THEN 0 ELSE 1 END AS canonicalRank
		FROM LocalScanSong s
		JOIN LocalScanAlbumKey k ON k.albumOccurrenceKey = s.albumOccurrenceKey
		JOIN LocalScanAlbumMap am ON am.albumKey = k.albumKey
		JOIN LocalScanArtist a ON a.scanSongId = s.id AND a.role = 1
		JOIN LocalScanArtistKey ak ON ak.occurrenceKey = a.occurrenceKey
		JOIN LocalScanArtistRoleMap m ON m.artistKey = ak.artistKey AND m.role = a.role
		ORDER BY k.albumKey, canonicalRank, a.position, a.scanSongId
		"""
	)
	suspend fun getAllAlbumArtists(): List<ScanAlbumArtistRow>

	@Query(
		"""
		SELECT k.albumKey AS albumKey, m.albumId AS albumId, m.displayName AS displayName,
			m.rawName AS rawName, COUNT(DISTINCT s.songId) AS songCount,
			SUM(s.duration) AS durationMs, MIN(s.year) AS year, MIN(s.addedMs) AS addedMs
		FROM LocalScanSong s
		JOIN LocalScanAlbumKey k ON k.albumOccurrenceKey = s.albumOccurrenceKey
		JOIN LocalScanAlbumMap m ON m.albumKey = k.albumKey
		GROUP BY k.albumKey, m.albumId, m.displayName, m.rawName
		"""
	)
	suspend fun getAlbumAggregates(): List<ScanAlbumAggregate>

	@Query(
		"""
		SELECT k.albumKey AS albumKey, m.displayName AS displayName,
			MIN(s.id) AS firstSong, MIN(g.position) AS firstPosition
		FROM LocalScanSong s
		JOIN LocalScanAlbumKey k ON k.albumOccurrenceKey = s.albumOccurrenceKey
		JOIN LocalScanGenre g ON g.scanSongId = s.id
		JOIN LocalScanGenreMap m ON m.genreKey = g.genreKey
		GROUP BY k.albumKey, m.displayName
		ORDER BY k.albumKey, firstSong, firstPosition
		"""
	)
	suspend fun getAlbumGenres(): List<ScanAlbumGenreRow>

	@Query(
		"""
		SELECT k.albumKey AS albumKey, s.coverId AS coverId, COUNT(*) AS uses
		FROM LocalScanSong s
		JOIN LocalScanAlbumKey k ON k.albumOccurrenceKey = s.albumOccurrenceKey
		WHERE s.coverId IS NOT NULL
		GROUP BY k.albumKey, s.coverId
		ORDER BY k.albumKey, uses DESC, s.coverId DESC
		"""
	)
	suspend fun getAlbumCovers(): List<ScanAlbumCoverRow>

	@Query("SELECT * FROM LocalScanArtistMap")
	suspend fun getArtistMaps(): List<LocalScanArtistMapEntity>

	@Query("SELECT * FROM LocalScanArtistRoleMap")
	suspend fun getArtistRoleMaps(): List<LocalScanArtistRoleMapEntity>

	@Query(
		"""
		SELECT ak.artistKey AS artistKey, COUNT(DISTINCT k.albumKey) AS albumCount
		FROM LocalScanArtist a
		JOIN LocalScanArtistKey ak ON ak.occurrenceKey = a.occurrenceKey
		JOIN LocalScanSong s ON s.id = a.scanSongId
		JOIN LocalScanAlbumKey k ON k.albumOccurrenceKey = s.albumOccurrenceKey
		WHERE a.role = 1
		GROUP BY ak.artistKey
		"""
	)
	suspend fun getArtistAlbumCounts(): List<ScanArtistAlbumCountRow>

	@Query("SELECT * FROM LocalScanGenreMap")
	suspend fun getGenreMaps(): List<LocalScanGenreMapEntity>

	@Query(
		"""
		SELECT g.genreKey AS genreKey, COUNT(DISTINCT s.songId) AS songCount
		FROM LocalScanGenre g
		JOIN LocalScanSong s ON s.id = g.scanSongId
		GROUP BY g.genreKey
		"""
	)
	suspend fun getGenreSongCounts(): List<ScanGenreSongCountRow>

	@Query(
		"""
		SELECT DISTINCT g.genreKey AS genreKey, ak.artistKey AS artistKey
		FROM LocalScanGenre g
		JOIN LocalScanSong s ON s.id = g.scanSongId
		JOIN LocalScanArtist a ON a.scanSongId = s.id AND a.role = 0
		JOIN LocalScanArtistKey ak ON ak.occurrenceKey = a.occurrenceKey
		"""
	)
	suspend fun getGenreArtistLinks(): List<ScanGenreArtistRow>

	@Query("SELECT COUNT(*) FROM LocalScanSong")
	suspend fun getScanSongCount(): Int
}
