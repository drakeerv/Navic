/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.server.jellyfin

import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.instantMixApi
import org.jellyfin.sdk.api.client.extensions.itemUpdateApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.lyricApi
import org.jellyfin.sdk.api.client.extensions.playlistApi
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.client.extensions.userDataApi
import org.jellyfin.sdk.api.sockets.subscribe
import org.jellyfin.sdk.api.sockets.subscribeGeneralCommands
import org.jellyfin.sdk.model.FileInfo
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ClientCapabilitiesDto
import org.jellyfin.sdk.model.api.CreatePlaylistDto
import org.jellyfin.sdk.model.api.GeneralCommandType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.PlaystateCommand
import org.jellyfin.sdk.model.api.PlaystateMessage
import org.jellyfin.sdk.model.api.RepeatMode
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.extensions.getValue
import org.jellyfin.sdk.model.extensions.ticks
import paige.navic.domain.model.DomainAlbum
import paige.navic.domain.model.DomainAlbumInfo
import paige.navic.domain.model.DomainAlbumListType
import paige.navic.domain.model.DomainArtist
import paige.navic.domain.model.DomainArtistInfo
import paige.navic.domain.model.DomainGenre
import paige.navic.domain.model.DomainPlaybackState
import paige.navic.domain.model.DomainPlaylist
import paige.navic.domain.model.DomainSearchResult
import paige.navic.domain.model.DomainSong
import paige.navic.domain.model.DomainUser
import paige.navic.domain.model.ServerLyricLine
import paige.navic.domain.model.ServerLyrics
import paige.navic.domain.server.CommandFlowServer
import paige.navic.domain.server.MusicServer
import paige.navic.domain.server.PlaylistCoverServer
import paige.navic.domain.server.ServerCommand
import paige.navic.domain.server.ServerException
import paige.navic.domain.server.ServerInfo
import paige.navic.domain.server.ServerStatus
import paige.navic.domain.server.jellyfin.mapper.toDomainAlbum
import paige.navic.domain.server.jellyfin.mapper.toDomainArtist
import paige.navic.domain.server.jellyfin.mapper.toDomainPlaylist
import paige.navic.domain.server.jellyfin.mapper.toDomainSong
import paige.navic.util.Logger
import java.util.UUID
import kotlin.io.encoding.Base64
import kotlin.time.Instant

