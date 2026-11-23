/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.server.local

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

internal fun ByteArray.transcodeCover(maxSize: Int): ByteArray {
	val bounds =
		BitmapFactory.Options().run {
			inJustDecodeBounds = true
			BitmapFactory.decodeByteArray(this@transcodeCover, 0, size, this)
			this
		}
	if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return this
	if (bounds.outWidth <= maxSize && bounds.outHeight <= maxSize) return this

	var sampleSize = 1
	while (bounds.outWidth / (sampleSize * 2) >= maxSize ||
		bounds.outHeight / (sampleSize * 2) >= maxSize
	) {
		sampleSize *= 2
	}

	val decoded =
		BitmapFactory.decodeByteArray(
			this,
			0,
			size,
			BitmapFactory.Options().apply { inSampleSize = sampleSize }
		) ?: return this

	val scale = maxSize.toFloat() / maxOf(decoded.width, decoded.height)
	val targetWidth = (decoded.width * scale).toInt().coerceAtLeast(1)
	val targetHeight = (decoded.height * scale).toInt().coerceAtLeast(1)
	val scaled =
		if (targetWidth == decoded.width && targetHeight == decoded.height) {
			decoded
		} else {
			Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true)
				.also { if (it !== decoded) decoded.recycle() }
		}

	return ByteArrayOutputStream().use { output ->
		val compressed = scaled.compress(Bitmap.CompressFormat.JPEG, 90, output)
		scaled.recycle()
		if (compressed) output.toByteArray() else this
	}
}
