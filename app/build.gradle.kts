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
        versionName = "1.8.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += when {
            providers.gradleProperty("mediaDeviceTests").orNull == "true" -> listOf("x86_64")
            providers.gradleProperty("universalRelease").orNull == "true" -> listOf("arm64-v8a", "x86_64")
            else -> listOf("arm64-v8a")
        } }
    }
    signingConfigs {
        getByName("debug") {
            // Use exactly the identity restored and checked by Actions.
            storeFile = file(System.getProperty("user.home") + "/.android/clipforge-development.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            isDebuggable = false
            isJniDebuggable = false
            // Keep the original installed identity. The key is owner-backed-up and never committed.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
    if (providers.gradleProperty("mediaDeviceTests").orNull != "true") {
        externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }
    }
}
dependencies {
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
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
    val preparedSdk = providers.environmentVariable("CLIPFORGE_ROBOLECTRIC_SDK").orNull
    if (preparedSdk != null) {
        systemProperty("robolectric.dependency.dir", preparedSdk)
        systemProperty("robolectric.offline", "true")
    } else {
        systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
    }
}

// Fixture generation uses production serializers; assertions run against the installed release UI.
tasks.register<JavaExec>("writeReleaseUpgradeSeed") {
    dependsOn("testDebugUnitTest")
    mainClass.set("com.clipforge.mobile.ReleaseUpgradeSeed")
    doFirst { classpath = tasks.named<org.gradle.api.tasks.testing.Test>("testDebugUnitTest").get().classpath }
    args(layout.buildDirectory.dir("outputs/release-update-seed").get().asFile.absolutePath)
}

