/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.util

import org.junit.Assert.assertEquals
import org.junit.Test

class HashUtilsTest {
	@Test
	fun matchesReferenceXxHash32() {
		assertEquals("02cc5d05", "".xxHash32())
		assertEquals("550d7456", "a".xxHash32())
		assertEquals("32d153ff", "abc".xxHash32())
		assertEquals("cebb6622", "hello world".xxHash32())
		assertEquals("e85ea4de", "The quick brown fox jumps over the lazy dog".xxHash32())
		assertEquals("c8ae20bd", "日本語".xxHash32())

		val alnum = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
		assertEquals("1dbdfa0f", alnum.take(15).xxHash32())
		assertEquals("c2c45b69", alnum.take(16).xxHash32())
		assertEquals("cc79b217", alnum.take(17).xxHash32())
		assertEquals("a3944879", alnum.take(31).xxHash32())
		assertEquals("efb1272d", alnum.take(32).xxHash32())
		assertEquals("93234478", alnum.take(33).xxHash32())
	}
}
