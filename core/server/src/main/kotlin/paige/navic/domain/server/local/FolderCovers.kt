/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.server.local

import android.content.Context
import org.oxycblt.musikr.covers.Cover
import org.oxycblt.musikr.covers.CoverResult
import org.oxycblt.musikr.covers.MemoryCover
import org.oxycblt.musikr.covers.MutableCovers
import org.oxycblt.musikr.covers.embedded.CoverIdentifier
import org.oxycblt.musikr.covers.fs.MutableFSCovers
import org.oxycblt.musikr.fs.File
import org.oxycblt.musikr.metadata.Metadata
import java.io.ByteArrayInputStream

// reads cover images that sit next to the audio files (cover.jpg etc.) into memory,
// so MutableStoredCovers can store them with the same "mcs:" ids embedded covers use
internal class FolderCovers(context: Context) : MutableCovers<MemoryCover> {
	private val source = MutableFSCovers(context)
	private val identifier = CoverIdentifier.md5()

	override suspend fun obtain(id: String): CoverResult<MemoryCover> = CoverResult.Miss()

	suspend fun hasCover(file: File) = source.hasCover(file)

	override suspend fun create(file: File, metadata: Metadata): CoverResult<MemoryCover> {
		val cover = when (val result = source.create(file, metadata)) {
			is CoverResult.Hit -> result.cover
			is CoverResult.Miss -> return CoverResult.Miss()
		}
		val data = cover.open()?.use { it.readBytes() } ?: return CoverResult.Miss()
		return CoverResult.Hit(MemoryBytesCover(identifier.identify(data), data))
	}

	override suspend fun cleanup(excluding: Collection<Cover>) = source.cleanup(excluding)
}

private class MemoryBytesCover(
	override val id: String,
	private val bytes: ByteArray
) : MemoryCover {
	override suspend fun open() = ByteArrayInputStream(bytes)
	override fun data() = bytes
	override fun equals(other: Any?) = other is MemoryBytesCover && id == other.id
	override fun hashCode() = id.hashCode()
}
