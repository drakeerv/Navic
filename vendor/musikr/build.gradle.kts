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

	testImplementation("junit:junit:4.13.2")
	testImplementation("io.mockk:mockk:1.14.12")
	testImplementation("org.robolectric:robolectric:4.18")
	testImplementation(libs.androidx.test.core.ktx)
	testImplementation(libs.kotlinx.coroutines.test)
	androidTestImplementation(libs.androidx.test.ext.junit)
	androidTestImplementation(libs.androidx.test.espresso.core)
}

val assembleTaglib = tasks.register<Exec>("assembleTaglib") {
	val jniDir = layout.projectDirectory.dir("src/main/cpp").asFile
	val packageDir = jniDir.resolve("taglib/pkg")
	if (packageDir.exists()) {
		commandLine("true")
	} else {
		commandLine("sh", jniDir.resolve("build_taglib.sh").absolutePath, jniDir.absolutePath, androidComponents.sdkComponents.ndkDirectory.get().asFile.absolutePath)
	}
}

tasks.matching { it.name == "preDebugBuild" || it.name == "preReleaseBuild" }.configureEach {
	dependsOn(assembleTaglib)
}

tasks.named("clean") {
	delete("src/main/cpp/taglib/pkg", "src/main/cpp/taglib/build")
}
