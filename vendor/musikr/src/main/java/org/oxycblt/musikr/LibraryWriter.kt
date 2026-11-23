/*
 * Copyright (c) 2026 Nightdavisao & contributors
 * LibraryWriter.kt is part of Musikr.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.oxycblt.musikr

import org.oxycblt.musikr.tag.interpret.PreSong

/**
 * Side-effect laden target for interpreted songs.
 *
 * Implementations persist the streamed [PreSong]s and are responsible for any deduplication. This
 * is the replacement for Musikr's in-memory library graph.
 */
interface LibraryWriter {
    /** Start a new scan. Any incomplete staging state should be discarded. */
    suspend fun begin()

    /** Persist one interpreted song. */
    suspend fun write(song: PreSong)

    /**
     * Finish the scan.
     *
     * @return the cover IDs still referenced by the resulting library, so unused covers can be
     *   cleaned up.
     */
    suspend fun finish(): Set<String>
}
