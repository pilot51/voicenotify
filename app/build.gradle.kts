/*
 * Copyright 2017-2022 Mark Injerd
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

import java.io.FileInputStream
import java.util.Properties

plugins {
	id("com.android.application")
	kotlin("android")
	kotlin("kapt")
	id("androidx.navigation.safeargs.kotlin")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
	keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
	compileSdk = 32
	defaultConfig {
		applicationId = "com.pilot51.voicenotify"
		minSdk = 18
		targetSdk = 32
		versionName = "1.2.2"
		versionCode = 26
		viewBinding { isEnabled = true }
		vectorDrawables.useSupportLibrary = true
		javaCompileOptions {
			annotationProcessorOptions {
				arguments += mapOf(
					"room.schemaLocation" to "$projectDir/schemas",
					"room.incremental" to "true",
					"room.expandProjection" to "true"
				)
			}
		}
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_1_8
		targetCompatibility = JavaVersion.VERSION_1_8
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
			isMinifyEnabled = false
			signingConfig = signingConfigs.getByName("release")
		}
		getByName("debug") {
			versionNameSuffix = "-debug"
		}
	}
}

dependencies {
	implementation("androidx.core:core-ktx:1.8.0")
	implementation("androidx.preference:preference-ktx:1.2.0")
	implementation("androidx.navigation:navigation-fragment-ktx:2.5.1")
	implementation("androidx.navigation:navigation-ui-ktx:2.5.1")
	implementation("androidx.room:room-ktx:2.5.0-alpha02")
	kapt("androidx.room:room-compiler:2.5.0-alpha02")
}
