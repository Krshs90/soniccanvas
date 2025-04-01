buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.9.1") // Use the latest version
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.20") // Use the latest version
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}