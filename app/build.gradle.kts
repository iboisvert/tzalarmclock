import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("keystore-props")
}

val keystoreProperties: Properties? by extra

android {
    namespace = "imb.tzalarmclock"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "imb.tzalarmclock"
        minSdk = 31
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.0-beta2"

        // Formatted at configuration time so every build (and every variant)
        // gets a stamp for the moment it was compiled, shown on the Settings
        // page so a debugger can tell which build they're looking at.
        val buildTimestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC)
            .format(Instant.now())
        buildConfigField("String", "BUILD_TIMESTAMP", "\"$buildTimestamp\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Only present when the private `secrets` submodule is checked out (see
    // keystore-props.gradle.kts). A contributor without access to it can
    // still build/test everything except a signed release - assembleRelease
    // and bundleRelease are made to fail loudly for that case below, rather
    // than silently producing an unsigned APK.
    val nonNullKeystoreProperties = keystoreProperties
    if (nonNullKeystoreProperties != null) {
        signingConfigs {
            create("config") {
                keyAlias = nonNullKeystoreProperties["keyAlias"] as String
                keyPassword = nonNullKeystoreProperties["keyPassword"] as String
                storeFile = rootProject.file("secrets/${nonNullKeystoreProperties["storeFile"]}")
                storePassword = nonNullKeystoreProperties["storePassword"] as String
            }
        }
    }
    buildTypes {
        release {
            if (nonNullKeystoreProperties != null) {
                signingConfig = signingConfigs.getByName("config")
            }
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

if (keystoreProperties == null) {
    tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
        doFirst {
            throw GradleException(
                "Missing secrets/keystore.properties - a signed release build " +
                    "needs the private `secrets` submodule (run `git submodule " +
                    "update --init`). assembleDebug doesn't need it."
            )
        }
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":alarm"))
    implementation(project(":timer"))
    implementation(project(":ui"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}