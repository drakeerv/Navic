/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.component.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.takeOrElse
import org.koin.compose.koinInject
import paige.navic.domain.model.settings.ThemeStyle
import paige.navic.preferences.PreferenceManager

@Composable
fun Switch(
	checked: Boolean,
	onCheckedChange: ((Boolean) -> Unit)?,
	modifier: Modifier = Modifier,
	thumbContent: (@Composable () -> Unit)? = null,
	enabled: Boolean = true,
	colors: SwitchColors = SwitchDefaults.colors(),
	interactionSource: MutableInteractionSource? = null,
) {
	val preferenceManager = koinInject<PreferenceManager>()
	androidx.compose.material3.Switch(
		checked = checked,
		onCheckedChange = onCheckedChange,
		modifier = modifier.then(
			if (preferenceManager.themeStyle == ThemeStyle.OneUI)
				Modifier.scale(0.8f)
			else Modifier
		),
		thumbContent = thumbContent,
		enabled = enabled,
		colors = colors,
		interactionSource = interactionSource
	)
}

object SwitchDefaults {
	@Composable
	fun colors(): SwitchColors {
		val preferenceManager = koinInject<PreferenceManager>()
		val borderColor = remember(preferenceManager.themeStyle) {
			when (preferenceManager.themeStyle) {
				ThemeStyle.OneUI -> Color.Transparent
				else -> Color.Unspecified
			}
		}
		return androidx.compose.material3.SwitchDefaults.colors(
			checkedBorderColor = borderColor.takeOrElse { Color.Transparent },
			uncheckedBorderColor = borderColor.takeOrElse { MaterialTheme.colorScheme.outline },
			disabledCheckedBorderColor = borderColor.takeOrElse { MaterialTheme.colorScheme.surface },
			disabledUncheckedBorderColor = borderColor.takeOrElse {
				MaterialTheme.colorScheme.onSurface
					.copy(alpha = 0.12f)
					.compositeOver(MaterialTheme.colorScheme.surface)
			}
		)
	}
}
