/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.collection.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import paige.navic.R
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Album
import paige.navic.ui.component.common.CoverArt

@Composable
fun DiscTitleHeading(
	disc: Int,
	coverArtId: String?,
	title: String?
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp)
			.padding(
				top = if (disc == 1) 0.dp else 12.dp,
				bottom = 4.dp
			)
			.heightIn(min = 32.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		if (!coverArtId.isNullOrBlank()) {
			CoverArt(
				coverArtId = coverArtId,
				shape = MaterialTheme.shapes.extraSmall,
				modifier = Modifier.size(32.dp)
			)
		} else {
			Icon(
				imageVector = Icons.Outlined.Album,
				contentDescription = null,
				tint = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.size(20.dp)
			)
		}

		Spacer(modifier = Modifier.width(8.dp))

		Text(
			text = title?.takeIf { it.isNotBlank() }
				?: stringResource(R.string.title_disc_number, disc),
			style = MaterialTheme.typography.titleMediumEmphasized,
			fontWeight = FontWeight(600),
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
	}
}
