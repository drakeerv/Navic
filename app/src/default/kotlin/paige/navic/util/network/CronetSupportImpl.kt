/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.util.network

import android.content.Context
import com.google.android.gms.net.CronetProviderInstaller
import com.google.net.cronet.okhttptransport.CronetInterceptor
import okhttp3.Interceptor
import org.chromium.net.CronetEngine

class CronetSupportImpl : CronetSupport {
	override fun install(context: Context) {
		CronetProviderInstaller.installProvider(context)
	}

	override fun createInterceptor(context: Context): Interceptor? = runCatching {
		CronetInterceptor
			.newBuilder(
				CronetEngine
					.Builder(context)
					.enableQuic(true)
					.build()
			)
			.build()
	}.getOrNull()
}
