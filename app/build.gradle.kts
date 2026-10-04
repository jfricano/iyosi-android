plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

fun property(name: String, default: String) = (project.findProperty(name) as String?) ?: default

android {
    namespace = "com.orcasolutions.iyosi"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.orcasolutions.iyosi"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
        buildConfigField("String", "TILE_URL", "\"${property("iyosiTileUrl", "https://tile.openstreetmap.org/{z}/{x}/{y}.png")}\"")
        buildConfigField("String", "TILE_ATTRIBUTION", "\"${property("iyosiTileAttribution", "© OpenStreetMap contributors")}\"")
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    buildTypes {
        debug {
            manifestPlaceholders["cleartextAllowed"] = "true"
            buildConfigField("String", "API_BASE_URL", "\"${property("iyosiApiUrl", "http://10.0.2.2:8000")}\"")
        }
        release {
            manifestPlaceholders["cleartextAllowed"] = "false"
            // Release builds block cleartext, so only an HTTPS default is compiled in. Without one,
            // the person enters a server under Account → API server settings.
            val releaseUrl = property("iyosiApiUrl", "").takeIf { it.startsWith("https://") } ?: ""
            buildConfigField("String", "API_BASE_URL", "\"$releaseUrl\"")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Screenshot tests write PNGs to app/build/outputs/roborazzi for review.
            it.systemProperty("roborazzi.test.record", "true")
            it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.40.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.40.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
