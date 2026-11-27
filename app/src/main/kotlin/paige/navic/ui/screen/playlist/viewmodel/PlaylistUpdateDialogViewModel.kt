/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.playlist.viewmodel

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import paige.navic.R
import paige.navic.database.dao.PlaylistDao
import paige.navic.database.entity.PlaylistSongCrossRef
import paige.navic.database.mapper.toDomainModel
import paige.navic.domain.manager.session.SessionManager
import paige.navic.domain.manager.ui.SnackBarManager
import paige.navic.domain.model.DomainPlaylist
import paige.navic.ui.core.UiState
import paige.navic.ui.viewmodel.AsyncViewModel

data class PlaylistUpdateViewState(
	val playlists: List<DomainPlaylist> = listOf(),
	val selectedPlaylists: Set<DomainPlaylist> = setOf()
)

class PlaylistUpdateDialogViewModel(
	private val songIds: List<String>,
	private val playlistToExclude: String?,
	private val playlistDao: PlaylistDao,
	private val sessionManager: SessionManager,
	private val snackBarManager: SnackBarManager
) : AsyncViewModel<PlaylistUpdateViewState>(
	initialState = UiState.Loading(PlaylistUpdateViewState()),
	snackBarManager = snackBarManager
) {
	private val _events = Channel<Event>()
	val events = _events.receiveAsFlow()

	init {
		refreshResults()
	}

	fun refreshResults() = execute {
		PlaylistUpdateViewState(
			playlists = playlistDao.getAllPlaylists()
				.map { it.toDomainModel() }
				.filter { it.id != playlistToExclude }
		)
	}

	fun togglePlaylistSelection(playlist: DomainPlaylist) = updateData {
		it.copy(
			playlists = it.playlists,
			selectedPlaylists = if (playlist in it.selectedPlaylists) {
				it.selectedPlaylists - playlist
			} else {
				it.selectedPlaylists + playlist
			}
		)
	}

	fun confirm() {
		val data = uiState.value.data ?: return
		val selected = data.selectedPlaylists
		val server = sessionManager.server
		execute {
			val selectedPlaylists = selected.mapNotNull { playlist ->
				playlistDao.getPlaylistById(playlist.id)
			}
			selectedPlaylists.forEach { playlistWithSongs ->
				val playlist = playlistWithSongs.playlist
				val nextPosition = (playlistWithSongs.songs.maxOfOrNull { it.crossRef.position } ?: -1) + 1
				val crossRefs = songIds.mapIndexed { index, songId ->
					PlaylistSongCrossRef(
						playlistId = playlist.playlistId,
						songId = songId,
						position = nextPosition + index
					)
				}
				playlistDao.appendPlaylistSongs(playlist.playlistId, crossRefs)
			}

			selected.forEach { playlist ->
				server?.updatePlaylist(
					playlist.id,
					songIdsToAdd = songIds
				)
			}
			if (selected.size == 1) {
				snackBarManager.notify(
					R.string.notice_added_to_playlist,
					selected.first().name.orEmpty()
				)
			} else if (selected.size > 1) {
				snackBarManager.notify(R.string.notice_added_to_multiple_playlists)
			}
			_events.send(Event.Dismiss)
			data
		}
	}

	enum class Event {
		Dismiss
	}
}
