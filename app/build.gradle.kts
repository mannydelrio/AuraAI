/*
 * Aura — an always-on, provider-agnostic AI assistant overlay for Meta Portal.
 */
import java.io.FileInputStream
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

// Release signing — credentials live in keystore.properties (gitignored), so the repo has no secrets.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps =
    Properties().apply {
      if (keystorePropsFile.exists()) FileInputStream(keystorePropsFile).use { load(it) }
    }

android {
  namespace = "com.aura.assistant"
  // Compile against the newest installed platform; runtime behavior is pinned by targetSdk below.
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aura.assistant"
    // Portal compatibility: Meta documents minSdk 28 / targetSdk 29 (older AOSP, no GMS).
    minSdk = 28
    targetSdk = 29
    versionCode = 3
    versionName = "0.1.2"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      if (keystorePropsFile.exists()) {
        storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
        storePassword = keystoreProps.getProperty("storePassword")
        keyAlias = keystoreProps.getProperty("keyAlias")
        keyPassword = keystoreProps.getProperty("keyPassword")
      }
    }
  }

  buildTypes {
    release {
      if (keystorePropsFile.exists()) signingConfig = signingConfigs.getByName("release")
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures { compose = true }

  lint {
    // Aura is sideloaded onto Meta Portal (not distributed via Google Play), so the Play Store
    // "target API 33+" requirement does not apply — targetSdk 29 is intentional for Portal.
    disable += "ExpiredTargetSdkVersion"
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.okhttp)
  testImplementation(libs.junit)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
