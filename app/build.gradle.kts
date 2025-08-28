plugins {
id("com.android.application")
id("org.jetbrains.kotlin.android")
}

android {
namespace = "com.sticker.app"
compileSdk = 34


defaultConfig {
    applicationId = "com.sticker.app"
    minSdk = 24
    targetSdk = 34
    versionCode = 1
    versionName = "1.0"

    // BuildConfig values from GitHub Secrets at build time
    buildConfigField("String", "FAL_KEY", "\"${System.getenv("FAL_KEY") ?: ""}\"")

    // You can override these with Secrets later if you want different models.
    buildConfigField("String", "PREVIEW_MODEL_SLUG", "\"${System.getenv("PREVIEW_MODEL_SLUG") ?: "fal-ai/flux-schnell"}\"")
    buildConfigField("String", "FINAL_MODEL_SLUG", "\"${System.getenv("FINAL_MODEL_SLUG") ?: "fal-ai/flux-pro"}\"")
}

buildTypes {
    debug { isMinifyEnabled = false }
    release { isMinifyEnabled = false }
}

buildFeatures {
    compose = true
    buildConfig = true
}

// Java/Kotlin 17
compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlinOptions { jvmTarget = "17" }

// Compose compiler compatible with Kotlin 1.9.24
composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

kotlin {
jvmToolchain(17)
}

dependencies {
val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
implementation(composeBom)
androidTestImplementation(composeBom)


implementation("androidx.activity:activity-compose:1.9.2")
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.ui:ui-tooling-preview")
debugImplementation("androidx.compose.ui:ui-tooling")
implementation("androidx.compose.material3:material3:1.3.0")
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
implementation("androidx.datastore:datastore-preferences:1.1.1")
implementation("androidx.webkit:webkit:1.11.0")

// Material XML theme
implementation("com.google.android.material:material:1.12.0")

// Networking
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("com.squareup.retrofit2:retrofit:2.11.0")
implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
}
