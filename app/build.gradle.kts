plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.parcelize")
    id("com.github.triplet.play")
}

val defaultVersionName = "0.1.0"
val defaultVersionCode = 1

val releaseVersionName = System.getenv("VR2XR_VERSION_NAME")?.trim()?.takeIf { it.isNotEmpty() }
val releaseVersionCodeRaw = System.getenv("VR2XR_VERSION_CODE")?.trim()?.takeIf { it.isNotEmpty() }
val releaseVersionCode = releaseVersionCodeRaw?.toIntOrNull()
    ?: if (releaseVersionCodeRaw == null) null else throw GradleException(
        "VR2XR_VERSION_CODE must be an integer when set."
    )

val releaseTasksRequested = gradle.startParameter.taskNames.any { task ->
    task.contains("release", ignoreCase = true) || task.contains("publish", ignoreCase = true)
}

if (releaseTasksRequested && releaseVersionName == null) {
    throw GradleException(
        "Missing VR2XR_VERSION_NAME for release/publish tasks. Run tools/release/derive_android_version.sh first."
    )
}

if (releaseTasksRequested && releaseVersionCode == null) {
    throw GradleException(
        "Missing VR2XR_VERSION_CODE for release/publish tasks. Run tools/release/derive_android_version.sh first."
    )
}

val releaseSigningEnv = mapOf(
    "VR2XR_UPLOAD_STORE_FILE" to System.getenv("VR2XR_UPLOAD_STORE_FILE")?.trim()?.takeIf { it.isNotEmpty() },
    "VR2XR_UPLOAD_STORE_PASSWORD" to System.getenv("VR2XR_UPLOAD_STORE_PASSWORD")?.trim()?.takeIf { it.isNotEmpty() },
    "VR2XR_UPLOAD_KEY_ALIAS" to System.getenv("VR2XR_UPLOAD_KEY_ALIAS")?.trim()?.takeIf { it.isNotEmpty() },
    "VR2XR_UPLOAD_KEY_PASSWORD" to System.getenv("VR2XR_UPLOAD_KEY_PASSWORD")?.trim()?.takeIf { it.isNotEmpty() }
)

val hasReleaseSigning = releaseSigningEnv.values.none { it == null }

if (releaseTasksRequested) {
    val missing = releaseSigningEnv
        .filterValues { it == null }
        .keys
        .sorted()
    if (missing.isNotEmpty()) {
        throw GradleException(
            "Missing release signing environment variables: ${missing.joinToString(", ")}"
        )
    }
}

android {
    namespace = "com.vr2xr"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vr2xr"
        minSdk = 33
        targetSdk = 35
        versionCode = releaseVersionCode ?: defaultVersionCode
        versionName = releaseVersionName ?: defaultVersionName
        buildConfigField("boolean", "PLAYBACK_DIAGNOSTICS_ENABLED", "false")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseSigningEnv["VR2XR_UPLOAD_STORE_FILE"]))
                storePassword = requireNotNull(releaseSigningEnv["VR2XR_UPLOAD_STORE_PASSWORD"])
                keyAlias = requireNotNull(releaseSigningEnv["VR2XR_UPLOAD_KEY_ALIAS"])
                keyPassword = requireNotNull(releaseSigningEnv["VR2XR_UPLOAD_KEY_PASSWORD"])
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
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
        buildConfig = true
        viewBinding = true
    }
}

play {
    useApplicationDefaultCredentials = true
    defaultToAppBundles.set(true)
    track.set(System.getenv("VR2XR_PLAY_TRACK")?.trim()?.takeIf { it.isNotEmpty() } ?: "internal")
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation(project(":onexr"))

    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
