/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.component.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
fun Waveform(
	modifier: Modifier = Modifier,
	isPlaying: Boolean
) {
	if (isPlaying) {
		AnimatedWaveform(modifier)
	} else {
		StaticWaveform(modifier)
	}
}

@Composable
private fun AnimatedWaveform(modifier: Modifier = Modifier) {
	val transition = rememberInfiniteTransition()
	Row(
		modifier = modifier.height(18.dp),
		horizontalArrangement = Arrangement.spacedBy(2.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		repeat(5) { index ->
			val fraction = transition.animateFloat(
				initialValue = 0.2f,
				targetValue = 1f,
				animationSpec = infiniteRepeatable(
					animation = tween(
						durationMillis = 400 + (index * 150),
						easing = FastOutSlowInEasing
					),
					repeatMode = RepeatMode.Reverse
				)
			)
			Box(
				modifier = Modifier
					.width(3.dp)
					.fillMaxHeight()
					.graphicsLayer {
						scaleY = fraction.value
						transformOrigin = TransformOrigin(0.5f, 0.5f)
					}
					.background(MaterialTheme.colorScheme.onSurface, shape = CircleShape)
			)
		}
	}
}

@Composable
private fun StaticWaveform(modifier: Modifier = Modifier) {
	Row(
		modifier = modifier.height(18.dp),
		horizontalArrangement = Arrangement.spacedBy(2.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		repeat(5) {
			Box(
				modifier = Modifier
					.width(3.dp)
					.fillMaxHeight(0.2f)
					.background(MaterialTheme.colorScheme.onSurface, shape = CircleShape)
			)
		}
	}
}
