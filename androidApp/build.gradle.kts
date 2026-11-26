plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// CI passes VERSION_CODE (the workflow run number) so every published build installs as an update.
val ciVersionCode = System.getenv("VERSION_CODE")?.toIntOrNull()

// Release signing comes from environment variables (set as GitHub secrets in CI). Without them,
// release builds fall back to the debug key so local builds still work.
val releaseKeystore = System.getenv("TOWERSCOPE_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }

android {
    namespace = "io.towerscope.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.towerscope.android"
        minSdk = 29
        targetSdk = 36
        versionCode = ciVersionCode ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "0.9.${ciVersionCode ?: 0}"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("TOWERSCOPE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("TOWERSCOPE_KEY_ALIAS")
                keyPassword = System.getenv("TOWERSCOPE_KEY_PASSWORD")
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
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
}
