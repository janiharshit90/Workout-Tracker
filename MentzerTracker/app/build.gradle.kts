import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// ---------------------------------------------------------------------------
// Versioning
//   Release builds get their version from the git tag via CI:
//     tag v1.4.2  ->  versionName "1.4.2", versionCode 10402
//   so every tag is automatically "newer" to Android and to the in-app updater.
//   Local builds default to the value below. (minor/patch must stay < 100)
// ---------------------------------------------------------------------------
val appVersionName: String =
    (findProperty("appVersionName") as String?)?.removePrefix("v")?.takeIf { it.isNotBlank() } ?: "1.0.0"

fun versionCodeFrom(name: String): Int {
    val p = name.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 } + listOf(0, 0, 0)
    return p[0] * 10000 + p[1] * 100 + p[2]
}
val appVersionCode: Int = (findProperty("appVersionCode") as String?)?.toIntOrNull() ?: versionCodeFrom(appVersionName)

// ---------------------------------------------------------------------------
// Update source: "owner/repo" on GitHub.
//   In GitHub Actions this is filled in automatically from GITHUB_REPOSITORY.
//   For local builds, set updateRepo=owner/repo in gradle.properties.
// ---------------------------------------------------------------------------
val updateRepo: String =
    (findProperty("updateRepo") as String?) ?: System.getenv("GITHUB_REPOSITORY") ?: ""

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.example.mentzertracker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.mentzertracker"
        minSdk = 24
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                // Resolved from the PROJECT ROOT (the old file() call looked inside app/,
                // which broke CI signing).
                storeFile = rootProject.file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePropsFile.exists()) signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
}
