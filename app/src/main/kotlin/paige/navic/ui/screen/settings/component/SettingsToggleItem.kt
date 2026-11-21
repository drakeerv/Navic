/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.settings.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItemShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import paige.navic.ui.component.common.SegmentedListItem
import paige.navic.ui.component.common.SegmentedListItemDefaults
import paige.navic.ui.component.common.Switch

@Composable
fun SettingsToggleItem(
	checked: Boolean,
	onCheckedChange: (Boolean) -> Unit,
	enabled: Boolean = true,
	shapes: ListItemShapes = SegmentedListItemDefaults.segmentedShapes(),
	contentPadding: PaddingValues = SegmentedListItemDefaults.contentPadding(),
	supportingContent: @Composable (() -> Unit)? = null,
	content: @Composable () -> Unit
) {
	val interactionSource = remember { MutableInteractionSource() }

	SegmentedListItem(
		onClick = { onCheckedChange(!checked) },
		enabled = enabled,
		shapes = shapes,
		contentPadding = contentPadding,
		interactionSource = interactionSource,
		content = content,
		supportingContent = supportingContent,
		trailingContent = {
			Row(
				verticalAlignment = Alignment.CenterVertically
			) {
				Switch(
					modifier = Modifier.padding(start = 4.dp),
					checked = checked,
					onCheckedChange = null,
					enabled = enabled,
					interactionSource = interactionSource
				)
			}
		}
	)
}
