/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.server.local.mappers

import paige.navic.database.entity.ArtistEntity
import paige.navic.database.entity.LocalScanArtistMapEntity
import paige.navic.database.entity.LocalScanArtistRoleMapEntity
import paige.navic.util.xxHash32

fun LocalScanArtistMapEntity.entityId() = displayName?.xxHash32() ?: ""

fun LocalScanArtistRoleMapEntity.entityId() = displayName?.xxHash32() ?: ""

fun LocalScanArtistMapEntity.toEntity(
	albumCount: Int,
	existing: ArtistEntity? = null
) = ArtistEntity(
	artistId = entityId(),
	name = displayName ?: "",
	albumCount = albumCount,
	coverArtId = null,
	artistImageUrl = null,
	starredAt = existing?.starredAt,
	userRating = existing?.userRating,
	sortName = sortName,
	musicBrainzId = null,
	lastFmUrl = null,
	roles = emptyList(),
	biography = null,
	similarArtistIds = emptyList()
)

fun LocalScanArtistRoleMapEntity.toEntity(
	albumCount: Int,
	existing: ArtistEntity? = null
) = ArtistEntity(
	artistId = entityId(),
	name = displayName ?: "",
	albumCount = albumCount,
	coverArtId = null,
	artistImageUrl = null,
	starredAt = existing?.starredAt,
	userRating = existing?.userRating,
	sortName = sortName,
	musicBrainzId = null,
	lastFmUrl = null,
	roles = emptyList(),
	biography = null,
	similarArtistIds = emptyList()
)
