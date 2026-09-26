plugins {
    id("meritscreen.android.application")
    id("meritscreen.android.compose")
    id("meritscreen.android.hilt")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

import java.util.Properties

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}
val appCheckDebugToken: String = sequenceOf(
    localProperties.getProperty("appCheckDebugToken"),
    project.findProperty("appCheckDebugToken") as? String,
).mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.firstOrNull().orEmpty()
val googleWebClientId: String = sequenceOf(
    localProperties.getProperty("googleWebClientId"),
    project.findProperty("googleWebClientId") as? String,
).mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.firstOrNull().orEmpty()

android {
    namespace = "com.meritscreen.app"
    defaultConfig {
        applicationId = "com.watching.app"
        // Closed tester wave (10+ sideload installs) — bump when shipping a new APK.
        versionCode = 2
        versionName = "0.2.0"
        // Demo / local backend: ./gradlew :app:installDebug -PuseFirebaseEmulators
        // Release / internal never connect to emulator hosts.
        buildConfigField(
            "boolean",
            "USE_FIREBASE_EMULATORS",
            (project.findProperty("useFirebaseEmulators") == "true").toString(),
        )
        // Optional stable App Check debug token from local.properties or -PappCheckDebugToken=
        // Register the same UUID in Firebase Console → App Check → Manage debug tokens.
        buildConfigField(
            "String",
            "APP_CHECK_DEBUG_TOKEN",
            "\"${appCheckDebugToken.replace("\\", "\\\\").replace("\"", "\\\"")}\"",
        )
        // Play Integrity often fails on sideloaded APKs; internal builds use a registered
        // debug token so Firebase AI Logic + callables still work for closed testers.
        buildConfigField("boolean", "USE_APP_CHECK_DEBUG_PROVIDER", "false")
        if (googleWebClientId.isNotEmpty()) {
            resValue("string", "default_web_client_id", googleWebClientId)
        }
        // Real phones only — drops x86/emulator ABIs for a smaller sideload APK.
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }
    signingConfigs {
        create("release") {
            val storeFilePath = keystoreProperties.getProperty("storeFile")
                ?: System.getenv("KEYSTORE_FILE")
            val keystoreFile = if (!storeFilePath.isNullOrBlank()) {
                val f = file(storeFilePath)
                if (f.exists()) f else rootProject.file(storeFilePath)
            } else {
                null
            }
            if (keystoreFile != null && keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = keystoreProperties.getProperty("storePassword")
                    ?: System.getenv("KEYSTORE_PASSWORD")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                    ?: System.getenv("KEY_ALIAS")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                    ?: System.getenv("KEY_PASSWORD")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }
    buildTypes {
        getByName("release") {
            val releaseSigning = signingConfigs.findByName("release")
            if (releaseSigning?.storeFile != null) {
                signingConfig = releaseSigning
            }
        }
        // Minified + release-signed + production Firebase, App Check debug provider for
        // closed sideload testing (Firebase AI / OTP / callables). Not for Play Store.
        create("internal") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            versionNameSuffix = "-internal"
            buildConfigField("boolean", "USE_APP_CHECK_DEBUG_PROVIDER", "true")
            val releaseSigning = signingConfigs.findByName("release")
            if (releaseSigning?.storeFile != null) {
                signingConfig = releaseSigning
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/*.kotlin_module",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
            )
        }
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:ui"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(project(":core:firebase"))
    implementation(project(":core:security"))
    implementation(project(":core:analytics"))

    implementation(project(":features:onboarding"))
    implementation(project(":features:authentication"))
    implementation(project(":features:parent"))
    implementation(project(":features:child"))
    implementation(project(":features:children"))
    implementation(project(":features:launcher"))
    implementation(project(":features:applications"))
    implementation(project(":features:screen-time"))
    implementation(project(":features:settings"))
    implementation(project(":features:devices"))
    implementation(project(":features:notifications"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber)

    implementation(libs.androidx.profileinstaller)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.appcheck)
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)
    // Closed-tester APK only — keeps Firebase AI Logic App Check working when sideloaded.
    add("internalImplementation", libs.firebase.appcheck.debug.get())
}
