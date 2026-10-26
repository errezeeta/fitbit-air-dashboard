import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val backendBaseUrl = providers.gradleProperty("backendBaseUrl")
    .orElse("https://fitbit-air-backend-i3pz5b53aq-no.a.run.app")
    .get()
    .trim()
// An empty base URL is how a release shipped showing sample data: the app fell
// back to the fake repository without saying anything. Fail the build instead.
check(backendBaseUrl.startsWith("https://")) {
    "backendBaseUrl must be an https URL, got '$backendBaseUrl'"
}
val escapedBackendBaseUrl = backendBaseUrl.replace("\\", "\\\\").replace("\"", "\\\"")
val repoOwner = "errezeeta"
val repoName = "fitbit-air-dashboard"

// A pinned signing key. Without it each build inside the throwaway container
// minted a fresh debug key, so every APK had a different signature and Android
// refused to install it over the previous one ("App not installed").
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasPinnedKeystore = keystorePropertiesFile.exists()

android {
    namespace = "com.javier.fitbitair"
    compileSdk = 37
    buildToolsVersion = "36.0.0"

    signingConfigs {
        if (hasPinnedKeystore) {
            create("pinned") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            if (hasPinnedKeystore) {
                signingConfig = signingConfigs.getByName("pinned")
            }
        }
    }

    defaultConfig {
        applicationId = "com.javier.fitbitair"
        minSdk = 31
        targetSdk = 35
        versionCode = 24
        versionName = "0.9.12"
        buildConfigField("String", "BACKEND_BASE_URL", "\"$escapedBackendBaseUrl\"")
        buildConfigField("String", "UPDATE_REPO", "\"https://api.github.com/repos/$repoOwner/$repoName/releases/latest\"")
        buildConfigField("int", "APP_VERSION_CODE", "$versionCode")
        buildConfigField("String", "APP_VERSION_NAME", "\"$versionName\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("androidx.core:core-ktx:1.18.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.2.10")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
