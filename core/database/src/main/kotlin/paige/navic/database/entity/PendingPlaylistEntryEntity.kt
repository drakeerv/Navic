/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.database.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index

@Entity(
	tableName = "PendingPlaylistEntry",
	primaryKeys = ["playlistId", "position", "candidateIndex"],
	indices = [Index(value = ["playlistId"])],
	foreignKeys = [
		ForeignKey(
			entity = PlaylistEntity::class,
			parentColumns = ["playlistId"],
			childColumns = ["playlistId"],
			onDelete = ForeignKey.CASCADE
		)
	]
)
data class PendingPlaylistEntryEntity(
	val playlistId: String,
	val position: Int,
	val candidateIndex: Int,
	val pathKey: String
)
