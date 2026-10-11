/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

// this is literally just to use ContinuousCapsule for indicator shape :trol:

@file:Suppress("INVISIBLE_REFERENCE")

package paige.navic.ui.component.common

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AnimatedNavigationItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationItemColors
import androidx.compose.material3.NavigationItemIconPosition
import androidx.compose.material3.WNRItemHorizontalPadding
import androidx.compose.material3.WNRItemNoLabelIndicatorPadding
import androidx.compose.material3.WideNavigationRailItemDefaults
import androidx.compose.material3.tokens.NavigationRailHorizontalItemTokens
import androidx.compose.material3.tokens.NavigationRailVerticalItemTokens
import androidx.compose.material3.value
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.kyant.capsule.ContinuousCapsule

@Composable
fun WideNavigationRailItem(
	selected: Boolean,
	onClick: () -> Unit,
	icon: @Composable () -> Unit,
	label: @Composable (() -> Unit)?,
	railExpanded: Boolean,
	modifier: Modifier = Modifier,
	enabled: Boolean = true,
	iconPosition: NavigationItemIconPosition =
		WideNavigationRailItemDefaults.iconPositionFor(railExpanded),
	colors: NavigationItemColors = WideNavigationRailItemDefaults.colors(),
	interactionSource: MutableInteractionSource? = null,
	indicatorPadding: PaddingValues =
		WideNavigationRailItemDefaults.indicatorPadding(railExpanded = railExpanded),
	/**
	 * When the rail is expanded, stretch the item and its indicator across the rail so the selected
	 * tab lines up with the header (now playing) instead of leaving a pill the size of the icon and
	 * label with empty space beside it. The collapsed rail keeps the stock Material layout.
	 */
	fillExpandedWidth: Boolean = false,
) {
	@Suppress("NAME_SHADOWING")
	val interactionSource = interactionSource ?: remember { MutableInteractionSource() }

	val layoutDirection = LocalLayoutDirection.current
	val motionSpec: FiniteAnimationSpec<Float> = MaterialTheme.motionScheme.defaultSpatialSpec()

	// Animated rather than switched on `railExpanded` so these values morph together with the rail's
	// own expand/collapse animation instead of snapping at its start.
	val fill by animateFloatAsState(
		targetValue = if (fillExpandedWidth && railExpanded) 1f else 0f,
		animationSpec = motionSpec,
	)

	// The item layout places both the indicator and the icon at `itemHorizontalPadding`, and sizes
	// the indicator as (icon + label + horizontal indicator padding) clamped to the item's width.
	//
	// Those formulas are what let this fill the rail: the indicator is placed at
	// `itemHorizontalPadding` while its ripple is placed at
	// `(itemHorizontalPadding + itemWidth - indicatorWidth) / 2`. Those only agree when the item is
	// not clamped, i.e. when `indicatorWidth + itemHorizontalPadding <= itemWidth`. The item's
	// maximum width is the rail's width minus 20dp, so with a 220dp rail the indicator has to come
	// out at 200 - 24 = 176dp, which is what the three values below add up to.
	val itemHorizontalPadding = lerp(WNRItemHorizontalPadding, ExpandedItemHorizontalPadding, fill)
	val indicatorStartPadding =
		lerp(indicatorPadding.calculateStartPadding(layoutDirection), ExpandedIndicatorLeadingPadding, fill)
	val indicatorEndPadding =
		lerp(indicatorPadding.calculateEndPadding(layoutDirection), ExpandedIndicatorTrailingPadding, fill)
	val effectiveIndicatorPadding =
		PaddingValues(
			start = indicatorStartPadding,
			end = indicatorEndPadding,
			top = indicatorPadding.calculateTopPadding(),
			bottom = indicatorPadding.calculateBottomPadding(),
		)

	// The expanded layout is sized from the label, so pinning every label to the same slot keeps
	// every item and indicator exactly the same width. The alignment flips with the animation phase
	// because the layout draws the label centred under the icon while collapsed and left aligned
	// beside the icon while expanded; the switch happens where Material cross fades the label out
	// (progress 0.5), so it isn't visible.
	val sizedLabel: (@Composable () -> Unit)? = label?.let { content ->
		{
			Box(
				modifier = Modifier.width(RailLabelWidth),
				contentAlignment = if (fill < 0.5f) Alignment.Center else Alignment.CenterStart,
			) {
				content()
			}
		}
	}

	AnimatedNavigationItem(
		selected = selected,
		onClick = onClick,
		icon = icon,
		indicatorShape = ContinuousCapsule,
		topIconIndicatorWidth = NavigationRailVerticalItemTokens.ActiveIndicatorWidth,
		topIconLabelTextStyle = NavigationRailVerticalItemTokens.LabelTextFont.value,
		startIconLabelTextStyle = NavigationRailHorizontalItemTokens.LabelTextFont.value,
		indicatorPadding = effectiveIndicatorPadding,
		topIconIndicatorToLabelVerticalPadding = NavigationRailVerticalItemTokens.IconLabelSpace,
		noLabelIndicatorPadding = WNRItemNoLabelIndicatorPadding,
		startIconToLabelHorizontalPadding = NavigationRailHorizontalItemTokens.IconLabelSpace,
		itemHorizontalPadding = itemHorizontalPadding,
		textColor =
			colors.textColor(selected, enabled, iconPosition == NavigationItemIconPosition.Top),
		iconColor = colors.iconColor(selected, enabled),
		indicatorColor = colors.selectedIndicatorColor,
		modifier = modifier,
		enabled = enabled,
		label = sizedLabel,
		iconPosition = iconPosition,
		interactionSource = interactionSource,
	)
}

private val RailLabelWidth = 80.dp

// Chosen so the expanded indicator lands on 176dp:
// icon (24) + icon-to-label space (8) + label (80) + leading (8) + trailing (56) = 176
private val ExpandedItemHorizontalPadding = 24.dp
private val ExpandedIndicatorLeadingPadding = 8.dp
private val ExpandedIndicatorTrailingPadding = 56.dp
