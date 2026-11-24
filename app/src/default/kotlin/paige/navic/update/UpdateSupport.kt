/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.compose.koinInject
import paige.navic.BuildConfig
import paige.navic.R
import paige.navic.designsystem.theme.defaultFont
import paige.navic.preferences.PreferenceManager
import paige.navic.ui.component.common.Markdown
import paige.navic.ui.component.dialog.LinkConfirmationDialog
import paige.navic.ui.component.sheet.ModalBottomSheet
import paige.navic.ui.screen.settings.component.SettingsGroup
import paige.navic.ui.screen.settings.component.SettingsToggleItem
import paige.navic.util.Logger

private const val CODEBERG_URL = "https://codeberg.org/paige/Navic"
private const val CODEBERG_API = "https://codeberg.org/api/v1/repos/paige/navic"
private const val CI_ARTIFACT_NAME = "navic-release-android"
private const val RELEASE_APK_NAME = "Navic.apk"

@Serializable
data class UpdateRelease(
	@SerialName("tag_name") val tag: String,
	@SerialName("html_url") val url: String,
	@SerialName("body") val body: String,
	val assets: List<UpdateAsset> = emptyList()
)

@Serializable
data class UpdateAsset(
	val name: String,
	@SerialName("browser_download_url") val url: String
)

private fun UpdateRelease.isInstallable(): Boolean {
	val path = url.substringBefore('?')
	return path.endsWith(".apk", ignoreCase = true) || path.endsWith("/zip", ignoreCase = true)
}

@Serializable
private data class CiRuns(
	@SerialName("workflow_runs") val runs: List<CiRun> = emptyList()
)

@Serializable
private data class CiRun(
	val id: Long,
	@SerialName("commit_sha") val sha: String,
	val title: String = ""
)

@Serializable
private data class CiArtifact(
	val name: String,
	@SerialName("archive_download_url") val url: String,
	val expired: Boolean = false
)

class ChangelogViewModel(
	private val context: Context
) : ViewModel() {
	val release: StateFlow<UpdateRelease?>
		field = MutableStateFlow(null)

	private val _installing = MutableStateFlow(false)
	val installing: StateFlow<Boolean> = _installing.asStateFlow()

	private val updateClient = HttpClient {
		install(ContentNegotiation) {
			json(Json { ignoreUnknownKeys = true })
		}
	}

	init {
		checkForUpdates(
			context.packageManager
				.getPackageInfo(context.packageName, 0)
				.versionName.toString()
		)
	}

	fun checkForUpdates(currentVersion: String) {
		if (BuildConfig.CI_BUILD) {
			checkCiUpdates()
		} else {
			checkReleaseUpdates(currentVersion)
		}
	}

	private fun checkReleaseUpdates(currentVersion: String) {
		viewModelScope.launch {
			release.value = try {
				val update: UpdateRelease =
					updateClient.get("$CODEBERG_API/releases/latest").body()
				val remoteVersion = update.tag
					.filter { it.isDigit() }
					.toIntOrNull() ?: return@launch
				val localVersion = currentVersion
					.filter { it.isDigit() }
					.toIntOrNull() ?: return@launch
				if (remoteVersion > localVersion || "$remoteVersion".length != "$localVersion".length)
					update.copy(
						url = update.assets
							.firstOrNull { it.name == RELEASE_APK_NAME }
							?.url ?: update.url
					)
				else null
			} catch (e: Exception) {
				Logger.e("ChangelogViewModel", "couldn't check for updates", e)
				null
			}
		}
	}

	private fun checkCiUpdates() {
		viewModelScope.launch {
			release.value = try {
				val branch = BuildConfig.GIT_BRANCH_NAME
				if (branch.isBlank() || branch == "unknown") {
					null
				} else {
					val runs: CiRuns = updateClient.get("$CODEBERG_API/actions/runs") {
						parameter("ref", "refs/heads/$branch")
						parameter("event", "push")
						parameter("status", "success")
						parameter("limit", 1)
						parameter("page", 1)
					}.body()
					val run = runs.runs.firstOrNull()
					if (run == null || run.sha.equals(BuildConfig.GIT_COMMIT_HASH, true)) {
						null
					} else {
						val artifacts: List<CiArtifact> =
							updateClient.get("$CODEBERG_API/actions/runs/${run.id}/artifacts").body()
						val artifact = artifacts.firstOrNull {
							it.name == CI_ARTIFACT_NAME && !it.expired
						}
						artifact?.let {
							UpdateRelease(
								tag = run.sha.take(7),
								url = it.url,
								body = "**${run.title}**\n\n[`${run.sha.take(7)}`]($CODEBERG_URL/commit/${run.sha})"
							)
						}
					}
				}
			} catch (e: Exception) {
				Logger.e("ChangelogViewModel", "couldn't check for updates", e)
				null
			}
		}
	}

	fun installUpdate() {
		val update = release.value ?: return
		if (_installing.value) return
		viewModelScope.launch {
			// user has to toggle "install unknown apps"; they tap update again afterwards
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
				!context.packageManager.canRequestPackageInstalls()
			) {
				context.startActivity(
					Intent(
						Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
						Uri.parse("package:${context.packageName}")
					).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
				)
				return@launch
			}
			_installing.value = true
			try {
				withContext(Dispatchers.IO) {
					val apk = UpdateInstaller.download(context, update.url)
					try {
						UpdateInstaller.install(context, apk)
					} finally {
						apk.delete()
					}
				}
			} catch (e: Exception) {
				Logger.e("ChangelogViewModel", "couldn't install update", e)
				Toast.makeText(
					context,
					R.string.info_update_failed,
					Toast.LENGTH_SHORT
				).show()
			} finally {
				_installing.value = false
			}
		}
	}

	fun clearRelease() {
		release.value = null
	}
}

