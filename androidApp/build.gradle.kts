import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// CI passes VERSION_CODE (the workflow run number) so every published build installs as an update.
val ciVersionCode = System.getenv("VERSION_CODE")?.toIntOrNull()

// Release signing. Environment variables win (CI sets them from GitHub secrets). Otherwise a gitignored
// keystore.properties in the repo root is used (see keystore.properties.example), or the file named by
// TOWERSCOPE_KEYSTORE_PROPERTIES. With neither, release builds fall back to the debug key so local
// builds still work; such builds can't update an installed release.
val keystoreProps = Properties().apply {
    val f = System.getenv("TOWERSCOPE_KEYSTORE_PROPERTIES")?.let { file(it) } ?: rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(env: String, prop: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(prop)?.takeIf { it.isNotBlank() }
val releaseKeystore = signingValue("TOWERSCOPE_KEYSTORE", "storeFile")?.let { file(it) }?.takeIf { it.exists() }

android {
    namespace = "io.towerscope.android"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
        targetSdk = 36
        versionCode = ciVersionCode ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "0.9.${ciVersionCode ?: 0}"
    }

    // Two editions from one codebase. Both can be installed on the same phone.
    flavorDimensions += "edition"
    productFlavors {
        // Google Play: precise location only while the app is open, no long-term history or export,
        // a 7-day trial and then a one-time unlock.
        create("play") {
            dimension = "edition"
            applicationId = "io.towerscope.live"
            buildConfigField("boolean", "IS_PLAY_EDITION", "true")
            buildConfigField("boolean", "ALLOW_PRECISE_LOCATION", "true")
            buildConfigField("boolean", "ALLOW_BACKGROUND_LOCATION", "false")
            buildConfigField("boolean", "ALLOW_HISTORY_LOGGING", "false")
            buildConfigField("boolean", "ALLOW_HISTORY_EXPORT", "false")
            buildConfigField("boolean", "ALLOW_PLAY_BILLING", "true")
        }
        // Open source / sideload: every feature, free. Keeps the original id so existing installs update in place.
        create("foss") {
            dimension = "edition"
            applicationId = "io.towerscope.android"
            buildConfigField("boolean", "IS_PLAY_EDITION", "false")
            buildConfigField("boolean", "ALLOW_PRECISE_LOCATION", "true")
            buildConfigField("boolean", "ALLOW_BACKGROUND_LOCATION", "true")
            buildConfigField("boolean", "ALLOW_HISTORY_LOGGING", "true")
            buildConfigField("boolean", "ALLOW_HISTORY_EXPORT", "true")
            buildConfigField("boolean", "ALLOW_PLAY_BILLING", "false")
        }
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = signingValue("TOWERSCOPE_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("TOWERSCOPE_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("TOWERSCOPE_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    // Google Play Billing ships only in the Play edition.
    "playImplementation"(libs.billing.ktx)
    // Billing pulls in Fragment 1.1, which predates the activity-result API MainActivity uses.
    "playImplementation"(libs.androidx.fragment)
}
