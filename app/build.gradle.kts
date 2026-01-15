/*
 * Copyright 2011-2026 Mark Injerd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.io.FileInputStream
import java.util.*

plugins {
	kotlin("android")
	alias(libs.plugins.android.application)
	alias(libs.plugins.compose)
	alias(libs.plugins.ksp)
	alias(libs.plugins.room)
}

val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
	keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

val gitCommitHash by lazy {
	providers.exec {
		commandLine("git", "rev-parse", "--short", "HEAD")
	}.standardOutput.asText.get().trim()
}

android {
	namespace = "com.pilot51.voicenotify"
	compileSdk = 36
	defaultConfig {
		applicationId = "com.pilot51.voicenotify"
		minSdk = 23
		targetSdk = 36
		versionName = "1.4.5-beta"
		versionCode = 34
		vectorDrawables.useSupportLibrary = true
	}

	room {
		schemaDirectory("$projectDir/schemas")
	}

	buildFeatures {
		buildConfig = true
		compose = true
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_21
		targetCompatibility = JavaVersion.VERSION_21
		isCoreLibraryDesugaringEnabled = true
	}

	signingConfigs {
		create("release") {
			if (keystoreProperties.isNotEmpty()) {
				keyAlias = keystoreProperties.getProperty("keyAlias")
				keyPassword = keystoreProperties.getProperty("keyPassword")
				storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
				storePassword = keystoreProperties.getProperty("storePassword")
			}
		}
	}

	buildTypes {
		getByName("release") {
			signingConfig = signingConfigs.getByName("release")
			versionNameSuffix = " [$gitCommitHash]"
			isMinifyEnabled = true
			isShrinkResources = true
			proguardFiles(
				getDefaultProguardFile("proguard-android-optimize.txt")
			)
		}
		getByName("debug") {
			applicationIdSuffix = ".debug"
			versionNameSuffix = "-debug [$gitCommitHash]"
		}
	}

	androidResources {
		@Suppress("UnstableApiUsage")
		generateLocaleConfig = true
	}

	applicationVariants.all {
		outputs.all {
			this as BaseVariantOutputImpl
			outputFileName = "VoiceNotify_v${defaultConfig.versionName}-${name}_$gitCommitHash.apk"
		}
	}
}

dependencies {
	coreLibraryDesugaring(libs.android.desugar)
	implementation(libs.accompanist.permissions)
	implementation(libs.androidx.activity.compose)
	implementation(libs.androidx.compose.material.iconsExtended)
	implementation(libs.androidx.compose.material3)
	implementation(libs.androidx.compose.ui.tooling)
	implementation(libs.androidx.core.ktx)
	implementation(libs.androidx.glance.appwidget)
	implementation(libs.androidx.navigation.compose)
	implementation(libs.androidx.preference)
	implementation(libs.androidx.room.ktx)
	implementation(libs.kotlin.reflect)
	implementation(libs.autostarter)
	ksp(libs.androidx.room.compiler)
}
