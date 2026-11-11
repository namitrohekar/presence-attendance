plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.navigation.safeargs)
    alias(libs.plugins.spotless)
}

// The project must build, lint and unit-test without Firebase credentials; only runtime needs them.
val firebaseConfigured = file("google-services.json").exists()
if (firebaseConfigured) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

android {
    namespace = "com.presence.attendance"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.presence.attendance"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        buildConfigField("boolean", "FIREBASE_CONFIGURED", firebaseConfigured.toString())
    }

    signingConfigs {
        // One debug key for the whole team, so a single SHA pair registered in Firebase works on every machine.
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    lint {
        warningsAsErrors = true
        abortOnError = true
        // These fire whenever a library publishes a release, which would break the build on demo day.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
        // shortcut: design tokens are defined before the screens that use them, re-enable in Phase 7.
        disable += "UnusedResources"
    }
}

tasks.withType<JavaCompile>().configureEach {
    // dangling-doc-comments is excluded because AGP's generated BuildConfig.java trips it.
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-dangling-doc-comments", "-Werror"))
}

spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat(libs.versions.googleJavaFormat.get()).aosp()
        removeUnusedImports()
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.dynamicanimation)
    implementation(libs.material)
}
