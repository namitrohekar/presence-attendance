import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.navigation.safeargs)
    alias(libs.plugins.spotless)
}

// The project must build, lint and unit-test without Firebase credentials; only runtime needs them.
val googleServicesJson =
    providers.fileContents(layout.projectDirectory.file("google-services.json")).asText.orNull
val firebaseConfigured = googleServicesJson != null
if (firebaseConfigured) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

// Google sign-in needs the project's web OAuth client (client_type 3). It is present only after
// the Google provider is enabled and the SHA fingerprints are registered, so it can be missing
// even when the file exists. Reading it here avoids depending on a generated string resource.
fun webClientId(json: String?): String {
    if (json == null) return ""
    val root = groovy.json.JsonSlurper().parseText(json) as? Map<*, *> ?: return ""
    val clients = root["client"] as? List<*> ?: return ""
    return clients
        .flatMap { ((it as? Map<*, *>)?.get("oauth_client") as? List<*>).orEmpty() }
        .mapNotNull { it as? Map<*, *> }
        .firstOrNull { (it["client_type"] as? Number)?.toInt() == 3 }
        ?.get("client_id") as? String ?: ""
}

// Debug builds can talk to local Firebase emulators. The host comes from local.properties
// (firebase.emulator.host=10.0.2.2 for the Android emulator, or the computer's LAN address for
// a phone), so no address is committed. Empty means the real project.
val emulatorHost: String =
    providers.fileContents(rootProject.layout.projectDirectory.file("local.properties"))
        .asText
        .map { text ->
            Properties().apply { load(text.reader()) }
                .getProperty("firebase.emulator.host", "")
                .trim()
        }
        .getOrElse("")

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
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${webClientId(googleServicesJson)}\"")
        buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"\"")
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
        debug {
            buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"$emulatorHost\"")
        }
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
    testOptions {
        // Firebase model classes touch android.util in static initialisers; without this the
        // Android stubs throw and those classes cannot be loaded in local unit tests.
        unitTests.isReturnDefaultValues = true
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
    // Every javac warning is an error, except three categories that only generated code trips:
    // dangling-doc-comments (AGP's BuildConfig.java) and rawtypes/unchecked (the raw HashMap in
    // Safe Args' generated Args and Directions classes). javac cannot exempt a directory.
    options.compilerArgs.addAll(
        listOf(
            "-Xlint:all",
            "-Xlint:-dangling-doc-comments",
            "-Xlint:-rawtypes",
            "-Xlint:-unchecked",
            "-Werror",
        ),
    )
}

tasks.withType<Test>().configureEach {
    // The contract files live outside this module. Declaring them as inputs makes the parity
    // tests rerun when a contract changes instead of being reported up to date.
    val contracts = rootProject.layout.projectDirectory.dir("contracts")
    inputs.dir(contracts).withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("contractsDir", contracts.asFile.absolutePath)
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
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.dynamicanimation)
    implementation(libs.material)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.zxing.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.play.services.location)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.arch.core.testing)
}
