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

val Icons.Outlined.Folder: ImageVector
	get() {
		if (_Folder != null) {
			return _Folder!!
		}
		_Folder = ImageVector.Builder(
			name = "Folder",
			defaultWidth = 24.dp,
			defaultHeight = 24.dp,
			viewportWidth = 24f,
			viewportHeight = 24f
		).apply {
			path(fill = SolidColor(Color.White)) {
				moveTo(4f, 20f)
				quadToRelative(-0.82f, 0f, -1.41f, -0.59f)
				reflectiveQuadTo(2f, 18f)
				lineTo(2f, 6f)
				quadTo(2f, 5.18f, 2.59f, 4.59f)
				reflectiveQuadTo(4f, 4f)
				lineTo(9.18f, 4f)
				quadToRelative(0.4f, 0f, 0.76f, 0.15f)
				reflectiveQuadToRelative(0.64f, 0.43f)
				lineTo(12f, 6f)
				horizontalLineToRelative(8f)
				quadToRelative(0.83f, 0f, 1.41f, 0.59f)
				quadTo(22f, 7.18f, 22f, 8f)
				lineTo(22f, 18f)
				quadToRelative(0f, 0.82f, -0.59f, 1.41f)
				reflectiveQuadTo(20f, 20f)
				lineTo(4f, 20f)
				close()
				moveTo(4f, 18f)
				lineTo(20f, 18f)
				lineTo(20f, 8f)
				lineTo(11.18f, 8f)
				lineToRelative(-2f, -2f)
				lineTo(4f, 6f)
				lineTo(4f, 18f)
				close()
				moveTo(4f, 18f)
				lineTo(4f, 6f)
				lineTo(4f, 8f)
				lineTo(4f, 18f)
				close()
			}
		}.build()

		return _Folder!!
	}

@Suppress("ObjectPropertyName")
private var _Folder: ImageVector? = null
