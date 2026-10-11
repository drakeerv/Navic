/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.model.settings

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.capsule.ContinuousRoundedRectangle

enum class CoverArtShape(
	val shape: Shape,
	val decreasedShape: Shape = shape,
	val tileShape: Shape = shape
) {
	Square(RectangleShape),
	Soft(
		shape = ContinuousRoundedRectangle(10.dp),
		decreasedShape = ContinuousRoundedRectangle(8.dp),
		tileShape = RoundedCornerShape(10.dp)
	),
	Curved(
		shape = ContinuousRoundedRectangle(32.dp),
		decreasedShape = ContinuousRoundedRectangle(10.dp),
		tileShape = RoundedCornerShape(32.dp)
	),
	Circle(CircleShape)
}

@Suppress("DEPRECATION")
fun Shape.tileVariant(): Shape =
	if (this is ContinuousRoundedRectangle) {
		RoundedCornerShape(
			topStart = topStart,
			topEnd = topEnd,
			bottomEnd = bottomEnd,
			bottomStart = bottomStart
		)
	} else {
		this
	}
