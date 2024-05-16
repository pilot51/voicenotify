plugins {
	id("com.google.devtools.ksp") version "1.9.21-1.0.15" apply false
    id("org.jetbrains.kotlin.android") version "1.9.0" apply false
}

buildscript {
	repositories {
		mavenCentral()
		google()
	}
	dependencies {
		classpath("com.android.tools.build:gradle:8.4.0")
		classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.21")
	}
}

allprojects {
	repositories {
		mavenCentral()
		google()
	}
}
