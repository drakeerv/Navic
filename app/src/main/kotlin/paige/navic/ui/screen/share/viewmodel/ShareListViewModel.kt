/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.share.viewmodel

import androidx.compose.foundation.lazy.grid.LazyGridState
import paige.navic.domain.manager.session.SessionManager
import paige.navic.domain.manager.ui.SnackBarManager
import paige.navic.domain.model.DomainShare
import paige.navic.domain.repository.ShareRepository
import paige.navic.ui.viewmodel.SelectableListViewModel

class ShareListViewModel(
	private val repository: ShareRepository,
	private val sessionManager: SessionManager,
	snackBarManager: SnackBarManager
) : SelectableListViewModel<DomainShare>(snackBarManager) {
	val gridState = LazyGridState()

	init {
		launch {
			sessionManager.sessionState.collect {
				refreshShares()
			}
		}
	}

	fun refreshShares() {
		launch {
			items = repository.getShares()
		}
	}
}
