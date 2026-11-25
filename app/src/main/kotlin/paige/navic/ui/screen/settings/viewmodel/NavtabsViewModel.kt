/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.settings.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import paige.navic.domain.model.settings.NavbarConfig
import paige.navic.domain.model.settings.NavbarTab
import paige.navic.ui.core.UiState
import paige.navic.ui.screen.settings.NavtabsConfigStore

class NavtabsViewModel(
	private val configStore: NavtabsConfigStore
) : ViewModel() {
	val state: StateFlow<UiState<NavbarConfig>> = configStore.state

	fun move(from: Int, to: Int) {
		configStore.move(from, to)
	}

	fun toggleVisibility(id: NavbarTab.Id) {
		configStore.toggleVisibility(id)
	}
}
