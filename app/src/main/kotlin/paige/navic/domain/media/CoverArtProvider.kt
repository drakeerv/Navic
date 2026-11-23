/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.media

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject

class CoverArtProvider : ContentProvider() {
	private val coverArtCache: CoverArtCache by inject()

	override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
		require(mode == "r") { "Cover art is read-only" }
		val coverId = uri.pathSegments.singleOrNull()
			?: error("Invalid cover art URI")
		val file = runBlocking(Dispatchers.IO) {
			coverArtCache.ensure(coverId, uri.artworkSize())
		} ?: error("Could not load cover art $coverId")
		return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
	}

	private fun Uri.artworkSize(): Int = getQueryParameter("size")
		?.toIntOrNull()
		?.coerceIn(MIN_ARTWORK_SIZE, MAX_ARTWORK_SIZE)
		?: DEFAULT_ARTWORK_SIZE

	override fun onCreate() = true
	override fun getType(uri: Uri) = "image/*"
	override fun query(
		uri: Uri,
		projection: Array<out String>?,
		selection: String?,
		selectionArgs: Array<out String>?,
		sortOrder: String?
	): Cursor? = null
	override fun insert(uri: Uri, values: ContentValues?) = null
	override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
	override fun update(
		uri: Uri,
		values: ContentValues?,
		selection: String?,
		selectionArgs: Array<out String>?
	) = 0

	private companion object {
		const val DEFAULT_ARTWORK_SIZE = 512
		const val MIN_ARTWORK_SIZE = 128
		const val MAX_ARTWORK_SIZE = 4096
	}
}
