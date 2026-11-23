/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import paige.navic.domain.manager.runtime.StorageManager
import paige.navic.domain.manager.session.SessionManager
import paige.navic.domain.model.settings.CoverArtQuality
import paige.navic.preferences.PreferenceManager
import paige.navic.util.Logger
import java.io.File
import kotlin.math.abs

class CoverArtCache(
	private val storageManager: StorageManager,
	private val sessionManager: SessionManager,
	private val preferenceManager: PreferenceManager
) {
	fun file(coverId: String, size: Int? = null): File? =
		storageManager.getCoverArtFile(coverId, bucket(size))

	suspend fun ensure(coverId: String, size: Int? = null): File? = withContext(Dispatchers.IO) {
		val bucket = bucket(size)
		val cached = storageManager.getCoverArtFile(coverId, bucket)
		if (cached != null && !cached.isStale()) return@withContext cached

		val bytes = runCatching {
			sessionManager.api.getCoverArt(
				coverId,
				size = bucket.toString()
			)
		}.onFailure {
			Logger.e("CoverArtCache", "failed to fetch cover $coverId", it)
		}.getOrNull()

		if (bytes == null || bytes.isEmpty()) return@withContext cached
		if (!storageManager.saveCoverArt(coverId, bytes, bucket)) {
			Logger.w("CoverArtCache", "could not save cover $coverId")
			return@withContext cached
		}
		storageManager.getCoverArtFile(coverId, bucket) ?: cached
	}

	private fun bucket(size: Int?): Int {
		val requested = size ?: preferenceManager.coverArtQuality.value
		return CoverArtQuality.entries.minBy { abs(it.value - requested) }.value
	}

	private fun File.isStale(): Boolean =
		System.currentTimeMillis() - lastModified() > COVER_ART_TTL_MS

	fun delete(coverId: String) {
		storageManager.deleteCoverArt(coverId)
	}

	fun clear() {
		storageManager.clearCoverArts()
	}

	private companion object {
		const val COVER_ART_TTL_MS = 7L * 24 * 60 * 60 * 1000
	}
}
