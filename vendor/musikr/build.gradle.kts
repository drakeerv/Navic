/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

plugins {
	alias(libs.plugins.navic.library)
	alias(libs.plugins.ksp)
	id("org.jetbrains.kotlin.plugin.parcelize")
}

android {
	namespace = "org.oxycblt.musikr"
	ndkVersion = libs.versions.android.ndk.get()

	defaultConfig {
		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
		consumerProguardFiles("consumer-rules.pro")
		externalNativeBuild {
			cmake {
				cppFlags += ""
			}
		}
	}

	buildTypes {
		release {
			isMinifyEnabled = false
			proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
		}
	}

	externalNativeBuild {
		cmake {
			path = file("src/main/cpp/CMakeLists.txt")
			version = "3.22.1"
		}
	}

	buildFeatures.buildConfig = true
	lint.targetSdk = libs.versions.android.targetSdk.get().toInt()
	testOptions.targetSdk = libs.versions.android.targetSdk.get().toInt()
}

dependencies {
	implementation(libs.kotlinx.coroutines.core)
	implementation(libs.androidx.core.ktx)
	implementation(libs.androidx.room.runtime)
	implementation(libs.androidx.room.ktx)
	ksp(libs.androidx.room.compiler)

	testImplementation(libs.junit)
	testImplementation(libs.mockk)
	testImplementation(libs.robolectric)
	testImplementation(libs.androidx.test.core.ktx)
	testImplementation(libs.kotlinx.coroutines.test)
	androidTestImplementation(libs.androidx.test.ext.junit)
	androidTestImplementation(libs.androidx.test.espresso.core)
}

val assembleTaglib = tasks.register<Exec>("assembleTaglib") {
	description = "Build TagLib"
	val jniDir = layout.projectDirectory.dir("src/main/cpp").asFile
	val taglibDir = jniDir.resolve("taglib")
	val packageDir = taglibDir.resolve("pkg")

	inputs.dir(taglibDir).ignoreEmptyDirectories()
	inputs.files(
		jniDir.resolve("build_taglib.sh"),
		jniDir.resolve("android.toolchain.cmake")
	)
	outputs.dir(packageDir)
	outputs.dir(taglibDir.resolve("build"))

	outputs.cacheIf { true }

	commandLine(
		jniDir.resolve("build_taglib.sh").absolutePath,
		jniDir.absolutePath,
		androidComponents.sdkComponents.ndkDirectory.get().asFile.absolutePath
	)
}

tasks.matching { it.name == "preDebugBuild" || it.name == "preReleaseBuild" }.configureEach {
	dependsOn(assembleTaglib)
}

tasks.named("clean") {
	delete("src/main/cpp/taglib/pkg", "src/main/cpp/taglib/build")
}
