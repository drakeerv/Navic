/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import paige.navic.R
import paige.navic.di.LocalNavStack
import paige.navic.di.LocalSnackBarState
import paige.navic.domain.manager.session.SessionManager
import paige.navic.domain.manager.session.SyncManager
import paige.navic.ui.component.layout.NestedTopBar
import paige.navic.ui.component.layout.NestedTopBarDefaults
import paige.navic.ui.component.local.LocalFolderSelection

@Composable
fun SettingsLocalLibraryScreen() {
	val sessionManager = koinInject<SessionManager>()
	val syncManager = koinInject<SyncManager>()
	val resources = LocalResources.current
	val snackBarState = LocalSnackBarState.current
	val backStack = LocalNavStack.current
	val scope = rememberCoroutineScope()

	val initialFolders = remember {
		sessionManager.accounts.value
			.firstOrNull { it.id == sessionManager.activeAccountId }
			?.localFolderUris
			.orEmpty()
	}
	var folders by remember { mutableStateOf(initialFolders) }

	Scaffold(
		topBar = {
			NestedTopBar(
				title = { Text(stringResource(R.string.title_music_folders)) },
				navigationAction = { NestedTopBarDefaults.NavigationAction() }
			)
		}
	) { innerPadding ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(innerPadding)
				.verticalScroll(rememberScrollState())
				.padding(horizontal = 16.dp, vertical = 16.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp)
		) {
			Text(
				text = stringResource(R.string.info_local_library_settings),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)

			LocalFolderSelection(
				modifier = Modifier.fillMaxWidth(),
				folderUris = folders,
				showError = folders.isEmpty(),
				onFoldersChanged = { folders = it },
				onPermissionError = {
					scope.launch {
						snackBarState.showSnackbar(
							resources.getString(R.string.info_local_folder_permission_denied)
						)
					}
				}
			)

			Button(
				modifier = Modifier.fillMaxWidth(),
				onClick = dropUnlessResumed {
					sessionManager.updateLocalFolders(folders)
					syncManager.triggerManualSync()
					scope.launch {
						snackBarState.showSnackbar(
							resources.getString(R.string.info_local_folders_saved)
						)
					}
					backStack.removeLastOrNull()
				},
				enabled = folders.isNotEmpty() && folders != initialFolders
			) {
				Text(stringResource(R.string.action_save_local_folders))
			}
		}
	}
}
