/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.login.page

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import paige.navic.R
import paige.navic.designsystem.theme.defaultFont
import paige.navic.di.LocalNavStack
import paige.navic.di.LocalSnackBarState
import paige.navic.domain.manager.runtime.PermissionManager
import paige.navic.domain.manager.session.LoginManager
import paige.navic.domain.server.ServerType
import paige.navic.icons.Icons
import paige.navic.icons.outlined.ArrowBack
import paige.navic.icons.outlined.Error
import paige.navic.ui.component.common.ErrorBox
import paige.navic.ui.component.common.SegmentedListButton
import paige.navic.ui.component.dialog.FormDialog
import paige.navic.ui.component.layout.TopBarButton
import paige.navic.ui.component.local.LocalFolderSelection
import paige.navic.ui.core.LoginEvent
import paige.navic.ui.core.LoginField
import paige.navic.ui.core.UiState
import paige.navic.ui.navigation.Screen.Library
import paige.navic.ui.screen.settings.component.SettingsGroup
import paige.navic.ui.screen.settings.component.SettingsToggleItem

@Composable
fun LocalLibraryPage(
	modifier: Modifier = Modifier,
	isCurrent: Boolean,
	onNavigateBack: () -> Unit
) {
	val loginManager = koinInject<LoginManager>()
	val permissionManager = koinInject<PermissionManager>()
	val backStack = LocalNavStack.current
	val snackBarState = LocalSnackBarState.current
	val resources = LocalResources.current
	val localFolderUris = loginManager.localFolderUris
	val syncM3uPlaylists = loginManager.syncM3uPlaylists
	var errorState by remember { mutableStateOf<Exception?>(null) }
	var isBusy by remember { mutableStateOf(false) }
	var notificationsDenied by remember { mutableStateOf(false) }
	val scope = rememberCoroutineScope()

	LaunchedEffect(isCurrent) {
		if (isCurrent) loginManager.selectServerType(ServerType.LOCAL)
	}

	LaunchedEffect(Unit) {
		loginManager.events.collect { event ->
			isBusy = event is LoginEvent.Loading
			when (event) {
				LoginEvent.Success -> {
					backStack.clear()
					backStack.add(Library())
				}

				LoginEvent.InvalidCredentials -> Unit
				is LoginEvent.Error -> errorState = event.error
				is LoginEvent.ValidationFailed -> if (event.field == LoginField.LocalFolders) {
					scope.launch {
						snackBarState.showSnackbar(resources.getString(R.string.info_local_folder_permission_denied))
					}
				}

				else -> Unit
			}
		}
	}

	val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
		snapAnimationSpec = spring(
			dampingRatio = Spring.DampingRatioNoBouncy,
			stiffness = 200f
		)
	)

	Scaffold(
		modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
		topBar = {
			LargeFlexibleTopAppBar(
				title = {
					Text(
						text = stringResource(R.string.subtitle_local_library),
						style = MaterialTheme.typography.headlineMedium,
						fontFamily = defaultFont(round = 100f)
					)
				},
				navigationIcon = {
					TopBarButton(
						modifier = Modifier.padding(horizontal = 12.dp),
						onClick = onNavigateBack
					) {
						Icon(
							Icons.Outlined.ArrowBack,
							stringResource(R.string.action_navigate_back)
						)
					}
				},
				scrollBehavior = scrollBehavior
			)
		}
	) { innerPadding ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(innerPadding)
				.padding(horizontal = 16.dp),
		) {
			Text(
				text = stringResource(R.string.info_local_library_onboarding),
				modifier = Modifier.padding(PaddingValues(top = 4.dp)),
				style = MaterialTheme.typography.bodyMedium
			)

			Column(
				modifier = Modifier
					.weight(1f)
					.verticalScroll(rememberScrollState()),
				verticalArrangement = Arrangement.spacedBy(16.dp)
			) {
				Spacer(Modifier.padding(4.dp))

				errorState?.let { error ->
					ErrorBox(
						UiState.Error(error, null),
						padding = PaddingValues(0.dp),
						modifier = Modifier.fillMaxWidth()
					)
				}

				SettingsGroup(
					title = { Text(stringResource(R.string.title_local_scan_settings)) }
				) {
					SettingsToggleItem(
						checked = syncM3uPlaylists,
						onCheckedChange = loginManager::updateSyncM3uPlaylists,
						enabled = !isBusy,
						supportingContent = { Text(stringResource(R.string.info_sync_m3u_playlists)) }
					) {
						Text(stringResource(R.string.option_sync_m3u_playlists))
					}
				}

				SettingsGroup(
					title = { Text(stringResource(R.string.title_music_folders)) }
				) {
					LocalFolderSelection(
						modifier = Modifier.fillMaxWidth(),
						folderUris = localFolderUris,
						enabled = !isBusy,
						onFoldersChanged = loginManager::setLocalFolders,
						onPermissionError = {
							scope.launch {
								snackBarState.showSnackbar(
									resources.getString(R.string.info_local_folder_permission_denied)
								)
							}
						}
					)
					Spacer(Modifier.padding(12.dp))
				}
			}

			Button(
				modifier = Modifier.fillMaxWidth(),
				onClick = {
					scope.launch {
						if (!permissionManager.requestNotificationsPermission()) {
							notificationsDenied = true
						} else {
							loginManager.login()
						}
					}
				},
				enabled = !isBusy && localFolderUris.isNotEmpty(),
				shape = CircleShape
			) {
				Text(
					stringResource(
						if (loginManager.hasLocalAccount) R.string.action_save_local_folders
						else R.string.action_create_local_library
					),
					fontFamily = defaultFont(100)
				)
			}
		}
	}

	if (notificationsDenied) {
		FormDialog(
			onDismissRequest = { notificationsDenied = false },
			icon = { Icon(Icons.Outlined.Error, null) },
			title = { Text(stringResource(R.string.notice_notifications_denied)) },
			content = { Text(stringResource(R.string.subtitle_notifications_denied)) },
			buttons = {
				SegmentedListButton(
					modifier = Modifier.fillMaxWidth(),
					onClick = {
						notificationsDenied = false
						permissionManager.openPermissionsSettings()
					}
				) { Text(stringResource(R.string.action_open_settings)) }
			}
		)
	}
}
