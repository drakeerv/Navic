/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.model.snackbars

import android.content.Context
import androidx.annotation.StringRes

sealed class SnackBarEvent {
	data class Actionable(
		@StringRes val resource: Int,
		val args: List<Any> = emptyList(),
		@StringRes val actionLabelRes: Int? = null,
		val onAction: ((Context) -> Unit)? = null
	) : SnackBarEvent()
	data class Error(
		val exception: Exception
	) : SnackBarEvent()
}
