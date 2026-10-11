/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.util

import androidx.room3.RoomRawQuery
import androidx.sqlite.SQLiteStatement
import paige.navic.domain.model.DomainAlbumListType
import paige.navic.domain.model.DomainSongListType

private fun bindArgument(statement: SQLiteStatement, bindIndex: Int, arg: Any) {
	when (arg) {
		is String -> statement.bindText(bindIndex, arg)
		is Int -> statement.bindInt(bindIndex, arg)
		is Long -> statement.bindLong(bindIndex, arg)
		is Float -> statement.bindFloat(bindIndex, arg)
		is Double -> statement.bindDouble(bindIndex, arg)
		is Boolean -> statement.bindInt(bindIndex, if (arg) 1 else 0)
	}
}

fun DomainSongListType.toSqlQuery(
	reversed: Boolean,
	starredOnly: Boolean
): RoomRawQuery {
	val conditions = mutableListOf<String>()
	val args = mutableListOf<Any>()

	if (starredOnly) conditions += "starredAt IS NOT NULL"

	val dir = if (reversed) "ASC" else "DESC"
	val tieDir = if (reversed) "DESC" else "ASC"

	val orderBy = when (this) {
		DomainSongListType.FrequentlyPlayed -> "playCount $dir, songId $tieDir"
		DomainSongListType.Rating -> "COALESCE(userRating, 0) $dir, songId $tieDir"
		DomainSongListType.Year -> "year $dir, songId $tieDir"
		DomainSongListType.Newest ->
			"(SELECT a.createdAt FROM AlbumEntity a WHERE a.albumId = SongEntity.belongsToAlbumId) $dir, songId $tieDir"

		DomainSongListType.Random -> "RANDOM()"

		is DomainSongListType.ByArtist -> {
			conditions += "artistId = ?"
			args += artistId
			"playCount $dir, songId $tieDir"
		}

		is DomainSongListType.ByGenre -> {
			conditions += "(genre = ? OR genres = ? OR genres LIKE ? OR genres LIKE ? OR genres LIKE ?)"
			args += genre
			args += genre
			args += "$genre||%"
			args += "%||$genre"
			args += "%||$genre||%"
			"playCount $dir, songId $tieDir"
		}
	}

	val whereClause = if (conditions.isEmpty()) "" else " WHERE " + conditions.joinToString(" AND ")
	val sql = "SELECT SongEntity.* FROM SongEntity$whereClause ORDER BY $orderBy"

	return RoomRawQuery(sql) { statement ->
		args.forEachIndexed { index, arg -> bindArgument(statement, index + 1, arg) }
	}
}

fun DomainAlbumListType.toSqlQuery(): RoomRawQuery {
	var where: String? = null
	var orderBy: String
	val args = mutableListOf<Any>()

	when (this) {
		DomainAlbumListType.AlphabeticalByArtist -> orderBy = "LOWER(artistName) ASC"
		DomainAlbumListType.AlphabeticalByName -> orderBy = "LOWER(name) ASC"
		DomainAlbumListType.Frequent -> {
			where = "playCount != 0"
			orderBy = "playCount DESC"
		}

		DomainAlbumListType.Highest -> orderBy = "userRating DESC"
		DomainAlbumListType.Newest -> orderBy = "createdAt DESC"
		DomainAlbumListType.Random -> orderBy = "RANDOM()"
		DomainAlbumListType.Recent -> orderBy = "lastPlayedAt DESC"

		DomainAlbumListType.Year -> {
			orderBy = "year DESC"
		}

		is DomainAlbumListType.ByGenre -> {
			where = "genre = ?"
			orderBy = "LOWER(name) ASC"
			args.add(genre)
		}

		is DomainAlbumListType.ByYear -> {
			where = "COALESCE(year, 0) BETWEEN ? AND ?"
			orderBy = "LOWER(name) ASC"
			args.add(fromYear)
			args.add(toYear)
		}
	}

	val whereClause = where?.let { " WHERE $it" } ?: ""
	val sql = "SELECT * FROM AlbumEntity$whereClause ORDER BY $orderBy"

	return RoomRawQuery(sql) { statement ->
		args.forEachIndexed { index, arg -> bindArgument(statement, index + 1, arg) }
	}
}
