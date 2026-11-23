/*
 * Copyright (c) 2024 Auxio Project
 * EvaluateStep.kt is part of Auxio.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.oxycblt.musikr.pipeline

import android.content.Context
import kotlinx.coroutines.channels.Channel
import org.oxycblt.musikr.Config
import org.oxycblt.musikr.Interpretation
import org.oxycblt.musikr.LibraryWriter
import org.oxycblt.musikr.tag.interpret.TagInterpreter

internal interface EvaluateStep {
    suspend fun evaluate(extractedMusic: Channel<Extracted>, writer: LibraryWriter)

    companion object {
        fun new(context: Context, config: Config, interpretation: Interpretation): EvaluateStep =
            EvaluateStepImpl(TagInterpreter.new(interpretation))
    }
}

private class EvaluateStepImpl(private val tagInterpreter: TagInterpreter) : EvaluateStep {
    override suspend fun evaluate(extractedMusic: Channel<Extracted>, writer: LibraryWriter) {
        for (extracted in extractedMusic) {
            when (extracted) {
                is RawSong -> writer.write(tagInterpreter.interpret(extracted))
                // Imported playlists are not part of the local library yet.
                is RawPlaylist -> {}
                is NotAudio -> {}
                is InvalidSong -> {}
            }
        }
    }
}
