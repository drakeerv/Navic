/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import paige.navic.R
import paige.navic.domain.manager.session.SessionManager
import paige.navic.domain.manager.session.connectivity.ReachabilityFlow
import paige.navic.domain.manager.session.supportsFeature
import paige.navic.domain.model.settings.StreamingQuality
import paige.navic.domain.model.settings.description
import paige.navic.domain.server.ServerFeature
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Info
import paige.navic.playback.exoplayer.impl.cache.StreamCacheManager
import paige.navic.preferences.PreferenceManager
import paige.navic.ui.component.common.SegmentedListItemDefaults
import paige.navic.ui.component.layout.NestedTopBar
import paige.navic.ui.screen.settings.component.SettingsGroup
import paige.navic.ui.screen.settings.component.SettingsGroupDefaults
import paige.navic.ui.screen.settings.component.SettingsRadioItem
import paige.navic.ui.screen.settings.component.SettingsToggleItem
import paige.navic.ui.util.label

@Composable
fun SettingsStreamingQualityScreen() {
	val preferenceManager = koinInject<PreferenceManager>()
	val sessionManager = koinInject<SessionManager>()
	val streamCacheManager = koinInject<StreamCacheManager>()
	val reachability = koinInject<ReachabilityFlow>()
	val isReachable by reachability.isReachable.collectAsStateWithLifecycle()
	val isMetered by reachability.isMetered.collectAsStateWithLifecycle()
	val supportsTranscodeOffset = remember {
		sessionManager.supportsFeature(ServerFeature.TRANSCODE_OFFSET)
	}

	Scaffold(
		topBar = { NestedTopBar({ Text(stringResource(R.string.title_streaming_quality)) }) }
	) { innerPadding ->
		CompositionLocalProvider(
			LocalMinimumInteractiveComponentSize provides 0.dp
		) {
			Column(
				modifier = Modifier
					.padding(innerPadding)
					.verticalScroll(rememberScrollState())
					.padding(horizontal = 16.dp)
			) {
				AnimatedVisibility(visible = !supportsTranscodeOffset) {
					Row(
						modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
						horizontalArrangement = Arrangement.spacedBy(16.dp)
					) {
						Icon(
							imageVector = Icons.Outlined.Info,
							contentDescription = null,
							tint = MaterialTheme.colorScheme.onSurfaceVariant
						)
						Text(
							text = stringResource(R.string.info_transcode_streaming_unsupported),
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							style = MaterialTheme.typography.bodyMedium
						)
					}
				}

				AnimatedVisibility(visible = !preferenceManager.isAdvancedTranscodingActive) {
					SettingsGroup(
						modifier = Modifier
							.selectableGroup()
							.padding(bottom = SettingsGroupDefaults.GapBetweenGroups),
						title = {
							Text(buildString {
								append(stringResource(R.string.title_wifi))
								if (isReachable && !isMetered) {
									append(' ' + stringResource(R.string.info_in_use))
								}
							})
						}
					) {
						RadioButtons(
							value = preferenceManager.streamingQualityWifi,
							onChangeValue = {
								preferenceManager.streamingQualityWifi = it
								streamCacheManager.clear()
							},
							enabled = supportsTranscodeOffset
						)
					}
				}

				AnimatedVisibility(visible = !preferenceManager.isAdvancedTranscodingActive) {
					SettingsGroup(
						modifier = Modifier
							.selectableGroup()
							.padding(bottom = SettingsGroupDefaults.GapBetweenGroups),
						title = {
							Text(buildString {
								append(stringResource(R.string.title_cellular))
								if (isReachable && isMetered) {
									append(' ' + stringResource(R.string.info_in_use))
								}
							})
						}
					) {
						RadioButtons(
							value = preferenceManager.streamingQualityCellular,
							onChangeValue = {
								preferenceManager.streamingQualityCellular = it
								streamCacheManager.clear()
							},
							enabled = supportsTranscodeOffset
						)
					}
				}

				SettingsGroup(
					modifier = Modifier.padding(bottom = SettingsGroupDefaults.GapBetweenGroups),
					title = { Text(stringResource(R.string.title_advanced)) }
				) {
					SettingsToggleItem(
						checked = preferenceManager.isAdvancedTranscodingActive,
						enabled = supportsTranscodeOffset,
						onCheckedChange = {
							preferenceManager.isAdvancedTranscodingActive = it
							streamCacheManager.clear()
						},
						content = { Text(stringResource(R.string.option_stream_custom_quality)) }
					)
				}

				AnimatedVisibility(visible = preferenceManager.isAdvancedTranscodingActive) {
					CustomOptions(
						enabled = supportsTranscodeOffset,
						onCustomFormatChanged = { customFormat ->
							preferenceManager.customFormat = customFormat
							streamCacheManager.clear()
						},
						onCustomMaxBitrateChanged = { customMaxBitrate ->
							preferenceManager.customMaxBitrate = customMaxBitrate
							streamCacheManager.clear()
						}
					)
				}

				Row(
					modifier = Modifier.padding(horizontal = 8.dp),
					horizontalArrangement = Arrangement.spacedBy(16.dp)
				) {
					Icon(
						imageVector = Icons.Outlined.Info,
						contentDescription = null,
						tint = MaterialTheme.colorScheme.onSurfaceVariant
					)
					Text(
						text = stringResource(R.string.info_streaming_quality),
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						style = MaterialTheme.typography.bodyMedium
					)
				}
			}
		}
	}
}

@Composable
private fun RadioButtons(
	value: StreamingQuality,
	onChangeValue: (StreamingQuality) -> Unit,
	enabled: Boolean
) {
	StreamingQuality.entries.forEachIndexed { index, quality ->
		val selected = value == quality

		SettingsRadioItem(
			selected = selected,
			onClick = { onChangeValue(quality) },
			enabled = enabled,
			content = { Text(quality.label()) },
			supportingContent = {
				quality.description()?.let { description ->
					AnimatedVisibility(visible = selected) {
						Text(description)
					}
				}
			},
			shapes = SegmentedListItemDefaults.segmentedShapes(
				index = index,
				count = StreamingQuality.entries.count()
			)
		)
	}
}

@Composable
private fun CustomOptions(
	enabled: Boolean,
	onCustomFormatChanged: (String) -> Unit,
	onCustomMaxBitrateChanged: (Int) -> Unit
) {
	val preferenceManager = koinInject<PreferenceManager>()
	var bitrateInput by remember {
		val current = preferenceManager.customMaxBitrate
		mutableStateOf(if (current > 0) current.toString() else "")
	}

	Column(
		modifier = Modifier.padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(8.dp)
	) {
		OutlinedTextField(
			value = bitrateInput,
			onValueChange = { newValue ->
				if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
					bitrateInput = newValue
					onCustomMaxBitrateChanged(newValue.toIntOrNull() ?: 0)
				}
			},
			label = { Text(stringResource(R.string.option_max_bitrate)) },
			placeholder = { Text("0") },
			keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
			modifier = Modifier.fillMaxWidth(),
			enabled = enabled,
			singleLine = true
		)

		OutlinedTextField(
			value = preferenceManager.customFormat,
			onValueChange = onCustomFormatChanged,
			label = { Text(stringResource(R.string.option_stream_format)) },
			supportingText = { Text(stringResource(R.string.info_transcoding_defaults)) },
			modifier = Modifier.fillMaxWidth(),
			enabled = enabled,
			singleLine = true
		)
	}
}
