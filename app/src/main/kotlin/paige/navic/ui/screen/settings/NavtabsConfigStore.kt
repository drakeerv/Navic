/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.settings

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import paige.navic.domain.model.settings.NavbarConfig
import paige.navic.domain.model.settings.NavbarTab
import paige.navic.ui.core.UiState

class NavtabsConfigStore(
	private val settings: Settings
) {
	private val _state = MutableStateFlow(loadState())
	val state: StateFlow<UiState<NavbarConfig>> = _state

	private fun loadState(): UiState<NavbarConfig> = try {
		val raw = settings.getStringOrNull(NavbarConfig.KEY)
		val config = raw?.let { Json.decodeFromString<NavbarConfig>(it) }
			?.takeIf { it.version == NavbarConfig.VERSION } ?: NavbarConfig.default
		UiState.Success(config)
	} catch (e: Exception) {
		UiState.Error(e)
	}

	private fun setConfig(newConfig: NavbarConfig) {
		_state.value = UiState.Success(newConfig)
		settings[NavbarConfig.KEY] = Json.encodeToString(newConfig)
	}

	fun move(from: Int, to: Int) {
		val config = (_state.value as? UiState.Success)?.data ?: return
		setConfig(
			config.copy(
				tabs = config.tabs.toMutableList().apply {
					add(
						to, removeAt(from)
					)
				}
			)
		)
	}

	fun toggleVisibility(id: NavbarTab.Id) {
		val config = (_state.value as? UiState.Success)?.data ?: return
		setConfig(
			config.copy(
				tabs = config.tabs.map {
					if (it.id == id)
						it.copy(visible = !it.visible)
					else
						it
				}
			)
		)
	}
}
