/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.nowPlaying.component.controls

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.koin.compose.koinInject
import paige.navic.di.LocalNavStack
import paige.navic.domain.model.settings.CoverArtTapAction
import paige.navic.playback.PlayerRepository
import paige.navic.preferences.PreferenceManager
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screen.nowPlaying.component.NowPlayingArtwork
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
@Composable
fun NowPlayingArtworkPager(
	modifier: Modifier = Modifier,
	isLandscape: Boolean
) {
	val backStack = LocalNavStack.current
	val preferenceManager = koinInject<PreferenceManager>()
	val player = koinInject<PlayerRepository>()
	val playerState by player.uiState.collectAsStateWithLifecycle()
	val semaphore = remember { Semaphore(permits = 2) }

	val pagerState = rememberPagerState(
		initialPage = playerState.activeIndex.coerceAtLeast(0),
		pageCount = { playerState.queue.size }
	)

	LaunchedEffect(playerState.activeIndex) {
		if (pagerState.isScrollInProgress) return@LaunchedEffect

		semaphore.withPermit {
			if (playerState.activeIndex != pagerState.currentPage && playerState.activeIndex != -1) {
				pagerState.animateScrollToPage(playerState.activeIndex)
			}
		}
	}

	LaunchedEffect(pagerState) {
		pagerState.interactionSource.interactions
			.debounce(500.milliseconds)
			.filterIsInstance<DragInteraction.Stop>()
			.collect {
				val page = pagerState.settledPage

				if (page != playerState.activeIndex && page in playerState.activeQueue.indices) {
					val wasPaused = playerState.isPaused
					player.playAt(playerState.normalIndex(page))
					if (wasPaused) {
						player.pause()
					}
				}
			}
	}

	HorizontalPager(
		modifier = modifier,
		state = pagerState,
		contentPadding = PaddingValues(horizontal = if (isLandscape) 0.dp else 8.dp),
		userScrollEnabled = preferenceManager.swipeToSkip,
		overscrollEffect = null
	) { page ->
		val song = playerState.activeQueue.getOrNull(page) ?: return@HorizontalPager
		val tapAction = preferenceManager.nowPlayingCoverArtAction
		val enabled = pagerState.settledPage == page
			&& tapAction != CoverArtTapAction.Disabled
		Box(
			modifier = Modifier.fillMaxSize(),
			contentAlignment = Alignment.Center
		) {
			NowPlayingArtwork(
				song = song,
				isLandscape = isLandscape,
				onClick = if (enabled) dropUnlessResumed {
					when (tapAction) {
						CoverArtTapAction.ShowLyrics -> backStack.add(Screen.Lyrics)
					}
				} else null
			)
		}
	}
}
