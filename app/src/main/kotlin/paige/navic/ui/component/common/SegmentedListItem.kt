/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.component.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemElevation
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import paige.navic.domain.model.settings.ThemeStyle
import paige.navic.preferences.PreferenceManager

@Composable
fun SegmentedListItem(
	modifier: Modifier = Modifier,
	shapes: ListItemShapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 1),
	enabled: Boolean = true,
	leadingContent: @Composable (() -> Unit)? = null,
	trailingContent: @Composable (() -> Unit)? = null,
	overlineContent: @Composable (() -> Unit)? = null,
	supportingContent: @Composable (() -> Unit)? = null,
	verticalAlignment: Alignment.Vertical = SegmentedListItemDefaults.verticalAlignment(),
	colors: ListItemColors = SegmentedListItemDefaults.segmentedColors(),
	elevation: ListItemElevation = ListItemDefaults.elevation(),
	contentPadding: PaddingValues = SegmentedListItemDefaults.contentPadding(),
	content: @Composable () -> Unit,
) {
	androidx.compose.material3.SegmentedListItem(
		modifier = modifier,
		shapes = shapes,
		enabled = enabled,
		leadingContent = leadingContent,
		trailingContent = trailingContent,
		overlineContent = overlineContent,
		supportingContent = supportingContent,
		verticalAlignment = verticalAlignment,
		colors = colors,
		elevation = elevation,
		contentPadding = contentPadding,
		content = content
	)
}

@Composable
fun SegmentedListItem(
	modifier: Modifier = Modifier,
	onClick: () -> Unit,
	shapes: ListItemShapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 1),
	enabled: Boolean = true,
	leadingContent: @Composable (() -> Unit)? = null,
	trailingContent: @Composable (() -> Unit)? = null,
	overlineContent: @Composable (() -> Unit)? = null,
	supportingContent: @Composable (() -> Unit)? = null,
	verticalAlignment: Alignment.Vertical = SegmentedListItemDefaults.verticalAlignment(),
	onLongClick: (() -> Unit)? = null,
	onLongClickLabel: String? = null,
	colors: ListItemColors = SegmentedListItemDefaults.segmentedColors(),
	elevation: ListItemElevation = ListItemDefaults.elevation(),
	contentPadding: PaddingValues = SegmentedListItemDefaults.contentPadding(),
	interactionSource: MutableInteractionSource? = null,
	content: @Composable () -> Unit,
) {
	androidx.compose.material3.SegmentedListItem(
		modifier = modifier,
		content = content,
		leadingContent = leadingContent,
		trailingContent = trailingContent,
		overlineContent = overlineContent,
		supportingContent = supportingContent,
		verticalAlignment = verticalAlignment,
		enabled = enabled,
		onClick = onClick,
		onLongClick = onLongClick,
		onLongClickLabel = onLongClickLabel,
		interactionSource = interactionSource,
		colors = colors,
		shapes = shapes,
		elevation = elevation,
		contentPadding = contentPadding,
	)
}

@Composable
fun SegmentedListItem(
	modifier: Modifier = Modifier,
	selected: Boolean,
	onClick: () -> Unit,
	shapes: ListItemShapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 1),
	enabled: Boolean = true,
	leadingContent: @Composable (() -> Unit)? = null,
	trailingContent: @Composable (() -> Unit)? = null,
	overlineContent: @Composable (() -> Unit)? = null,
	supportingContent: @Composable (() -> Unit)? = null,
	verticalAlignment: Alignment.Vertical = SegmentedListItemDefaults.verticalAlignment(),
	onLongClick: (() -> Unit)? = null,
	onLongClickLabel: String? = null,
	colors: ListItemColors = SegmentedListItemDefaults.segmentedColors(),
	elevation: ListItemElevation = ListItemDefaults.elevation(),
	contentPadding: PaddingValues = SegmentedListItemDefaults.contentPadding(),
	interactionSource: MutableInteractionSource? = null,
	content: @Composable () -> Unit,
) {
	val colors = if (selected) {
		colors.copy(
			containerColor = colors.selectedContainerColor,
			contentColor = colors.selectedContentColor,
			leadingContentColor = colors.selectedLeadingContentColor,
			trailingContentColor = colors.selectedTrailingContentColor,
			overlineContentColor = colors.selectedOverlineContentColor,
			supportingContentColor = colors.selectedSupportingContentColor
		)
	} else {
		colors
	}

	androidx.compose.material3.SegmentedListItem(
		modifier = modifier.semantics {
			this@semantics.selected = selected
		},
		content = content,
		leadingContent = leadingContent,
		trailingContent = trailingContent,
		overlineContent = overlineContent,
		supportingContent = supportingContent,
		verticalAlignment = verticalAlignment,
		enabled = enabled,
		selected = false,
		onClick = onClick,
		onLongClick = onLongClick,
		onLongClickLabel = onLongClickLabel,
		interactionSource = interactionSource,
		colors = colors,
		shapes = shapes,
		elevation = elevation,
		contentPadding = contentPadding,
	)
}

object SegmentedListItemDefaults {
	@Composable
	fun segmentedGap(): Dp {
		val preferenceManager = koinInject<PreferenceManager>()
		return remember(preferenceManager.themeStyle) {
			when (preferenceManager.themeStyle) {
				ThemeStyle.Material -> 3.dp
				else -> 0.dp
			}
		}
	}

