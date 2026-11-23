/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.IntentCompat
import java.io.File
import java.net.URL
import java.util.zip.ZipInputStream

internal object UpdateInstaller {
	private const val DOWNLOAD_NAME = "navic-update.download"
	private const val APK_NAME = "navic-update.apk"

	fun download(context: Context, url: String): File {
		val raw = File(context.cacheDir, DOWNLOAD_NAME)
		URL(url).openStream().use { input ->
			raw.outputStream().use { output -> input.copyTo(output) }
		}
		// release assets are plain apks, ci artifacts are zips
		if (url.substringBefore('?').endsWith(".apk", ignoreCase = true)) return raw

		val apk = File(context.cacheDir, APK_NAME)
		try {
			ZipInputStream(raw.inputStream().buffered()).use { stream ->
				var entry = stream.nextEntry
				while (entry != null) {
					if (!entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)) {
						apk.outputStream().use { output -> stream.copyTo(output) }
						return apk
					}
					entry = stream.nextEntry
				}
			}
			error("no apk found in update archive")
		} finally {
			raw.delete()
		}
	}

	fun install(context: Context, apk: File) {
		val installer = context.packageManager.packageInstaller
		val params = PackageInstaller.SessionParams(
			PackageInstaller.SessionParams.MODE_FULL_INSTALL
		).apply {
			setAppPackageName(context.packageName)
		}
		val sessionId = installer.createSession(params)
		installer.openSession(sessionId).use { session ->
			apk.inputStream().use { input ->
				session.openWrite(APK_NAME, 0, apk.length()).use { output ->
					input.copyTo(output)
					session.fsync(output)
				}
			}
			val flags = PendingIntent.FLAG_UPDATE_CURRENT or
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
					PendingIntent.FLAG_MUTABLE
				} else {
					0
				}
			val callback = PendingIntent.getBroadcast(
				context,
				sessionId,
				Intent(context, UpdateInstallReceiver::class.java),
				flags
			)
			session.commit(callback.intentSender)
		}
	}
}

class UpdateInstallReceiver : BroadcastReceiver() {
	override fun onReceive(context: Context, intent: Intent) {
		val status = intent.getIntExtra(
			PackageInstaller.EXTRA_STATUS,
			PackageInstaller.STATUS_FAILURE
		)
		if (status != PackageInstaller.STATUS_PENDING_USER_ACTION) return
		val confirm = IntentCompat.getParcelableExtra(
			intent,
			Intent.EXTRA_INTENT,
			Intent::class.java
		) ?: return
		confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		context.startActivity(confirm)
	}
}
