/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.server

enum class SyncProgressMessage {
	Indexing,
	Saved,
	Finished
}

interface LibraryIndexer {
	suspend fun index(onProgress: suspend (Float, SyncProgressMessage) -> Unit = { _, _ -> })
}
