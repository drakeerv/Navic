/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.oxycblt.musikr.fs

import android.net.Uri
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.oxycblt.musikr.util.tryAsyncWith
import java.io.File as IoFile
import java.net.URLConnection

class DirectFileSystem private constructor(private val roots: List<IoFile>) : FS {
    override suspend fun explore(files: Channel<File>) = coroutineScope {
        tryAsyncWith(files, Dispatchers.IO) { channel ->
            roots.forEach { root ->
                require(root.isDirectory && root.canRead()) { "Unreadable music folder: $root" }
                val rootUri = Uri.fromFile(root)
                val rootPath = Path(Volume.ThirdParty(rootUri), Components.root())

                suspend fun visit(directory: IoFile, relative: Components) {
                    val entries = directory.listFiles() ?: run {
                        if (directory == root) {
                            throw IllegalStateException("Cannot read music folder: $directory")
                        }
                        return
                    }
                    val children = mutableListOf<File>()
                    val directoryDeferred = CompletableDeferred<Directory>()

                    entries.forEach { entry ->
                        if (entry.name.startsWith('.')) return@forEach
                        val childPath = Path(rootPath.volume, relative.child(entry.name))
                        if (entry.isDirectory) {
                            visit(entry, relative.child(entry.name))
                        } else if (entry.isFile && entry.canRead()) {
                            val uri = Uri.fromFile(entry)
                            val mime = URLConnection.guessContentTypeFromName(entry.name)
                                ?: "application/octet-stream"
                            val item = File(
                                uri = uri,
                                path = childPath,
                                addedMs = object : AddedMs {
                                    override suspend fun resolve() = entry.lastModified()
                                },
                                modifiedMs = entry.lastModified(),
                                mimeType = mime,
                                size = entry.length(),
                                parent = directoryDeferred
                            )
                            children.add(item)
                            channel.send(item)
                        }
                    }

                    directoryDeferred.complete(
                        Directory(Uri.fromFile(directory), Path(rootPath.volume, relative), null, children)
                    )
                }

                visit(root, Components.root())
            }
        }
    }

    override fun track(): Flow<FSUpdate> = callbackFlow {
        awaitClose { }
    }

    companion object {
        fun from(roots: List<IoFile>) = DirectFileSystem(roots.distinct())
    }
}