object UpdateSupport {
	@Composable
	fun Sheet() {
		UpdateSheet()
	}

	@Composable
	fun SettingsItem() {
		val preferenceManager = koinInject<PreferenceManager>()

		SettingsGroup {
			SettingsToggleItem(
				content = { Text(stringResource(R.string.option_check_for_updates)) },
				supportingContent = { Text(stringResource(R.string.subtitle_check_for_updates)) },
				checked = preferenceManager.checkForUpdates,
				onCheckedChange = { preferenceManager.checkForUpdates = it }
			)
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdateSheet() {
	val context = LocalContext.current
	val preferenceManager = koinInject<PreferenceManager>()
	val viewModel: ChangelogViewModel = viewModel {
		ChangelogViewModel(context.applicationContext)
	}
	val release by viewModel.release.collectAsStateWithLifecycle()
	val installing by viewModel.installing.collectAsStateWithLifecycle()
	var linkToOpen by rememberSaveable { mutableStateOf<String?>(null) }

	release?.let { release ->
		ModalBottomSheet(
			onDismissRequest = { viewModel.clearRelease() },
			sheetState = rememberBottomSheetState(
				initialValue = SheetValue.Hidden,
				enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
			)
		) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.verticalScroll(rememberScrollState())
					.padding(horizontal = 16.dp)
			) {
				Column(
					modifier = Modifier.fillMaxWidth(),
					horizontalAlignment = Alignment.CenterHorizontally
				) {
					Text(
						text = stringResource(R.string.title_update),
						style = MaterialTheme.typography.titleLarge,
						fontFamily = defaultFont(round = 100f)
					)
					Text(
						text = if (BuildConfig.CI_BUILD) {
							stringResource(
								R.string.info_update_ci,
								BuildConfig.GIT_BRANCH_NAME,
								release.tag
							)
						} else {
							stringResource(R.string.info_update, release.tag)
						},
						style = MaterialTheme.typography.bodyMedium
					)
				}

				Spacer(Modifier.height(8.dp))

				Markdown(
					text = release.body,
					modifier = Modifier
						.heightIn(max = 400.dp)
						.fillMaxWidth()
						.clip(MaterialTheme.shapes.large)
						.background(MaterialTheme.colorScheme.surfaceContainerHigh)
						.verticalScroll(rememberScrollState())
						.padding(10.dp)
				)

				Spacer(Modifier.height(8.dp))
				Spacer(Modifier.weight(1f))

				Button(
					onClick = {
						if (release.isInstallable()) {
							viewModel.installUpdate()
						} else {
							linkToOpen = release.url
						}
					},
					modifier = Modifier.fillMaxWidth(),
					shape = CircleShape,
					enabled = !installing
				) {
					Text(
						text = if (installing) {
							stringResource(R.string.action_downloading)
						} else {
							stringResource(R.string.action_update_app)
						},
						fontFamily = defaultFont(100)
					)
				}

				OutlinedButton(
					onClick = {
						viewModel.clearRelease()
						preferenceManager.checkForUpdates = false
					},
					modifier = Modifier.fillMaxWidth(),
					shape = CircleShape
				) {
					Text(
						text = stringResource(R.string.action_dont_show_again),
						fontFamily = defaultFont(100)
					)
				}
			}
		}
	}

	LinkConfirmationDialog(
		linkToOpen = linkToOpen,
		onDismissRequest = { linkToOpen = null }
	)
}
