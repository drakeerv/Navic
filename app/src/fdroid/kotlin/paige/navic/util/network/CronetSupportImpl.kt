/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.util.network

import android.content.Context
import okhttp3.Interceptor

class CronetSupportImpl : CronetSupport {
	override fun install(context: Context) = Unit

	override fun createInterceptor(context: Context): Interceptor? = null
}
