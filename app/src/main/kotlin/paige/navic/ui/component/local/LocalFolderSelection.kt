/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.component.local

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import paige.navic.R
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Delete
import paige.navic.icons.outlined.Folder
import paige.navic.ui.component.common.SegmentedListItem
import paige.navic.ui.component.common.SegmentedListItemDefaults

@Composable
fun LocalFolderSelection(
	modifier: Modifier = Modifier,
	folderUris: List<String>,
	enabled: Boolean = true,
	onFoldersChanged: (List<String>) -> Unit,
	onPermissionError: () -> Unit
) {
	val context = LocalContext.current
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
		modifier = modifier,
		verticalArrangement = Arrangement.spacedBy(8.dp)
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.animateContentSize(),
			verticalArrangement = Arrangement.spacedBy(SegmentedListItemDefaults.segmentedGap())
		) {
			folderUris.forEachIndexed { index, uri ->
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
					content = { Text(uri.toFolderName()) }
				)
			}
		}

		Button(
			modifier = Modifier.fillMaxWidth(),
			onClick = { picker.launch(null) },
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
	}
}

private fun String.toFolderName(): String {
	val uri = Uri.parse(this)
	val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
	return documentId
		?.substringAfterLast(':')
		?.ifBlank { documentId }
		?: uri.lastPathSegment
		?: this
}
