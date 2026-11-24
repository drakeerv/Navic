/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.update

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File
import java.net.URL
import java.util.zip.ZipFile

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
			ZipFile(raw).use { zip ->
				val entry = zip.entries().asSequence().firstOrNull {
					!it.isDirectory && it.name.endsWith(".apk", ignoreCase = true)
				} ?: error("no apk found in update archive")
				zip.getInputStream(entry).use { input ->
					apk.outputStream().use { output -> input.copyTo(output) }
				}
			}
			check(apk.length() > 0) { "extracted apk is empty" }
			return apk
		} finally {
			raw.delete()
		}
	}

	@SuppressLint("RequestInstallPackagesPolicy")
	fun install(context: Context, apk: File) {
		val installer = context.packageManager.packageInstaller
		val params = PackageInstaller.SessionParams(
			PackageInstaller.SessionParams.MODE_FULL_INSTALL
		).apply {
			setAppPackageName(context.packageName)

			// make updates silent on api 31+
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
				setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
			}
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
		val action = intent.action ?: return
		if (action != Intent.ACTION_MY_PACKAGE_REPLACED) return

		val status = intent.getIntExtra(
			PackageInstaller.EXTRA_STATUS,
			PackageInstaller.STATUS_FAILURE
		)

		when (status) {
			PackageInstaller.STATUS_PENDING_USER_ACTION -> {
				val confirmationIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
					intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
				} else {
					@Suppress("DEPRECATION")
					intent.getParcelableExtra(Intent.EXTRA_INTENT)
				}
				confirmationIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
				confirmationIntent?.let(context::startActivity)
			}

			PackageInstaller.STATUS_SUCCESS -> {
				val launchIntent = context.packageManager
					.getLaunchIntentForPackage(context.packageName)
					?.apply {
						addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
					} ?: return

				try {
					context.startActivity(launchIntent)
				} catch (_: Exception) {
					// blocked by the OS, maybe should post a notification instead?
				}
			}
		}
	}
}
