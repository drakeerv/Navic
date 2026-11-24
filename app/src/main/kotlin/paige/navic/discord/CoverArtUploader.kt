/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.discord

import io.ktor.client.request.forms.FormBuilder
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders

data class CoverArtImage(
	val bytes: ByteArray,
	val contentType: ContentType,
	val fileName: String
) {
	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as CoverArtImage

		if (!bytes.contentEquals(other.bytes)) return false
		if (contentType != other.contentType) return false
		if (fileName != other.fileName) return false

		return true
	}

	override fun hashCode(): Int {
		var result = bytes.contentHashCode()
		result = 31 * result + contentType.hashCode()
		result = 31 * result + fileName.hashCode()
		return result
	}
}

interface CoverArtUploader {
	suspend fun upload(image: CoverArtImage): String
}

fun ByteArray.toCoverArtImage(): CoverArtImage? {
	val contentType = imageContentType() ?: return null

	return CoverArtImage(
		bytes = this,
		contentType = contentType,
		fileName = "cover.${contentType.contentSubtype}"
	)
}

private fun ByteArray.imageContentType(): ContentType? = when {
	hasMagic(0x89, 0x50, 0x4E, 0x47) -> ContentType.Image.PNG
	hasMagic(0xFF, 0xD8, 0xFF) -> ContentType.Image.JPEG
	hasMagic(0x47, 0x49, 0x46, 0x38) -> ContentType.Image.GIF
	hasMagic(0x52, 0x49, 0x46, 0x46) && size >= 12 &&
		String(this, 8, 4, Charsets.US_ASCII) == "WEBP" -> ContentType("image", "webp")
	hasMagic(0x42, 0x4D) -> ContentType("image", "bmp")
	else -> null
}

private fun ByteArray.hasMagic(vararg magic: Int): Boolean =
	size >= magic.size && magic.indices.all { this[it] == magic[it].toByte() }

fun String.toPublicImageUrl(): String? =
	trim().takeIf { it.startsWith("https://") && length < 128 }

fun FormBuilder.appendImage(field: String, image: CoverArtImage) {
	append(
		key = field,
		value = image.bytes,
		headers = Headers.build {
			append(HttpHeaders.ContentType, image.contentType.toString())
			append(HttpHeaders.ContentDisposition, "filename=\"${image.fileName}\"")
		}
	)
}