	@Composable
	fun contentPadding(): PaddingValues {
		val preferenceManager = koinInject<PreferenceManager>()
		return remember(preferenceManager.themeStyle) {
			when (preferenceManager.themeStyle) {
				ThemeStyle.OneUI -> PaddingValues(horizontal = 18.dp, vertical = 14.dp)
				else -> PaddingValues(horizontal = 14.dp, vertical = 14.dp)
			}
		}
	}

	@Composable
	private fun overrideShape(): RoundedCornerShape {
		val preferenceManager = koinInject<PreferenceManager>()
		return remember(preferenceManager.themeStyle) {
			when (preferenceManager.themeStyle) {
				ThemeStyle.OneUI -> RoundedCornerShape(32.dp)
				else -> RoundedCornerShape(18.dp)
			}
		}
	}

	@Composable
	fun verticalAlignment() = Alignment.CenterVertically

	@Composable
	fun segmentedColors(
		// default
		containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
		contentColor: Color = MaterialTheme.colorScheme.onSurface,
		leadingContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
		trailingContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
		overlineContentColor: Color = MaterialTheme.colorScheme.primary,
		supportingContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,

		// disabled
		disabledContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
		disabledContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
		disabledLeadingContentColor: Color = leadingContentColor,
		disabledTrailingContentColor: Color = trailingContentColor,
		disabledOverlineContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
		disabledSupportingContentColor: Color = supportingContentColor,

		// selected
		selectedContainerColor: Color = containerColor,
		selectedContentColor: Color = contentColor,
		selectedLeadingContentColor: Color = leadingContentColor,
		selectedTrailingContentColor: Color = trailingContentColor,
		selectedOverlineContentColor: Color = overlineContentColor,
		selectedSupportingContentColor: Color = supportingContentColor,

		// dragged
		draggedContainerColor: Color = containerColor,
		draggedContentColor: Color = contentColor,
		draggedLeadingContentColor: Color = leadingContentColor,
		draggedTrailingContentColor: Color = trailingContentColor,
		draggedOverlineContentColor: Color = overlineContentColor,
		draggedSupportingContentColor: Color = supportingContentColor
	) = ListItemDefaults.segmentedColors(
		// default
		containerColor = containerColor,
		contentColor = contentColor,
		leadingContentColor = leadingContentColor,
		trailingContentColor = trailingContentColor,
		overlineContentColor = overlineContentColor,
		supportingContentColor = supportingContentColor,

		// disabled
		disabledContainerColor = disabledContainerColor,
		disabledContentColor = disabledContentColor,
		disabledLeadingContentColor = disabledLeadingContentColor,
		disabledTrailingContentColor = disabledTrailingContentColor,
		disabledOverlineContentColor = disabledOverlineContentColor,
		disabledSupportingContentColor = disabledSupportingContentColor,

		// selected
		selectedContainerColor = selectedContainerColor,
		selectedContentColor = selectedContentColor,
		selectedLeadingContentColor = selectedLeadingContentColor,
		selectedTrailingContentColor = selectedTrailingContentColor,
		selectedOverlineContentColor = selectedOverlineContentColor,
		selectedSupportingContentColor = selectedSupportingContentColor,

		// dragged
		draggedContainerColor = draggedContainerColor,
		draggedContentColor = draggedContentColor,
		draggedLeadingContentColor = draggedLeadingContentColor,
		draggedTrailingContentColor = draggedTrailingContentColor,
		draggedOverlineContentColor = draggedOverlineContentColor,
		draggedSupportingContentColor = draggedSupportingContentColor
	)

	@Composable
	fun segmentedErrorColors() = segmentedColors(
		contentColor = MaterialTheme.colorScheme.error,
		supportingContentColor = MaterialTheme.colorScheme.error
	)

	@Composable
	fun segmentedShapes() = segmentedShapes(index = 0, count = 1)

	@Composable
	fun segmentedShapes(
		index: Int,
		count: Int,
		defaultShapes: ListItemShapes = ListItemDefaults.shapes(),
		dismissDirection: SwipeToDismissBoxValue? = null
	): ListItemShapes {
		val overrideShape = overrideShape()
		return remember(index, count, defaultShapes, overrideShape, dismissDirection) {
			when {
				count == 1 || (dismissDirection != SwipeToDismissBoxValue.Settled && dismissDirection != null) -> {
					defaultShapes.copy(
						shape = overrideShape
					)
				}

				index == 0 -> {
					val defaultBaseShape = defaultShapes.shape
					if (defaultBaseShape is CornerBasedShape) {
						defaultShapes.copy(
							shape =
								defaultBaseShape.copy(
									topStart = overrideShape.topStart,
									topEnd = overrideShape.topEnd,
								)
						)
					} else {
						defaultShapes
					}
				}

				index == count - 1 -> {
					val defaultBaseShape = defaultShapes.shape
					if (defaultBaseShape is CornerBasedShape) {
						defaultShapes.copy(
							shape =
								defaultBaseShape.copy(
									bottomStart = overrideShape.bottomStart,
									bottomEnd = overrideShape.bottomEnd,
								)
						)
					} else {
						defaultShapes
					}
				}

				else -> defaultShapes
			}
		}
	}
}
