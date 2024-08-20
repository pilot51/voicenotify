plugins {
	id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
}

buildscript {
	repositories {
		mavenCentral()
		google()
	}
	dependencies {
		classpath("com.android.tools.build:gradle:8.4.1")
		classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.24")
	}
}

allprojects {
	repositories {
		mavenCentral()
		google()
	}
}
