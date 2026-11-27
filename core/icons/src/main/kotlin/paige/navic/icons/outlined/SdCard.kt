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

val Icons.Outlined.SdCard: ImageVector
	get() {
		if (_SdCard != null) {
			return _SdCard!!
		}
		_SdCard = ImageVector.Builder(
			name = "SdCard",
			defaultWidth = 24.dp,
			defaultHeight = 24.dp,
			viewportWidth = 24f,
			viewportHeight = 24f
		).apply {
			path(fill = SolidColor(Color.White)) {
				moveTo(9f, 11f)
				horizontalLineToRelative(2f)
				verticalLineTo(7f)
				horizontalLineTo(9f)
				verticalLineToRelative(4f)
				close()
				moveToRelative(3f, 0f)
				horizontalLineToRelative(2f)
				verticalLineTo(7f)
				horizontalLineTo(12f)
				verticalLineToRelative(4f)
				close()
				moveToRelative(3f, 0f)
				horizontalLineToRelative(2f)
				verticalLineTo(7f)
				horizontalLineTo(15f)
				verticalLineToRelative(4f)
				close()
				moveTo(6f, 22f)
				quadTo(5.18f, 22f, 4.59f, 21.41f)
				reflectiveQuadTo(4f, 20f)
				verticalLineTo(8f)
				lineTo(10f, 2f)
				horizontalLineToRelative(8f)
				quadToRelative(0.82f, 0f, 1.41f, 0.59f)
				reflectiveQuadTo(20f, 4f)
				verticalLineTo(20f)
				quadToRelative(0f, 0.82f, -0.59f, 1.41f)
				reflectiveQuadTo(18f, 22f)
				horizontalLineTo(6f)
				close()
				moveTo(6f, 20f)
				horizontalLineTo(18f)
				verticalLineTo(4f)
				horizontalLineTo(10.85f)
				lineTo(6f, 8.85f)
				verticalLineTo(20f)
				close()
				moveToRelative(0f, 0f)
				horizontalLineToRelative(4.85f)
				horizontalLineTo(18f)
				horizontalLineTo(6f)
				close()
			}
		}.build()

		return _SdCard!!
	}

@Suppress("ObjectPropertyName")
private var _SdCard: ImageVector? = null
