plugins {
	alias(libs.plugins.ksp) apply false
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
