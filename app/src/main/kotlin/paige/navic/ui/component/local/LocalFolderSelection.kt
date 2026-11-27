/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.component.local

import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.net.toUri
import paige.navic.R
import paige.navic.icons.Icons
import paige.navic.icons.outlined.ArrowBack
import paige.navic.icons.outlined.Delete
import paige.navic.icons.outlined.Folder
import paige.navic.icons.outlined.SdCard
import paige.navic.ui.component.common.SegmentedListItem
import paige.navic.ui.component.common.SegmentedListItemDefaults
import java.io.File

@Composable
fun LocalFolderSelection(
	modifier: Modifier = Modifier,
	folderUris: List<String>,
	enabled: Boolean = true,
	onFoldersChanged: (List<String>) -> Unit,
	onPermissionError: () -> Unit
) {
	val context = LocalContext.current
	val storageRoots = remember(context) {
		(context.getExternalFilesDirs(null).asList() + Environment.getExternalStorageDirectory())
			.mapNotNull { it?.let(::storageRoot) }
			.distinctBy { it.absolutePath }
	}
	var directory by remember(storageRoots) {
		mutableStateOf(
			storageRoots.firstOrNull() ?: Environment.getExternalStorageDirectory()
		)
	}
	var showDirectoryPicker by remember { mutableStateOf(false) }
	var choosingRoot by remember { mutableStateOf(false) }
	val permission = rememberLauncherForActivityResult(RequestPermission()) { granted ->
		if (granted) {
			choosingRoot = storageRoots.size > 1
			showDirectoryPicker = true
		} else {
			onPermissionError()
		}
	}

	val picker = rememberLauncherForActivityResult(
		ActivityResultContracts.OpenDocumentTree()
	) { uri ->
		if (uri == null) return@rememberLauncherForActivityResult

		val persisted = runCatching {
			context.contentResolver.takePersistableUriPermission(
				uri,
				Intent.FLAG_GRANT_READ_URI_PERMISSION
			)
		}.isSuccess

		if (!persisted) {
			onPermissionError()
			return@rememberLauncherForActivityResult
		}

		onFoldersChanged((folderUris + uri.toString()).distinct())
	}

	Column(
		modifier = modifier
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.animateContentSize(),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			Button(
				modifier = Modifier.fillMaxWidth(),
				onClick = {
					if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
						if (ContextCompat.checkSelfPermission(
								context,
								Manifest.permission.READ_EXTERNAL_STORAGE
							) == PackageManager.PERMISSION_GRANTED
						) {
							choosingRoot = storageRoots.size > 1
							showDirectoryPicker = true
						} else {
							permission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
						}
					} else {
						picker.launch(null)
					}
				},
				enabled = enabled
			) {
				val label = stringResource(
					if (folderUris.isEmpty()) {
						R.string.action_choose_music_folders
					} else {
						R.string.action_add_music_folder
					}
				)
				Text(text = label)
			}

			Column(
				verticalArrangement = Arrangement.spacedBy(SegmentedListItemDefaults.segmentedGap())
			) {
				folderUris.forEachIndexed { index, uri ->
					val folder = uri.toFolderDisplayName()

					SegmentedListItem(
						shapes = SegmentedListItemDefaults.segmentedShapes(
							index = index,
							count = folderUris.size
						),
						enabled = enabled,
						leadingContent = {
							Icon(
								imageVector = Icons.Outlined.Folder,
								contentDescription = null
							)
						},
						trailingContent = {
							IconButton(
								onClick = {
									onFoldersChanged(folderUris.filterIndexed { i, _ -> i != index })
								},
								enabled = enabled
							) {
								Icon(
									imageVector = Icons.Outlined.Delete,
									contentDescription = stringResource(R.string.action_remove_folder)
								)
							}
						},
						content = {
							Text(folder.name)
						},
						supportingContent = {
							if (folder.parentPath.isNotBlank()) {
								Text(
									text = folder.parentPath,
									style = MaterialTheme.typography.labelSmall
								)
							}
						}
					)
				}
			}
		}
	}

	if (showDirectoryPicker) {
		if (choosingRoot) {
			AlertDialog(
				onDismissRequest = {
					showDirectoryPicker = false
					choosingRoot = false
				},
				text = {
					Column(
						verticalArrangement = Arrangement.spacedBy(SegmentedListItemDefaults.segmentedGap())
					) {
						storageRoots.forEachIndexed { index, root ->
							val isInternal = root == Environment.getExternalStorageDirectory()
							SegmentedListItem(
								onClick = {
									directory = root
									choosingRoot = false
								},
								shapes = SegmentedListItemDefaults.segmentedShapes(
									index = index,
									count = storageRoots.size
								),
								leadingContent = {
									Icon(
										imageVector = if (isInternal) {
											Icons.Outlined.Folder
										} else {
											Icons.Outlined.SdCard
										},
										contentDescription = null
									)
								},
								content = {
									Text(
										stringResource(
											if (isInternal) {
												R.string.option_download_storage_internal
											} else {
												R.string.option_download_storage_external
											}
										)
									)
								},
								supportingContent = { Text(root.absolutePath) }
							)
						}
					}
				},
				confirmButton = {
					TextButton(
						onClick = {
							showDirectoryPicker = false
							choosingRoot = false
						}
					) {
						Text(stringResource(R.string.action_cancel))
					}
				}
			)
		} else {
			val directories by produceState(initialValue = emptyList<File>(), directory) {
				value = withContext(Dispatchers.IO) {
					directory.listFiles()
						?.filter { it.isDirectory && it.canRead() }
						?.sortedBy { it.name.lowercase() }
						.orEmpty()
				}
			}
			val parent = directory.parentFile?.takeIf {
				it.canRead() && storageRoots.any { root ->
					directory.isWithin(root) && directory != root
				}
			}
			val canChooseRoot = storageRoots.size > 1 && storageRoots.any { directory == it }
			val canGoUp = parent != null || canChooseRoot
			val itemCount = directories.size + 1
			AlertDialog(
				onDismissRequest = {
					showDirectoryPicker = false
					choosingRoot = false
				},
				title = { Text(directory.absolutePath) },
				text = {
					LazyColumn(
						verticalArrangement = Arrangement.spacedBy(SegmentedListItemDefaults.segmentedGap())
					) {
						item {
							SegmentedListItem(
								onClick = {
									if (parent != null) {
										directory = parent
									} else {
										choosingRoot = true
									}
								},
								enabled = canGoUp,
								shapes = SegmentedListItemDefaults.segmentedShapes(
									index = 0,
									count = itemCount
								),
								leadingContent = {
									Icon(
										imageVector = Icons.Outlined.ArrowBack,
										contentDescription = null
									)
								},
								content = { Text("..") }
							)
						}
						itemsIndexed(
							items = directories,
							key = { _, child -> "$child" }
						) { index, child ->
							SegmentedListItem(
								onClick = { directory = child },
								shapes = SegmentedListItemDefaults.segmentedShapes(
									index = index + 1,
									count = itemCount
								),
								leadingContent = {
									Icon(
										imageVector = Icons.Outlined.Folder,
										contentDescription = null
									)
								},
								content = { Text(child.name) }
							)
						}
					}
				},
				confirmButton = {
					TextButton(
						onClick = {
							val uri = Uri.fromFile(directory).toString()
							onFoldersChanged((folderUris + uri).distinct())
							showDirectoryPicker = false
						}
					) {
						Text(stringResource(R.string.action_select_this_folder))
					}
				},
				dismissButton = {
					TextButton(
						onClick = {
							showDirectoryPicker = false
							choosingRoot = false
						}) {
						Text(stringResource(R.string.action_cancel))
					}
				}
			)
		}
	}
}

private fun storageRoot(externalFilesDir: File): File? =
	generateSequence(externalFilesDir) { it.parentFile }
		.drop(4)
		.firstOrNull()

private fun File.isWithin(root: File): Boolean =
	absolutePath == root.absolutePath || absolutePath.startsWith("${root.absolutePath}${File.separator}")

private data class FolderDisplayName(
	val name: String,
	val parentPath: String
)

private fun String.toFolderDisplayName(): FolderDisplayName {
	val uri = toUri()
	if (uri.scheme == "file") {
		val path = uri.path.orEmpty()
		return FolderDisplayName(
			path.substringAfterLast('/'),
			path.substringBeforeLast(
				'/',
				""
			)
		)
	}

	val documentId = runCatching {
		DocumentsContract.getTreeDocumentId(uri)
	}.getOrNull()

	val path = documentId
		?.substringAfter(':', documentId)
		?.takeIf { it.isNotBlank() }
		?: uri.lastPathSegment
		?: this

	return FolderDisplayName(
		name = path.substringAfterLast('/'),
		parentPath = path.substringBeforeLast('/', "")
	)
}
