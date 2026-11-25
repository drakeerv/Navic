/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.util

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale
import androidx.core.content.edit

private const val LOCALE_KEY = "app_locale"

private const val LEGACY_LOCALE_FILE =
	"androidx.appcompat.app.AppCompatDelegate.application_locales_record_file"

fun Context.appLocale(): String? = if (Build.VERSION.SDK_INT >= 33) {
	getSystemService(LocaleManager::class.java).applicationLocales.get(0)?.toLanguageTag()
} else {
	getSharedPreferences(preferencesFile(this), Context.MODE_PRIVATE)
		.getString(LOCALE_KEY, null) ?: legacyLocale()
}

fun setAppLocale(activity: Activity, tag: String?) {
	if (Build.VERSION.SDK_INT >= 33) {
		activity.getSystemService(LocaleManager::class.java).applicationLocales =
			if (tag == null) {
				LocaleList.getEmptyLocaleList()
			} else {
				LocaleList.forLanguageTags(tag)
			}
	} else {
		activity.getSharedPreferences(
			preferencesFile(activity),
			Context.MODE_PRIVATE
		).edit { putString(LOCALE_KEY, tag) }

		activity.deleteFile(LEGACY_LOCALE_FILE)
		activity.recreate()
	}
}

// pre-33 needs the locale applied to each context's own resources
fun Context.withAppLocale(): Context {
	if (Build.VERSION.SDK_INT >= 33) return this
	val tag = appLocale() ?: return this
	val configuration = Configuration(resources.configuration)
	configuration.setLocale(Locale.forLanguageTag(tag))
	return createConfigurationContext(configuration)
}

private fun Context.legacyLocale(): String? = runCatching {
	openFileInput(LEGACY_LOCALE_FILE).bufferedReader().use { it.readText() }
		.substringAfter("application_locales=\"", "")
		.substringBefore('"')
		.substringBefore(',')
		.ifEmpty { null }
}.getOrNull()
