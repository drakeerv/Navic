/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import paige.navic.R
import paige.navic.preferences.AccountSettings
import paige.navic.domain.manager.session.SessionManager
import paige.navic.domain.model.settings.ServerAddress
import paige.navic.domain.model.settings.ServerScheme
import paige.navic.ui.component.common.ContentUnavailable
import paige.navic.ui.component.dialog.AlertDialog
import paige.navic.ui.component.layout.NestedTopBar
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Add
import paige.navic.icons.outlined.Delete
import paige.navic.icons.outlined.Edit
import paige.navic.ui.screen.settings.component.SettingsGroup
import paige.navic.ui.screen.settings.component.SettingsGroupDefaults
import paige.navic.ui.screen.settings.component.SettingsToggleItem
import paige.navic.util.network.NavicAddressHelper
import kotlin.time.Duration.Companion.milliseconds


@Composable
fun SettingsAlternativeAddressesScreen() {
	val sessionManager = koinInject<SessionManager>()
	val accountSettings = koinInject<AccountSettings>()
	val dnsResolver = koinInject<NavicAddressHelper>()

	val alternativeAddresses = remember {
		accountSettings.alternateServerAddresses.toMutableStateList()
	}

	val update = {
		accountSettings.alternateServerAddresses = alternativeAddresses
			.filter { it.host.isNotBlank() }
		dnsResolver.invalidate()
		sessionManager.refreshClient()
	}

	var allowInsecure by remember {
		mutableStateOf(accountSettings.allowInsecureConnections)
	}

	Scaffold(
		topBar = { NestedTopBar({ Text(stringResource(R.string.option_alternate_addresses)) }) },
		floatingActionButton = {
			FloatingActionButton(
				onClick = {
					alternativeAddresses.add(ServerAddress(ServerScheme.HTTPS, ""))
					update()
				},
				shape = MaterialTheme.shapes.medium
			) {
				Icon(
					imageVector = Icons.Outlined.Add,
					contentDescription = stringResource(R.string.action_new)
				)
			}
		}
	) { innerPadding ->
		CompositionLocalProvider(
			LocalMinimumInteractiveComponentSize provides 0.dp
		) {
			Column(
				modifier = Modifier
					.padding(innerPadding)
					.verticalScroll(rememberScrollState())
					.padding(horizontal = 16.dp),
				verticalArrangement = Arrangement.spacedBy(SettingsGroupDefaults.GapBetweenGroups)
			) {
				SettingsGroup {
					SettingsToggleItem(
						checked = allowInsecure,
						onCheckedChange = { checked ->
							allowInsecure = checked
							accountSettings.allowInsecureConnections = checked
							sessionManager.refreshClient()
						},
						content = { Text(stringResource(R.string.alt_addresses_allow_insecure)) },
						supportingContent = {
							Text(stringResource(R.string.alt_addresses_allow_insecure_description))
						}
					)
				}
				if (alternativeAddresses.isNotEmpty()) {
					SettingsGroup(
						verticalArrangement = Arrangement.spacedBy(8.dp)
					) {
						HorizontalDivider()
						alternativeAddresses.forEachIndexed { index, address ->
							AddressRow(
								address = address,
								onValueChanged = { newValue ->
									alternativeAddresses[index] = newValue
									update()
								},
								onDelete = {
									alternativeAddresses.removeAt(index)
									update()
								}
							)
							HorizontalDivider()
						}
					}
				} else {
					ContentUnavailable(
						modifier = Modifier
							.fillMaxWidth()
							.padding(vertical = 32.dp)
					)
				}
			}
		}
	}
}

