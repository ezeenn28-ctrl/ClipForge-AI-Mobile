plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
val ciBuildNumber = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toIntOrNull() ?: 1
android {
    namespace = "com.clipforge.mobile"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.clipforge.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = ciBuildNumber
        versionName = "0.2.$ciBuildNumber"
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    signingConfigs {
        getByName("debug") {
            // Use exactly the identity restored and checked by Actions.
            storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.36.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.36.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    implementation("androidx.core:core:1.15.0")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-transformer:1.5.1")
    implementation("androidx.media3:media3-effect:1.5.1")
}
// Render real Android layouts during the studio interaction tests.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    maxHeapSize = "2g"
    systemProperty("roborazzi.test.record", "true")
    systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
}
