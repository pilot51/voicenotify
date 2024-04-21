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
	id("com.android.application")
	kotlin("android")
	id("com.google.devtools.ksp")
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

	composeOptions {
		kotlinCompilerExtensionVersion = "1.5.7"
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
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
	implementation("androidx.core:core-ktx:1.13.0")
	implementation("androidx.activity:activity-compose:1.9.0")
	implementation("androidx.compose.material3:material3:1.3.0-alpha05")
	implementation("androidx.compose.material:material-icons-extended-android:1.6.6")
	implementation("androidx.compose.ui:ui-tooling-preview:1.6.6")
	debugImplementation("androidx.compose.ui:ui-tooling:1.6.6")
	implementation("androidx.navigation:navigation-compose:2.7.7")
	implementation("androidx.glance:glance-appwidget:1.0.0")
	implementation("androidx.preference:preference-ktx:1.2.1")
	implementation("androidx.room:room-ktx:2.6.1")
	ksp("androidx.room:room-compiler:2.6.1")
	implementation("com.google.accompanist:accompanist-permissions:0.32.0")
}
