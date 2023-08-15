plugins {
	id("com.google.devtools.ksp") version "1.9.0-1.0.13" apply false
}

buildscript {
	repositories {
		mavenCentral()
		google()
	}
	dependencies {
		classpath("com.android.tools.build:gradle:8.1.0")
		classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.0")
		classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.7.0")
	}
}

allprojects {
	repositories {
		mavenCentral()
		google()
	}
}
