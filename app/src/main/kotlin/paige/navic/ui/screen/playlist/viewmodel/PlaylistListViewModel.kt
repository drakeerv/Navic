/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.playlist.viewmodel

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import paige.navic.preferences.PreferenceManager
import paige.navic.domain.manager.session.SessionManager
import paige.navic.domain.manager.session.SessionState
import paige.navic.domain.manager.ui.SnackBarManager
import paige.navic.domain.server.ServerType
import paige.navic.domain.server.local.NavicM3uPlaylistPersistence
import paige.navic.R
import paige.navic.domain.model.DomainFilter
import paige.navic.domain.model.DomainPlaylist
import paige.navic.domain.model.DomainPlaylistListType
import paige.navic.domain.model.toBitmask
import paige.navic.domain.model.toDomainFilters
import paige.navic.domain.repository.PlaylistRepository
import paige.navic.ui.viewmodel.SelectableViewModel


class PlaylistListViewModel(
	private val repository: PlaylistRepository,
	private val sessionManager: SessionManager,
	private val preferenceManager: PreferenceManager,
	private val m3uPlaylistPersistence: NavicM3uPlaylistPersistence,
	private val snackBarManager: SnackBarManager
) : SelectableViewModel<DomainPlaylist>(snackBarManager) {

	val selectedSorting: StateFlow<DomainPlaylistListType>
		field = MutableStateFlow(preferenceManager.playlistSortType)

	val selectedReversed: StateFlow<Boolean>
		field = MutableStateFlow(preferenceManager.playlistSortReversed)

	val selectedFilters: StateFlow<Set<DomainFilter>>
		field = MutableStateFlow(preferenceManager.playlistFilters.toDomainFilters())

	val gridState = LazyGridState()
	val canImportM3u: Boolean
		get() = (sessionManager.sessionState.value as? SessionState.Authenticated)
			?.serverInfo?.type == ServerType.LOCAL

	init {
		viewModelScope.launch {
			sessionManager.sessionState.collect { if (it is SessionState.Authenticated) refreshPlaylists(false) }
		}
	}

	fun refreshPlaylists(fullRefresh: Boolean) = launch {
		items = repository.getPlaylists(
			fullRefresh,
			selectedSorting.value,
			selectedReversed.value,
			selectedFilters.value
		).cachedIn(viewModelScope)
	}

	fun importM3u(uri: Uri) = launch {
		check(canImportM3u) { "M3U import is available for the local library" }
		check(m3uPlaylistPersistence.import(uri)) { "Could not read this M3U playlist" }
		snackBarManager.notify(R.string.notice_imported_m3u_playlist)
		refreshPlaylists(false)
	}

	fun setSorting(sorting: DomainPlaylistListType) {
		selectedSorting.value = sorting
		preferenceManager.playlistSortType = sorting
		refreshPlaylists(false)
	}

	fun setReversed(reversed: Boolean) {
		selectedReversed.value = reversed
		preferenceManager.playlistSortReversed = reversed
		refreshPlaylists(false)
	}

	fun toggleFilter(filter: DomainFilter) {
		val current = selectedFilters.value
		val newFilters = if (current.contains(filter)) {
			current - filter
		} else {
			current + filter
		}
		selectedFilters.value = newFilters
		preferenceManager.playlistFilters = newFilters.toBitmask()
		refreshPlaylists(false)
	}

}
