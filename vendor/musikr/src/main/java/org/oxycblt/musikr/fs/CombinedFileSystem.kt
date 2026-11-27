/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.oxycblt.musikr.fs

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.merge

class CombinedFileSystem(private val filesystems: List<FS>) : FS {
    override suspend fun explore(files: Channel<File>): Deferred<Result<Unit>> = coroutineScope {
        async(Dispatchers.IO) {
            try {
                val results = filesystems.map { filesystem ->
                    val source = Channel<File>(Channel.BUFFERED)
                    val task = filesystem.explore(source)
                    val pump = async {
                        for (file in source) files.send(file)
                    }
                    val result = task.await()
                    pump.await()
                    result
                }
                val failure = results.firstOrNull { it.isFailure }
                if (failure != null) {
                    files.close(failure.exceptionOrNull())
                    failure
                } else {
                    files.close()
                    Result.success(Unit)
                }
            } catch (error: Throwable) {
                files.close(error)
                Result.failure(error)
            }
        }
    }

    override fun track(): Flow<FSUpdate> =
        filesystems.map(FS::track).let { flows ->
            if (flows.isEmpty()) emptyFlow() else merge(*flows.toTypedArray())
        }
}
