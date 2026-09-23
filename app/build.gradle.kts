plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlinx.serialization)
}

import java.util.Properties

// Play Store upload key. Lives outside version control in keystore.properties (see
// keystore.properties.example) or in HANGRY_* environment variables for CI.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? =
    keystoreProperties.getProperty(key) ?: System.getenv(env)
val releaseStoreFile = signingValue("storeFile", "HANGRY_KEYSTORE_PATH")

android {
    namespace = "com.kevan.hangry"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kevan.hangry"
        minSdk = 28
        targetSdk = 35
        versionCode = 18
        versionName = "1.16.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = signingValue("storePassword", "HANGRY_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "HANGRY_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "HANGRY_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Without an upload key, release builds fall back to the debug key so sideloadable
            // APKs still build - Play rejects debug-signed uploads, so nothing ships by mistake.
            signingConfig = if (releaseStoreFile != null) {
                signingConfigs.getByName("release")
            } else {
                logger.warn("keystore.properties not found - release build is signed with the DEBUG key and can't be uploaded to Play.")
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Health Connect
    implementation(libs.androidx.health.connect)

    // AI integration: networking, image loading. The API key is encrypted with a raw
    // Android Keystore AES key instead of androidx.security:security-crypto, which Google
    // deprecated/stopped maintaining as of the 1.1.0 release.
    implementation(libs.okhttp)
    implementation(libs.coil.compose)

    // Unit Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.room.testing)

    // Android Instrumentation Testing
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.turbine)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}