@Composable
private fun AddressRow(
	address: ServerAddress,
	onValueChanged: (ServerAddress) -> Unit,
	onDelete: () -> Unit
) {
	var isEditing by remember { mutableStateOf(false) }

	Surface {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(vertical = 4.dp),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			Text(
				text = address.displayName(),
				modifier = Modifier.weight(1f)
			)
			IconButton(
				onClick = { isEditing = true }
			) {
				Icon(
					imageVector = Icons.Outlined.Edit,
					contentDescription = stringResource(R.string.action_edit)
				)
			}
			IconButton(
				onClick = onDelete
			) {
				Icon(
					imageVector = Icons.Outlined.Delete,
					contentDescription = stringResource(R.string.action_delete),
					tint = MaterialTheme.colorScheme.error
				)
			}
		}
	}

	if (isEditing) {
		AddressDialog(
			address = address,
			onValueChanged = onValueChanged,
			onDismiss = { isEditing = false }
		)
	}
}

@Composable
private fun AddressDialog(
	address: ServerAddress,
	onValueChanged: (ServerAddress) -> Unit,
	onDismiss: () -> Unit
) {
	var hostField by remember { mutableStateOf(address.host) }
	var portField by remember { mutableStateOf(address.configuredPortText()) }
	var basePathField by remember { mutableStateOf(address.basePath) }

	val port = portField.toIntOrNull()
	val portIsValid = portField.isBlank() || (port != null && port in 1..65535)
	val current = ServerAddress(
		scheme = address.scheme,
		host = hostField,
		configuredPort = if (portIsValid) port else address.configuredPort(),
		basePath = basePathField
	)

	LaunchedEffect(current, portIsValid) {
		if (portIsValid && current != address) {
			delay(500.milliseconds)
			onValueChanged(current)
		}
	}

	val commitAndDismiss = {
		if (portIsValid && current != address) {
			onValueChanged(current)
		}
		onDismiss()
	}

	AlertDialog(
		onDismissRequest = commitAndDismiss,
		title = { Text(stringResource(R.string.alt_addresses_edit_address)) },
		text = {
			Column(
				verticalArrangement = Arrangement.spacedBy(8.dp)
			) {
				Text(stringResource(R.string.alt_addresses_dialog_hint))
				SingleChoiceSegmentedButtonRow(
					modifier = Modifier.fillMaxWidth()
				) {
					ServerScheme.entries.forEachIndexed { index, scheme ->
						SegmentedButton(
							shape = SegmentedButtonDefaults.itemShape(
								index = index,
								count = ServerScheme.entries.size
							),
							onClick = {
								onValueChanged(current.copy(scheme = scheme))
							},
							selected = address.scheme == scheme,
							label = { Text(scheme.name) }
						)
					}
				}
				OutlinedTextField(
					value = hostField,
					onValueChange = { hostField = it },
					label = { Text(stringResource(R.string.alt_addresses_host)) },
					placeholder = { Text(stringResource(R.string.alt_addresses_text_field_placeholder)) },
					singleLine = true,
					keyboardOptions = KeyboardOptions(
						autoCorrectEnabled = false,
						keyboardType = KeyboardType.Uri
					),
					modifier = Modifier.fillMaxWidth()
				)
				OutlinedTextField(
					value = portField,
					onValueChange = { value ->
						if (value.all(Char::isDigit)) {
							portField = value
						}
					},
					isError = !portIsValid,
					label = { Text(stringResource(R.string.alt_addresses_port)) },
					placeholder = { Text(address.scheme.getKnownPort().toString()) },
					singleLine = true,
					keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
					modifier = Modifier.fillMaxWidth()
				)
				OutlinedTextField(
					value = basePathField ?: "",
					onValueChange = { basePathField = it },
					label = { Text(stringResource(R.string.alt_addresses_base_path)) },
					singleLine = true,
					modifier = Modifier.fillMaxWidth()
				)
			}
		},
		confirmButton = {
			TextButton(
				onClick = commitAndDismiss
			) {
				Text(stringResource(R.string.action_ok))
			}
		}
	)
}

private fun ServerAddress.configuredPort(): Int? =
	port.takeIf { it != scheme.getKnownPort() }

private fun ServerAddress.configuredPortText(): String =
	configuredPort()?.toString().orEmpty()

private fun ServerAddress.displayName(): String {
	if (host.isBlank()) return "(empty)"
	return buildString {
		append(scheme.name.lowercase())
		append("://")
		append(host)
		configuredPort()?.let {
			append(':')
			append(it)
		}
	}
}
