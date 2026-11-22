/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.util.network

import android.content.Context
import okhttp3.Interceptor

interface CronetSupport {
	fun install(context: Context)
	fun createInterceptor(context: Context): Interceptor?
}
