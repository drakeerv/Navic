/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.server

import androidx.media3.common.Player
import kotlinx.coroutines.flow.SharedFlow
import paige.navic.domain.model.DomainAlbum
import paige.navic.domain.model.DomainAlbumInfo
import paige.navic.domain.model.DomainAlbumListType
import paige.navic.domain.model.DomainArtist
import paige.navic.domain.model.DomainArtistInfo
import paige.navic.domain.model.DomainChatMessage
import paige.navic.domain.model.DomainGenre
import paige.navic.domain.model.DomainPlaybackState
import paige.navic.domain.model.DomainPlaylist
import paige.navic.domain.model.DomainRadio
import paige.navic.domain.model.DomainSearchResult
import paige.navic.domain.model.DomainShare
import paige.navic.domain.model.DomainSong
import paige.navic.domain.model.ServerLyrics
import kotlin.time.Instant

interface MusicServer : MusicProvider {
	suspend fun getAlbums(
		type: DomainAlbumListType,
		size: Int = 10,
		offset: Int = 0
	): List<DomainAlbum>

	suspend fun getAlbum(id: String): DomainAlbum

	suspend fun getSong(id: String): DomainSong

	suspend fun getArtists(): List<DomainArtist>

	suspend fun getArtistInfo(id: String): DomainArtistInfo

	suspend fun getAlbumInfo(id: String): DomainAlbumInfo

	suspend fun getGenres(): List<DomainGenre>

	suspend fun getPlaylists(): List<DomainPlaylist>

	suspend fun getPlaylist(id: String): DomainPlaylist

	suspend fun getSimilarSongs(id: String, count: Int = 50): List<DomainSong>

	suspend fun search(query: String): DomainSearchResult

	suspend fun getLyrics(songId: String): ServerLyrics?

	suspend fun star(id: String)

	suspend fun unstar(id: String)

	suspend fun setRating(id: String, rating: Int)

	suspend fun reportPlayback(
		id: String,
		positionMs: Long,
		state: DomainPlaybackState,
		isDeviceMuted: Boolean,
		deviceVolume: Int,
		@Player.RepeatMode repeatMode: Int,
		shuffleModeEnabled: Boolean,
		playbackRate: Float = 1f
	)

	suspend fun createPlaylist(name: String, songIds: List<String>): DomainPlaylist

	suspend fun updatePlaylist(
		id: String,
		name: String? = null,
		comment: String? = null,
		public: Boolean? = null,
		songIdsToAdd: List<String> = emptyList(),
		songIndicesToRemove: List<Int> = emptyList()
	)

	suspend fun deletePlaylist(id: String)

	fun parseResponseError(contentType: String?, body: String): ServerException?
}

interface ChatServer {
	suspend fun getChatMessages(): List<DomainChatMessage>

	suspend fun addChatMessage(message: String)
}

interface ShareServer {
	suspend fun getShares(): List<DomainShare>

	suspend fun createShare(
		ids: List<String>,
		description: String? = null,
		expiresAt: Instant? = null,
		downloadable: Boolean = false
	): DomainShare

	suspend fun deleteShare(id: String)
}

interface RadioServer {
	suspend fun getRadioStations(): List<DomainRadio>

	suspend fun createRadioStation(name: String, streamUrl: String, homepageUrl: String? = null)

	suspend fun updateRadioStation(
		id: String,
		name: String,
		streamUrl: String,
		homepageUrl: String? = null
	)

	suspend fun deleteRadioStation(id: String)
}

// this is probably very subject to change later
interface PlaylistCoverServer {
	suspend fun isImageAbsent(playlistId: String): Boolean

	suspend fun uploadPlaylistCover(id: String, bytes: ByteArray, contentType: String)

	suspend fun removePlaylistCover(id: String)
}

interface CommandFlowServer {
	val commands: SharedFlow<ServerCommand>
}
