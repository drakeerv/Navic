/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import paige.navic.domain.model.DomainDiscTitle
import kotlin.time.Duration
import kotlin.time.Instant

@Entity(
	indices = [
		Index(value = ["artistId"]),
		Index(value = ["starredAt"])
	]
)
data class AlbumEntity(
	@PrimaryKey val albumId: String,
	val name: String?,
	val artistName: String?,
	val artistId: String,
	val displayArtist: String?,
	val year: Int?,
	val coverArtId: String?,
	val genre: String?,
	val genres: List<String>,
	val songCount: Int,
	val duration: Duration?,
	val createdAt: Instant,
	val starredAt: Instant?,
	val lastPlayedAt: Instant?,
	val playCount: Int = 0,
	val userRating: Int?,
	val version: String?,
	val musicBrainzId: String?,
	val isExternal: Boolean,
	@ColumnInfo(defaultValue = "") val discTitles: List<DomainDiscTitle>
)
