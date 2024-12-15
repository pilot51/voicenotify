/*
 * Copyright 2011-2024 Mark Injerd
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
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.util.*

plugins {
	kotlin("android")
	alias(libs.plugins.android.application)
	alias(libs.plugins.compose)
	alias(libs.plugins.ksp)
}

val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
	keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

val gitCommitHash by lazy {
	val stdout = ByteArrayOutputStream()
	exec {
		commandLine("git", "rev-parse", "--short", "HEAD")
		standardOutput = stdout
	}
	stdout.toString().trim()
}

android {
	namespace = "com.pilot51.voicenotify"
	compileSdk = 34
	defaultConfig {
		applicationId = "com.pilot51.voicenotify"
		minSdk = 21
		targetSdk = 34
		versionName = "1.4.0-beta"
		versionCode = 29
		vectorDrawables.useSupportLibrary = true
		ksp {
			arg("room.schemaLocation", "$projectDir/schemas")
		}
	}

	buildFeatures {
		viewBinding = true
	}

	buildFeatures {
		buildConfig = true
		compose = true
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_21
		targetCompatibility = JavaVersion.VERSION_21
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
			@Suppress("UnstableApiUsage")
			postprocessing {
				isRemoveUnusedCode = true
				isOptimizeCode = true
				isShrinkResources = true
				isObfuscate = false
			}
		}
		getByName("debug") {
			applicationIdSuffix = ".debug"
			versionNameSuffix = "-debug [$gitCommitHash]"
		}
	}

	androidResources {
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
	implementation(libs.accompanist.permissions)
	implementation(libs.androidx.activity.compose)
	implementation(libs.androidx.compose.material.iconsExtended)
	implementation(libs.androidx.compose.material3)
	implementation(libs.androidx.compose.ui.tooling.preview)
	implementation(libs.androidx.core.ktx)
	implementation(libs.androidx.glance.appwidget)
	implementation(libs.androidx.navigation.compose)
	implementation(libs.androidx.preference)
	implementation(libs.androidx.room.ktx)
	implementation(libs.kotlin.reflect)
	ksp(libs.androidx.room.compiler)
	debugImplementation(libs.androidx.compose.ui.tooling)
}
