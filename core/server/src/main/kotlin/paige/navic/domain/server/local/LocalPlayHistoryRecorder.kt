/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.server.local

import paige.navic.database.dao.AlbumDao
import paige.navic.database.dao.SongDao
import paige.navic.domain.server.PlayHistoryRecorder
import kotlin.time.Instant

class LocalPlayHistoryRecorder(
	private val songDao: SongDao,
	private val albumDao: AlbumDao
) : PlayHistoryRecorder {
	override suspend fun recordPlay(songId: String, playedAt: Instant) {
		val song = songDao.getSongById(songId) ?: return
		songDao.incrementPlayCount(songId)
		song.belongsToAlbumId?.let { albumId -> albumDao.recordPlay(albumId, playedAt) }
	}
}
