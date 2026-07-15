import java.util.Properties

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
  id("org.jetbrains.kotlin.plugin.serialization")
  id("com.google.devtools.ksp")
}

val appVersion = file("../../VERSION").readText().trim()
val versionParts = appVersion.split(".").map { it.toInt() }
val appVersionCode = versionParts[0] * 10000 + versionParts[1] * 100 + versionParts[2]

android {
  namespace = "top.lvziwang.ntfyhub"
  compileSdk = 35

  defaultConfig {
    applicationId = "top.lvziwang.ntfyhub"
    minSdk = 28
    targetSdk = 35
    versionCode = appVersionCode
    versionName = appVersion

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    vectorDrawables {
      useSupportLibrary = true
    }
  }

  val signingPropertiesFile = rootProject.file("keystore/keystore.properties")
  if (signingPropertiesFile.exists()) {
    val signingProperties = Properties().apply {
      signingPropertiesFile.inputStream().use { load(it) }
    }
    signingConfigs {
      create("release") {
        storeFile = rootProject.file(signingProperties.getProperty("storeFile"))
        storePassword = signingProperties.getProperty("storePassword")
        keyAlias = signingProperties.getProperty("keyAlias")
        keyPassword = signingProperties.getProperty("keyPassword")
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      if (signingPropertiesFile.exists()) {
        signingConfig = signingConfigs.getByName("release")
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

  kotlinOptions {
    jvmTarget = "17"
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

dependencies {
  val composeBom = platform("androidx.compose:compose-bom:2025.01.01")

  implementation(composeBom)
  androidTestImplementation(composeBom)

  implementation("androidx.core:core-ktx:1.15.0")
  implementation("androidx.appcompat:appcompat:1.7.0")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
  implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
  implementation("androidx.activity:activity-compose:1.10.0")
  implementation("androidx.navigation:navigation-compose:2.8.5")
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-graphics")
  implementation("androidx.compose.ui:ui-tooling-preview")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.material3:material3-android:1.3.1")
  implementation("androidx.compose.material:material-icons-extended")
  implementation("androidx.compose.animation:animation")
  implementation("androidx.compose.foundation:foundation")

  implementation("androidx.datastore:datastore-preferences:1.1.1")
  implementation("androidx.room:room-runtime:2.6.1")
  implementation("androidx.room:room-ktx:2.6.1")
  ksp("androidx.room:room-compiler:2.6.1")

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
  implementation("com.squareup.retrofit2:retrofit:2.11.0")
  implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
  implementation("com.squareup.okhttp3:okhttp:4.12.0")

  debugImplementation("androidx.compose.ui:ui-tooling")
  debugImplementation("androidx.compose.ui:ui-test-manifest")

  testImplementation("junit:junit:4.13.2")
  androidTestImplementation("androidx.test.ext:junit:1.2.1")
  androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
  androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