class JellyfinMusicServer(
	private val api: ApiClient,
	private val baseUrl: String,
	private val userId: UUID,
	private val accessToken: String
) : MusicServer, PlaylistCoverServer, CommandFlowServer {
	private val commandScope = CoroutineScope(Dispatchers.Main)
	private val _commands = MutableSharedFlow<ServerCommand>()
	override val commands = _commands.asSharedFlow()

	init {
		commandScope.launch {
			subscribeToSocket(this)
		}
		commandScope.launch {
			postCapabilities()
		}
	}

	private fun subscribeToSocket(scope: CoroutineScope) = api.webSocket.apply {
		state.onEach { state ->
			Logger.i("JellyfinMusicServer", "socket state change: $state")
		}.launchIn(scope)
		subscribe<PlaystateMessage>()
			.onEach { message ->
				Logger.i("JellyfinMusicServer", "$message")
				val command = when (message.data?.command) {
					PlaystateCommand.STOP -> ServerCommand.PlaybackCommand.Stop
					PlaystateCommand.PAUSE -> ServerCommand.PlaybackCommand.Pause
					PlaystateCommand.UNPAUSE -> ServerCommand.PlaybackCommand.Resume
					PlaystateCommand.PLAY_PAUSE -> ServerCommand.PlaybackCommand.Toggle
					PlaystateCommand.NEXT_TRACK -> ServerCommand.PlaybackCommand.NextTrack
					PlaystateCommand.PREVIOUS_TRACK -> ServerCommand.PlaybackCommand.PreviousTrack
					PlaystateCommand.SEEK -> ServerCommand.PlaybackCommand.Seek(
						message.data?.seekPositionTicks?.ticks?.inWholeMilliseconds ?: 0L
					)

					else -> null
				} ?: return@onEach
				_commands.emit(command)
			}
			.launchIn(scope)
		subscribeGeneralCommands(
			setOf(
				GeneralCommandType.DISPLAY_MESSAGE,
				GeneralCommandType.SEND_STRING
			)
		)
			.onEach { message ->
				val text by message
				text?.let { text ->
					_commands.emit(ServerCommand.DisplayMessage(text))
				}
			}
			.launchIn(scope)
	}

	private suspend fun postCapabilities() {
		try {
			api.sessionApi.postFullCapabilities(
				data = ClientCapabilitiesDto(
					playableMediaTypes = listOf(MediaType.AUDIO),
					supportedCommands = listOf(
						GeneralCommandType.DISPLAY_CONTENT,
						GeneralCommandType.DISPLAY_MESSAGE,
						GeneralCommandType.SEND_STRING
					),
					supportsMediaControl = true,
					supportsPersistentIdentifier = true,
					iconUrl = "https://navic.app/jellyfin.svg",
					appStoreUrl = "https://codeberg.org/paige/Navic"
				)
			)
		} catch (ex: Exception) {
			Logger.e("JellyfinMusicServer", "could not post capabilities", ex)
		}
	}

	override suspend fun ping(): ServerStatus {
		//systemApi.getPingSystem()
		val info = api.systemApi.getPublicSystemInfo().content
		return ServerStatus(
			serverVersion = info.version,
			version = info.version,
			type = "jellyfin",
			isOpenSubsonic = false
		)
	}

	override suspend fun serverInfo(): ServerInfo {
		val info = api.systemApi.getPublicSystemInfo().content
		return ServerInfo(
			serverVersion = info.version,
			version = null,
			type = "jellyfin",
			isOpenSubsonic = false
		)
	}

	override suspend fun currentUser(): DomainUser {
		val user = api.userApi.getUserById(userId).content
		return DomainUser(
			username = user.name ?: "Jellyfin User",
			roles = if (user.policy?.isAdministrator == true) setOf("admin") else emptySet()
		)
	}

	override suspend fun getAlbums(
		type: DomainAlbumListType,
		size: Int,
		offset: Int
	): List<DomainAlbum> {
		val sortByList = when (type) {
			is DomainAlbumListType.Newest -> listOf(ItemSortBy.DATE_CREATED)
			is DomainAlbumListType.Random -> listOf(ItemSortBy.RANDOM)
			is DomainAlbumListType.Frequent -> listOf(ItemSortBy.PLAY_COUNT)
			is DomainAlbumListType.Recent -> listOf(ItemSortBy.DATE_PLAYED)
			is DomainAlbumListType.Highest -> listOf(ItemSortBy.COMMUNITY_RATING)
			is DomainAlbumListType.AlphabeticalByName -> listOf(ItemSortBy.NAME)
			is DomainAlbumListType.AlphabeticalByArtist -> listOf(
				ItemSortBy.ALBUM_ARTIST,
				ItemSortBy.NAME
			)

			is DomainAlbumListType.Year -> listOf(ItemSortBy.PRODUCTION_YEAR)
			is DomainAlbumListType.ByYear -> listOf(ItemSortBy.PRODUCTION_YEAR)
			else -> listOf(ItemSortBy.SORT_NAME)
		}

		val sortOrder =
			if (type is DomainAlbumListType.Newest || type is DomainAlbumListType.Recent || type is DomainAlbumListType.Frequent || type is DomainAlbumListType.Highest || type is DomainAlbumListType.Year) {
				SortOrder.DESCENDING
			} else {
				SortOrder.ASCENDING
			}

		val genres = if (type is DomainAlbumListType.ByGenre) listOf(type.genre) else emptyList()
		val years =
			if (type is DomainAlbumListType.ByYear) (type.fromYear..type.toYear).toList() else emptyList()

		val result = api.libraryApi.getItems(
			userId = userId,
			includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
			limit = size,
			startIndex = offset,
			recursive = true,
			sortBy = sortByList,
			sortOrder = listOf(sortOrder),
			genres = genres,
			years = years,
			fields = listOf(ItemFields.PRIMARY_IMAGE_ASPECT_RATIO, ItemFields.ITEM_COUNTS)
		).content

		return result.items.map { it.toDomainAlbum() }
	}

	override suspend fun getAlbum(id: String): DomainAlbum {
		val albumUuid = id.toUuid()
		val albumDto = api.libraryApi.getItem(userId = userId, itemId = albumUuid).content
		val songsResult = api.libraryApi.getItems(
			userId = userId,
			parentId = albumUuid,
			includeItemTypes = listOf(BaseItemKind.AUDIO),
			recursive = true,
			sortBy = listOf(ItemSortBy.PARENT_INDEX_NUMBER, ItemSortBy.INDEX_NUMBER),
			sortOrder = listOf(SortOrder.ASCENDING),
			fields = listOf(ItemFields.MEDIA_STREAMS, ItemFields.PRIMARY_IMAGE_ASPECT_RATIO)
		).content

		val songs = songsResult.items.map { it.toDomainSong() }
		return albumDto.toDomainAlbum(songs)
	}

	override suspend fun getSong(id: String): DomainSong {
		val item = api.libraryApi.getItem(userId = userId, itemId = id.toUuid()).content
		return item.toDomainSong()
	}

	override suspend fun getArtists(): List<DomainArtist> {
		val result = api.libraryApi.getItems(
			userId = userId,
			includeItemTypes = listOf(BaseItemKind.MUSIC_ARTIST),
			recursive = true,
			sortBy = listOf(ItemSortBy.SORT_NAME),
			sortOrder = listOf(SortOrder.ASCENDING),
			fields = listOf(ItemFields.PRIMARY_IMAGE_ASPECT_RATIO, ItemFields.ITEM_COUNTS)
		).content
		return result.items.map { it.toDomainArtist() }
	}

	override suspend fun getArtistInfo(id: String): DomainArtistInfo {
		val artistUuid = id.toUuid()
		val artist = api.libraryApi.getItem(userId = userId, itemId = artistUuid).content
		val musicBrainzId = artist.providerIds?.entries?.find {
			it.key.equals(
				"MusicBrainzArtist",
				ignoreCase = true
			)
		}?.value
		val similar = runCatching {
			api.libraryApi.getSimilarArtists(
				itemId = artistUuid,
				userId = userId,
				limit = 5
			).content.items.map { it.toDomainArtist() }
		}.getOrDefault(emptyList())

		return DomainArtistInfo(
			biography = artist.overview,
			musicBrainzId = musicBrainzId,
			lastFmUrl = null,
			smallImageUrl = getCoverArtUrl(id, "300"),
			mediumImageUrl = getCoverArtUrl(id, "600"),
			largeImageUrl = getCoverArtUrl(id, "1000"),
			similarArtists = similar
		)
	}

	override suspend fun getAlbumInfo(id: String): DomainAlbumInfo {
		val album = api.libraryApi.getItem(userId = userId, itemId = id.toUuid()).content
		val musicBrainzId = album.providerIds?.entries?.find {
			it.key.equals(
				"MusicBrainzAlbum",
				ignoreCase = true
			)
		}?.value
		return DomainAlbumInfo(
			notes = album.overview,
			musicBrainzId = musicBrainzId,
			lastFmUrl = null,
			smallImageUrl = getCoverArtUrl(id, "300"),
			mediumImageUrl = getCoverArtUrl(id, "600"),
			largeImageUrl = getCoverArtUrl(id, "1000")
		)
	}

	override suspend fun getGenres(): List<DomainGenre> {
		val result = api.libraryApi.getItems(
			userId = userId,
			includeItemTypes = listOf(BaseItemKind.MUSIC_GENRE),
			recursive = true,
			sortBy = listOf(ItemSortBy.SORT_NAME),
			sortOrder = listOf(SortOrder.ASCENDING),
			fields = listOf(ItemFields.PRIMARY_IMAGE_ASPECT_RATIO, ItemFields.ITEM_COUNTS)
		).content

		return result.items.map { dto ->
			DomainGenre(
				name = dto.name ?: "Unknown",
				albumCount = dto.albumCount ?: dto.childCount ?: 0,
				songCount = dto.songCount ?: dto.recursiveItemCount ?: 0,
				coverArtIds = emptyList()
			)
		}
	}

	override suspend fun getPlaylists(): List<DomainPlaylist> {
		val result = api.libraryApi.getItems(
			userId = userId,
			includeItemTypes = listOf(BaseItemKind.PLAYLIST),
			recursive = true,
			sortBy = listOf(ItemSortBy.SORT_NAME),
			sortOrder = listOf(SortOrder.ASCENDING),
			fields = listOf(ItemFields.PRIMARY_IMAGE_ASPECT_RATIO, ItemFields.ITEM_COUNTS)
		).content
		return result.items.map { it.toDomainPlaylist() }
	}

	override suspend fun getPlaylist(id: String): DomainPlaylist {
		val playlistUuid = id.toUuid()
		val playlistDto = api.libraryApi.getItem(userId = userId, itemId = playlistUuid).content
		val songsResult = runCatching {
			api.playlistApi.getPlaylistItems(
				playlistId = playlistUuid,
				userId = userId,
				limit = 10000,
				fields = listOf(ItemFields.MEDIA_STREAMS, ItemFields.PRIMARY_IMAGE_ASPECT_RATIO)
			).content
		}.getOrElse {
			api.libraryApi.getItems(
				userId = userId,
				parentId = playlistUuid,
				includeItemTypes = listOf(BaseItemKind.AUDIO),
				limit = 10000,
				fields = listOf(ItemFields.MEDIA_STREAMS, ItemFields.PRIMARY_IMAGE_ASPECT_RATIO)
			).content
		}

		val songs = songsResult.items.map { it.toDomainSong() }
		return playlistDto.toDomainPlaylist(songs)
	}

	override suspend fun getSimilarSongs(id: String, count: Int): List<DomainSong> {
		val uuid = id.toUuid()
		val result = runCatching {
			api.instantMixApi.getInstantMixFromSong(
				itemId = uuid,
				userId = userId,
				limit = count,
				fields = listOf(ItemFields.MEDIA_STREAMS, ItemFields.PRIMARY_IMAGE_ASPECT_RATIO)
			).content
		}.getOrElse {
			api.instantMixApi.getInstantMixFromItem(
				itemId = uuid,
				userId = userId,
				limit = count,
				fields = listOf(ItemFields.MEDIA_STREAMS, ItemFields.PRIMARY_IMAGE_ASPECT_RATIO)
			).content
		}
		return result.items.map { it.toDomainSong() }
	}

	override suspend fun search(query: String): DomainSearchResult {
		val result = api.libraryApi.getItems(
			userId = userId,
			searchTerm = query,
			recursive = true,
			includeItemTypes = listOf(
				BaseItemKind.AUDIO,
				BaseItemKind.MUSIC_ALBUM,
				BaseItemKind.MUSIC_ARTIST
			),
			fields = listOf(
				ItemFields.MEDIA_STREAMS,
				ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
				ItemFields.ITEM_COUNTS
			)
		).content

		val artists =
			result.items.filter { it.type == BaseItemKind.MUSIC_ARTIST }.map { it.toDomainArtist() }
		val albums =
			result.items.filter { it.type == BaseItemKind.MUSIC_ALBUM }.map { it.toDomainAlbum() }
		val songs = result.items.filter { it.type == BaseItemKind.AUDIO }.map { it.toDomainSong() }

		return DomainSearchResult(
			artists = artists,
			albums = albums,
			songs = songs
		)
	}

	override suspend fun getLyrics(songId: String): ServerLyrics? {
		return try {
			val response = api.lyricApi.getLyrics(songId.toUuid()).content
			val lines = response.lyrics.map { line ->
				ServerLyricLine(
					startMillis = line.start?.let { it / 10_000L },
					text = line.text
				)
			}
			ServerLyrics(
				lang = null,
				synced = lines.any { it.startMillis != null },
				offsetMillis = 0,
				lines = lines
			)
		} catch (_: Exception) {
			null
		}
	}

	override suspend fun star(id: String) {
		api.userDataApi.markFavoriteItem(userId = userId, itemId = id.toUuid())
	}

	override suspend fun unstar(id: String) {
		api.userDataApi.unmarkFavoriteItem(userId = userId, itemId = id.toUuid())
	}

	override suspend fun setRating(id: String, rating: Int) {
		val uuid = id.toUuid()
		if (rating > 0) {
			api.userDataApi.updateUserItemRating(
				userId = userId,
				itemId = uuid,
				likes = rating > 3
			)
		} else {
			api.userDataApi.deleteUserItemRating(
				userId = userId,
				itemId = uuid
			)
		}
	}

	override suspend fun scrobble(id: String, submission: Boolean, time: Instant?) {
		//doesn't support scrobble
	}

	override suspend fun reportPlayback(
		id: String,
		positionMs: Long,
		state: DomainPlaybackState,
		isDeviceMuted: Boolean,
		deviceVolume: Int,
		@Player.RepeatMode repeatMode: Int,
		shuffleModeEnabled: Boolean,
		playbackRate: Float
	) {
		val uuid = id.toUuid()
		val positionTicks = positionMs * 10_000L
		val repeatMode = when (repeatMode) {
			Player.REPEAT_MODE_ALL -> RepeatMode.REPEAT_ALL
			Player.REPEAT_MODE_ONE -> RepeatMode.REPEAT_ONE
			else -> RepeatMode.REPEAT_NONE
		}
		val playbackOrder = if (shuffleModeEnabled) PlaybackOrder.SHUFFLE else PlaybackOrder.DEFAULT

		when (state) {
			DomainPlaybackState.PLAYING, DomainPlaybackState.STARTING -> {
				api.sessionApi.reportPlaybackProgress(
					PlaybackProgressInfo(
						itemId = uuid,
						positionTicks = positionTicks,
						isPaused = false,
						canSeek = true,
						isMuted = isDeviceMuted,
						volumeLevel = deviceVolume,
						playMethod = PlayMethod.DIRECT_PLAY,
						repeatMode = repeatMode,
						playbackOrder = playbackOrder
					)
				)
			}

			DomainPlaybackState.PAUSED -> {
				api.sessionApi.reportPlaybackProgress(
					PlaybackProgressInfo(
						itemId = uuid,
						positionTicks = positionTicks,
						isPaused = true,
						canSeek = true,
						isMuted = isDeviceMuted,
						volumeLevel = deviceVolume,
						playMethod = PlayMethod.DIRECT_PLAY,
						repeatMode = repeatMode,
						playbackOrder = playbackOrder
					)
				)
			}

			DomainPlaybackState.STOPPED -> {
				api.sessionApi.reportPlaybackStopped(
					PlaybackStopInfo(
						itemId = uuid,
						positionTicks = positionTicks,
						failed = false
					)
				)
			}
		}
	}

	override suspend fun createPlaylist(name: String, songIds: List<String>): DomainPlaylist {
		val playlistDto = api.playlistApi.createPlaylist(
			data = CreatePlaylistDto(
				name = name,
				ids = songIds.map { it.toUuid() },
				userId = userId,
				mediaType = MediaType.AUDIO,
				users = listOf(
					org.jellyfin.sdk.model.api.PlaylistUserPermissions(
						userId = userId,
						canEdit = true
					)
				),
				isPublic = true
			)
		).content

		return getPlaylist(playlistDto.id)
	}

	override suspend fun updatePlaylist(
		id: String,
		name: String?,
		comment: String?,
		public: Boolean?,
		songIdsToAdd: List<String>,
		songIndicesToRemove: List<Int>
	) {
		if (id.startsWith("local_")) return
		val playlistUuid = id.toUuid()

		if (name != null || comment != null) {
			runCatching {
				val currentItem =
					api.libraryApi.getItem(userId = userId, itemId = playlistUuid).content
				api.itemUpdateApi.updateItem(
					itemId = playlistUuid,
					data = currentItem.copy(
						name = name ?: currentItem.name,
						overview = comment ?: currentItem.overview
					)
				)
			}
		}

		if (songIdsToAdd.isNotEmpty()) {
			api.playlistApi.addItemToPlaylist(
				playlistId = playlistUuid,
				ids = songIdsToAdd.map { it.toUuid() },
				userId = userId
			)
		}

		if (songIndicesToRemove.isNotEmpty()) {
			runCatching {
				val currentItems = api.playlistApi.getPlaylistItems(
					playlistId = playlistUuid,
					userId = userId,
					limit = 10000
				).content.items

				val entryIdsToRemove = songIndicesToRemove
					.filter { it in currentItems.indices }
					.mapNotNull { currentItems[it].playlistItemId }

				if (entryIdsToRemove.isNotEmpty()) {
					api.playlistApi.removeItemFromPlaylist(
						playlistId = id,
						entryIds = entryIdsToRemove
					)
				}
			}
		}
	}

	override suspend fun deletePlaylist(id: String) {
		if (id.startsWith("local_")) return
		runCatching {
			api.libraryApi.deleteItem(itemId = id.toUuid())
		}
	}

	override suspend fun getPlaybackStreamUrl(
		id: String,
		bitrateKbps: Int,
		format: String?,
		skipTranscodeDecisions: Boolean
	): String {
		val params = mutableListOf("ApiKey=$accessToken")

		if (format != null) {
			params.add("container=$format")
		}

		if (bitrateKbps > 0) {
			params.add("maxStreamingBitrate=${bitrateKbps * 1000}")
		} else {
			params.add("static=true")
		}
		return "$baseUrl/Audio/$id/stream?${params.joinToString("&")}"
	}

	override fun getHlsStreamUrl(id: String, bitrateKbps: Int, format: String?): String? {
		if (bitrateKbps <= 0 || format == null) return null

		val params = mutableListOf(
			"ApiKey=$accessToken",
			"mediaSourceId=$id",
			"audioCodec=$format",
			"maxStreamingBitrate=${bitrateKbps * 1000}"
		)
		// opus/flac aren't valid in mpeg-ts segments
		if (format.equals("opus", ignoreCase = true) || format.equals("flac", ignoreCase = true)) {
			params.add("segmentContainer=mp4")
		}
		return "$baseUrl/Audio/$id/master.m3u8?${params.joinToString("&")}"
	}

	override fun getCoverArtUrl(id: String, size: String?): String {
		val query = size?.let { "?fillWidth=$it&fillHeight=$it&quality=90&ApiKey=$accessToken" }
			?: "?ApiKey=$accessToken"
		return "$baseUrl/Items/$id/Images/Primary$query"
	}

	override suspend fun getCoverArt(id: String, size: String?): ByteArray =
		withContext(Dispatchers.IO) {
			val url = getCoverArtUrl(id, size)
			java.net.URL(url).readBytes()
		}

	override fun getAvatarUrl(username: String): String {
		return "$baseUrl/Users/$userId/Images/Primary?ApiKey=$accessToken"
	}

	override fun parseResponseError(contentType: String?, body: String): ServerException? {
		return null
	}

	override suspend fun isImageAbsent(playlistId: String): Boolean = runCatching {
		api.imageApi.getItemImageInfos(playlistId.toUuid()).content
			.none { it.imageType == ImageType.PRIMARY }
	}.getOrDefault(true)

	override suspend fun uploadPlaylistCover(id: String, bytes: ByteArray, contentType: String) {
		api.imageApi.setItemImage(
			itemId = id.toUuid(),
			imageType = ImageType.PRIMARY,
			data = FileInfo(Base64.encodeToByteArray(bytes), contentType)
		)
	}

	override suspend fun removePlaylistCover(id: String) {
		api.imageApi.deleteItemImage(id.toUuid(), ImageType.PRIMARY)
	}
}

private fun String.toUuid(): UUID {
	val clean = replace("-", "")
	if (clean.length == 32) {
		val formatted = "${clean.substring(0, 8)}-${clean.substring(8, 12)}-${
			clean.substring(
				12,
				16
			)
		}-${clean.substring(16, 20)}-${clean.substring(20)}"
		val parsed = runCatching { UUID.fromString(formatted) }.getOrNull()
		if (parsed != null) return parsed
	}
	return runCatching { UUID.fromString(this) }.getOrElse {
		UUID.nameUUIDFromBytes(toByteArray())
	}
}
