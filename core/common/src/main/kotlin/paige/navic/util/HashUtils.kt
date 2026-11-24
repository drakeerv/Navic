/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.util

fun String.xxHash32(): String =
	xxHash32(toByteArray(Charsets.UTF_8)).toUInt().toString(16).padStart(8, '0')

private const val PRIME1 = -1640531535 // 0x9E3779B1
private const val PRIME2 = -2048144777 // 0x85EBCA77
private const val PRIME3 = -1028477379 // 0xC2B2AE3D
private const val PRIME4 = 668265263 // 0x27D4EB2F
private const val PRIME5 = 374761393 // 0x165667B1

private fun Int.round(input: Int): Int =
	(this + input * PRIME2).rotateLeft(13) * PRIME1

private fun ByteArray.intAt(offset: Int): Int =
	(this[offset].toInt() and 0xff) or
		((this[offset + 1].toInt() and 0xff) shl 8) or
		((this[offset + 2].toInt() and 0xff) shl 16) or
		((this[offset + 3].toInt() and 0xff) shl 24)

private fun xxHash32(data: ByteArray): Int {
	var index = 0
	var hash: Int

	if (data.size >= 16) {
		var v1 = PRIME1 + PRIME2
		var v2 = PRIME2
		var v3 = 0
		var v4 = -PRIME1
		val limit = data.size - 16
		while (index <= limit) {
			v1 = v1.round(data.intAt(index))
			v2 = v2.round(data.intAt(index + 4))
			v3 = v3.round(data.intAt(index + 8))
			v4 = v4.round(data.intAt(index + 12))
			index += 16
		}
		hash = v1.rotateLeft(1) + v2.rotateLeft(7) + v3.rotateLeft(12) + v4.rotateLeft(18)
	} else {
		hash = PRIME5
	}

	hash += data.size

	while (index + 4 <= data.size) {
		hash += data.intAt(index) * PRIME3
		hash = hash.rotateLeft(17) * PRIME4
		index += 4
	}
	while (index < data.size) {
		hash += (data[index].toInt() and 0xff) * PRIME5
		hash = hash.rotateLeft(11) * PRIME1
		index++
	}

	hash = hash xor (hash ushr 15)
	hash *= PRIME2
	hash = hash xor (hash ushr 13)
	hash *= PRIME3
	return hash xor (hash ushr 16)
}
