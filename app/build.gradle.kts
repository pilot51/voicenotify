/*
 * Copyright 2017-2023 Mark Injerd
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
import java.util.*

plugins {
	id("com.android.application")
	kotlin("android")
	id("com.google.devtools.ksp")
	id("androidx.navigation.safeargs.kotlin")
}

val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
	keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
	namespace = "com.pilot51.voicenotify"
	compileSdk = 33
	defaultConfig {
		applicationId = "com.pilot51.voicenotify"
		minSdk = 18
		targetSdk = 33
		versionName = "1.2.2"
		versionCode = 26
		vectorDrawables.useSupportLibrary = true
		ksp {
			arg("room.schemaLocation", "$projectDir/schemas")
		}
	}

	buildFeatures {
		viewBinding = true
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
			isMinifyEnabled = false
			signingConfig = signingConfigs.getByName("release")
		}
		getByName("debug") {
			versionNameSuffix = "-debug"
		}
	}
}

dependencies {
	implementation("androidx.core:core-ktx:1.10.1")
	implementation("androidx.preference:preference-ktx:1.2.1")
	implementation("androidx.navigation:navigation-fragment-ktx:2.6.0")
	implementation("androidx.navigation:navigation-ui-ktx:2.6.0")
	implementation("androidx.room:room-ktx:2.5.2")
	ksp("androidx.room:room-compiler:2.5.2")
}
