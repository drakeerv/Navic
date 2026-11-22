/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.util

import com.appmattus.crypto.Algorithm

fun String.xxHash32(): String {
	val digest = Algorithm.XXHash32().createDigest()
	return digest.digest(toByteArray(Charsets.UTF_8))
		.joinToString("") { byte -> "%02x".format(byte) }
}
