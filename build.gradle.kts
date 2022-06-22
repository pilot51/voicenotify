// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
	repositories {
		mavenCentral()
		google()
	}
	dependencies {
		classpath("com.android.tools.build:gradle:7.2.1")
		classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.7.0")
		classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.4.2")
	}
}

allprojects {
	repositories {
		mavenCentral()
		google()
	}
}
