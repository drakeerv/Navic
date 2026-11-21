/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.icons.outlined

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import paige.navic.icons.Icons

val Icons.Outlined.ArrowBackIOS: ImageVector
	get() {
		if (_ArrowBackIOS != null) {
			return _ArrowBackIOS!!
		}
		_ArrowBackIOS = ImageVector.Builder(
			name = "ArrowBackIOS",
			defaultWidth = 24.dp,
			defaultHeight = 24.dp,
			viewportWidth = 24f,
			viewportHeight = 24f
		).apply {
			path(fill = SolidColor(Color.White)) {
				moveTo(9.55f, 12f)
				lineToRelative(7.35f, 7.35f)
				quadToRelative(0.38f, 0.38f, 0.36f, 0.88f)
				reflectiveQuadTo(16.88f, 21.1f)
				reflectiveQuadTo(16f, 21.48f)
				reflectiveQuadTo(15.13f, 21.1f)
				lineToRelative(-7.7f, -7.68f)
				quadToRelative(-0.3f, -0.3f, -0.45f, -0.67f)
				reflectiveQuadTo(6.83f, 12f)
				reflectiveQuadToRelative(0.15f, -0.75f)
				reflectiveQuadToRelative(0.45f, -0.68f)
				lineToRelative(7.7f, -7.7f)
				quadTo(15.5f, 2.5f, 16.01f, 2.51f)
				quadTo(16.53f, 2.52f, 16.9f, 2.9f)
				reflectiveQuadToRelative(0.38f, 0.88f)
				reflectiveQuadTo(16.9f, 4.65f)
				lineTo(9.55f, 12f)
				close()
			}
		}.build()

		return _ArrowBackIOS!!
	}

@Suppress("ObjectPropertyName")
private var _ArrowBackIOS: ImageVector? = null
