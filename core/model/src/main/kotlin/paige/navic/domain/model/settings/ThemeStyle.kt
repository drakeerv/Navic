/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.model.settings

enum class ThemeStyle {
	Material,
	OneUI,
	iOS;

	companion object {
		val Default = ThemeStyle.Material
	}
}
