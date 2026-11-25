/*
 * Copyright (c) 2026 Navic contributors.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.oxycblt.musikr.playlist

import org.oxycblt.musikr.fs.Path

/** Parsed M3U entries discovered while scanning the local library. */
data class M3UPlaylistImport(
	val sourceUri: String,
	val name: String?,
	val entries: List<List<Path>>,
	val parsed: Boolean = true
)
