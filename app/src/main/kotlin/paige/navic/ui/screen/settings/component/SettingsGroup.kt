/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.settings.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import paige.navic.designsystem.theme.defaultFont
import paige.navic.domain.model.settings.ThemeStyle
import paige.navic.preferences.PreferenceManager
import paige.navic.ui.component.common.SegmentedListItemDefaults

@Composable
fun SettingsGroup(
	modifier: Modifier = Modifier,
	title: (@Composable () -> Unit)? = null,
	verticalArrangement: Arrangement.Vertical = Arrangement.Top,
	content: @Composable ColumnScope.() -> Unit,
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val primary = MaterialTheme.colorScheme.primary
	val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
	val titleColor = remember(preferenceManager.themeStyle) {
		when (preferenceManager.themeStyle) {
			ThemeStyle.Material -> primary
			else -> onSurfaceVariant
		}
	}
	val titleFontWeight = remember(preferenceManager.themeStyle) {
		when (preferenceManager.themeStyle) {
			ThemeStyle.Material -> FontWeight.Normal
			else -> FontWeight.Bold
		}
	}

	Column(
		modifier = modifier.fillMaxWidth(),
		verticalArrangement = verticalArrangement
	) {
		if (title != null) {
			Box(modifier = Modifier.padding(start = 12.dp)) {
				CompositionLocalProvider(
					LocalTextStyle provides MaterialTheme.typography.titleSmallEmphasized.copy(
						fontFamily = defaultFont(grade = 100, round = 100f),
						fontWeight = titleFontWeight
					),
					LocalContentColor provides titleColor
				) {
					title()
				}
			}
		}

		Column(
			modifier = Modifier.padding(top = if (title != null) 6.dp else 0.dp),
			verticalArrangement = Arrangement.spacedBy(SegmentedListItemDefaults.segmentedGap())
		) {
			content()
		}
	}
}

object SettingsGroupDefaults {
	val GapBetweenGroups = 24.dp
}
