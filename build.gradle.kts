plugins {
	alias(libs.plugins.ksp) apply false
	alias(libs.plugins.room) apply false
}

buildscript {
	repositories {
		mavenCentral()
		google()
	}
	dependencies {
		classpath(libs.android.gradlePlugin)
		classpath(libs.kotlin.gradlePlugin)
	}
}

allprojects {
	repositories {
		mavenCentral()
		google()
	}
}
