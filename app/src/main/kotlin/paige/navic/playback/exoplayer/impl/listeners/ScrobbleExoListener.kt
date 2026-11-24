/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.playback.exoplayer.impl.listeners

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import paige.navic.database.entity.SyncActionType
import paige.navic.domain.manager.session.SessionManager
import paige.navic.domain.manager.session.SyncManager
import paige.navic.domain.manager.session.connectivity.ReachabilityFlow
import paige.navic.domain.manager.session.isJellyfin
import paige.navic.domain.manager.session.isLocal
import paige.navic.domain.manager.session.supportsFeature
import paige.navic.domain.model.DomainPlaybackState
import paige.navic.domain.server.PlayHistoryRecorder
import paige.navic.domain.server.ScrobbleProvider
import paige.navic.domain.server.ServerFeature
import paige.navic.preferences.PreferenceManager
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class ScrobbleExoListener(
	private val player: Player,
	val reachability: ReachabilityFlow,
	val syncManager: SyncManager,
	val sessionManager: SessionManager,
	val preferenceManager: PreferenceManager
) : Player.Listener {
	private var currentMediaId: String? = null
	private var hasScrobbledCurrent = false
	private var hasSentNowPlaying = false
	private var hasRecordedLocalPlay = false
	private var progressJob: Job? = null
	private var accumulatedPlayTime: Long = 0
	private val scope = CoroutineScope(Dispatchers.IO)

	init {
		player.addListener(this)
	}

	private fun getPlaybackState(): DomainPlaybackState {
		return when {
			player.playbackState == Player.STATE_ENDED || player.playbackState == Player.STATE_IDLE -> DomainPlaybackState.STOPPED
			player.playWhenReady -> DomainPlaybackState.PLAYING
			else -> DomainPlaybackState.PAUSED
		}
	}

	private fun reportPlaybackState(
		songId: String?,
		positionMs: Long,
		state: DomainPlaybackState,
		isDeviceMuted: Boolean = player.isDeviceMuted,
		deviceVolume: Int = player.deviceVolume,
		@Player.RepeatMode repeatMode: Int = player.repeatMode,
		shuffleModeEnabled: Boolean = player.shuffleModeEnabled,
		playbackRate: Float = player.playbackParameters.speed
	) {
		val server = sessionManager.server ?: return
		if (songId == null
			|| !reachability.isReachable.value
			|| !sessionManager.supportsFeature(ServerFeature.PLAYBACK_REPORT)
			) return

		scope.launch {
			runCatching {
				server.reportPlayback(
					id = songId,
					positionMs = positionMs,
					state = state,
					isDeviceMuted = isDeviceMuted,
					deviceVolume = deviceVolume,
					repeatMode = repeatMode,
					shuffleModeEnabled = shuffleModeEnabled,
					playbackRate = playbackRate
				)
			}
		}
	}

	private fun submitNowPlayingScrobble(songId: String?) {
		if (!preferenceManager.enableScrobbling || songId == null) return
		if (!reachability.isReachable.value) return

		val scrobble = sessionManager.provider as? ScrobbleProvider ?: return
		scope.launch {
			runCatching {
				scrobble.scrobble(songId, submission = false)
			}
		}
	}

	private fun recordLocalPlay(songId: String?) {
		if (songId == null || hasRecordedLocalPlay || !sessionManager.isLocal()) return
		if (accumulatedPlayTime < LOCAL_PLAY_MINIMUM_MS) return
		hasRecordedLocalPlay = true
		val playHistory = sessionManager.provider as? PlayHistoryRecorder ?: return
		scope.launch {
			runCatching {
				playHistory.recordPlay(songId, Clock.System.now())
			}
		}
	}

	private fun scrobbleSubmission(songId: String?) {
		if (songId == null) return
		if (!preferenceManager.enableScrobbling) return
		val scrobble = sessionManager.provider as? ScrobbleProvider ?: return

		scope.launch {
			val currentTime = Clock.System.now()

			if (reachability.isReachable.value) {
				runCatching {
					scrobble.scrobble(songId, submission = true, time = currentTime)
					return@launch
				}
			}
			syncManager.enqueueAction(SyncActionType.SCROBBLE, songId, currentTime)
		}
	}

	fun hasTransitioned(mediaItem: MediaItem?) {
		currentMediaId = mediaItem?.mediaId
		hasScrobbledCurrent = false
		hasSentNowPlaying = false
		hasRecordedLocalPlay = false
		accumulatedPlayTime = 0
		progressJob?.cancel()

		val songId = mediaItem?.mediaId
		if (songId != null) {
			val pos = player.currentPosition.coerceAtLeast(0L)
			val state = getPlaybackState()
			reportPlaybackState(
				songId = songId,
				positionMs = pos,
				state = state
			)
			submitNowPlayingScrobble(songId)
			hasSentNowPlaying = true

			if (player.isPlaying) {
				startProgressTracker()
			}
		}
	}

	override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) =
		hasTransitioned(mediaItem)

	private fun startProgressTracker() {
		progressJob?.cancel()
		progressJob = scope.launch(Dispatchers.Main) {
			var lastTickTime = Clock.System.now().toEpochMilliseconds()
			var ticksSinceReport = 0

			while (isActive) {
				val now = Clock.System.now().toEpochMilliseconds()
				val timePassed = now - lastTickTime
				lastTickTime = now

				accumulatedPlayTime += timePassed

				checkProgress()

				ticksSinceReport++
				if (ticksSinceReport >= 5) {
					ticksSinceReport = 0
					currentMediaId?.let { songId ->
						reportPlaybackState(
							songId = songId,
							positionMs = player.currentPosition.coerceAtLeast(0L),
							state = DomainPlaybackState.PLAYING
						)
					}
				}

				delay(2.seconds)
			}
		}
	}

	private fun checkProgress() {
		if (hasScrobbledCurrent) return

		if (sessionManager.isLocal()) {
			recordLocalPlay(currentMediaId)
			return
		}

		val duration = player.duration
		if (duration <= 0) return

		val percent = accumulatedPlayTime.toFloat() / duration.toFloat()
		val playedEnoughPercent = percent >= preferenceManager.scrobblePercentage
		val isValidSong = duration >= preferenceManager.minDurationToScrobble * 1000

		if (isValidSong && playedEnoughPercent) {
			scrobbleSubmission(currentMediaId)
			hasScrobbledCurrent = true
		}
	}

	override fun onPositionDiscontinuity(
		oldPosition: Player.PositionInfo,
		newPosition: Player.PositionInfo,
		reason: Int
	) {
		val songId = currentMediaId ?: player.currentMediaItem?.mediaId ?: return
		val newPos = newPosition.positionMs.coerceAtLeast(0L)

		val isSeekOrRepeat = reason == Player.DISCONTINUITY_REASON_SEEK ||
			reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION

		val jumpedToStart = newPosition.positionMs < 5000L
		val wasFurtherAlong = oldPosition.positionMs > 10000L

		if (isSeekOrRepeat && jumpedToStart && wasFurtherAlong) {
			hasScrobbledCurrent = false
			accumulatedPlayTime = 0
		}

		if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
			reportPlaybackState(
				songId = songId,
				positionMs = newPos,
				state = getPlaybackState()
			)
		}
	}

	override fun onIsPlayingChanged(isPlaying: Boolean) {
		val songId = currentMediaId ?: player.currentMediaItem?.mediaId
		if (isPlaying) {
			startProgressTracker()
			if (songId != null) {
				reportPlaybackState(
					songId = songId,
					positionMs = player.currentPosition.coerceAtLeast(0L),
					state = DomainPlaybackState.PLAYING
				)
				if (!hasSentNowPlaying) {
					submitNowPlayingScrobble(songId)
					hasSentNowPlaying = true
				}
			}
		} else {
			progressJob?.cancel()
			if (songId != null) {
				reportPlaybackState(
					songId = songId,
					positionMs = player.currentPosition.coerceAtLeast(0L),
					state = getPlaybackState()
				)
			}
		}
	}

	override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
		val songId = currentMediaId ?: player.currentMediaItem?.mediaId ?: return
		reportPlaybackState(
			songId = songId,
			positionMs = player.currentPosition.coerceAtLeast(0L),
			state = getPlaybackState()
		)
	}

	override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
		val songId = currentMediaId ?: player.currentMediaItem?.mediaId
		if (songId != null) {
			reportPlaybackState(
				songId = songId,
				positionMs = player.currentPosition.coerceAtLeast(0L),
				state = getPlaybackState(),
				playbackRate = playbackParameters.speed
			)
		}
	}

	fun release() {
		progressJob?.cancel()
		player.removeListener(this)
	}

	private companion object {
		const val LOCAL_PLAY_MINIMUM_MS = 10_000L
	}
}
