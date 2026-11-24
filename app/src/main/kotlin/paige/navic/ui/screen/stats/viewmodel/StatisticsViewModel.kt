/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.stats.viewmodel

import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import paige.navic.database.dao.AlbumDao
import paige.navic.database.dao.ArtistDao
import paige.navic.database.dao.SongDao
import paige.navic.database.mapper.toDomainModel
import paige.navic.domain.model.DomainAlbum
import paige.navic.domain.model.DomainArtist
import paige.navic.domain.model.DomainSong
import paige.navic.domain.model.settings.StatsCalculationMode
import paige.navic.preferences.PreferenceManager
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

data class ArtistStats(
	val artist: DomainArtist,
	val playCount: Int,
	val listeningTime: Duration
)

data class AlbumStats(
	val album: DomainAlbum,
	val playCount: Int,
	val listeningTime: Duration
)

data class SongStats(
	val song: DomainSong,
	val playCount: Int,
	val listeningTime: Duration
)

data class StatisticsState(
	val totalPlays: Int = 0,
	val totalDuration: Duration = Duration.ZERO,
	val topSongs: List<SongStats> = emptyList(),
	val topAlbums: List<AlbumStats> = emptyList(),
	val topArtists: List<ArtistStats> = emptyList(),
	val isLoading: Boolean = true
)

class StatisticsViewModel(
	private val songDao: SongDao,
	private val artistDao: ArtistDao,
	private val albumDao: AlbumDao,
	private val preferenceManager: PreferenceManager
) : ViewModel() {

	val starredSongs: StateFlow<List<DomainSong>> = songDao
		.getStarredSongs()
		.map { list -> list.map { song -> song.toDomainModel() } }
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

	@OptIn(ExperimentalCoroutinesApi::class)
	val state: StateFlow<StatisticsState> = snapshotFlow { preferenceManager.statsCalculationMode }
		.flatMapLatest { mode ->
			val topSongsFlow = if (mode == StatsCalculationMode.Duration) {
				songDao.getTopSongs(10)
			} else {
				songDao.getTopSongsByPlayCount(10)
			}

			val topAlbumsFlow = if (mode == StatsCalculationMode.Duration) {
				songDao.getTopAlbumIds(10)
			} else {
				songDao.getTopAlbumIdsByPlayCount(10)
			}

			val topArtistsFlow = if (mode == StatsCalculationMode.Duration) {
				songDao.getTopArtistIds(10)
			} else {
				songDao.getTopArtistIdsByPlayCount(10)
			}

			combine(
				songDao.getTotalPlayCount().map { it ?: 0 },
				songDao.getTotalListeningTime().map { (it ?: 0L).milliseconds },
				topSongsFlow.map { songs ->
					songs.map { song ->
						SongStats(
							song = song.toDomainModel(),
							playCount = song.playCount,
							listeningTime = song.duration * song.playCount
						)
					}
				},
				topAlbumsFlow.flatMapLatest { ids ->
					if (ids.isEmpty()) flowOf(emptyList())
					else flow {
						val albums = mutableListOf<AlbumStats>()
						for (id in ids) {
							val entity = albumDao.getAlbumById(id)
							val playCount = songDao.getSongsByAlbumId(id).sumOf { it.playCount }
							val listeningTime = (songDao.getAlbumListeningTime(id).first() ?: 0L).milliseconds
							entity?.toDomainModel()?.let { albums.add(AlbumStats(it, playCount, listeningTime)) }
						}
						emit(albums)
					}
				},
				topArtistsFlow.flatMapLatest { ids ->
					if (ids.isEmpty()) flowOf(emptyList())
					else flow {
						val artists = mutableListOf<ArtistStats>()
						for (id in ids) {
							val entity = artistDao.getArtistById(id)
							val playCount = songDao.getArtistPlayCount(id).first() ?: 0
							val listeningTime = (songDao.getArtistListeningTime(id).first() ?: 0L).milliseconds
							entity?.toDomainModel()?.let { artists.add(ArtistStats(it, playCount, listeningTime)) }
						}
						emit(artists)
					}
				}
			) { total, duration, songs, albums, artists ->
				StatisticsState(
					totalPlays = total,
					totalDuration = duration,
					topSongs = songs,
					topAlbums = albums,
					topArtists = artists,
					isLoading = false
				)
			}
		}
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatisticsState())
}
