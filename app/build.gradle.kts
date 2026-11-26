plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.shaonirfan.samonline.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shaonirfan.samonline.tv"
        minSdk = 23
        targetSdk = 35
        versionCode = 7
        versionName = "1.0.6"

        // OpenSubtitles consumer key: injected by CI from the OPENSUBTITLES_API_KEY repository
        // secret (never committed). Empty in local builds = online subtitle search disabled.
        val osKey = (System.getenv("OPENSUBTITLES_API_KEY") ?: "").filter { it.isLetterOrDigit() }
        buildConfigField("String", "OPENSUBTITLES_API_KEY", "\"$osKey\"")
    }

    // Release signing is optional: it is only configured when the keystore and
    // the KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD env vars are present
    // (see .github/workflows). Otherwise the release build falls back to debug signing.
    val keystoreFile = file("samonline.jks")
    val hasReleaseKey = keystoreFile.exists() && !System.getenv("KEYSTORE_PASSWORD").isNullOrBlank()

    signingConfigs {
        // A fixed debug key checked into the repo, so every CI build is signed the same way
        // and updates install over the previous version (keeping My List / watch progress).
        // It is NOT a secret; use the release keystore secrets for Play-quality signing.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (hasReleaseKey) {
            create("release") {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.tv.material3.ExperimentalTvMaterial3Api",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=coil.annotation.ExperimentalCoilApi",
        )
    }

    lint {
        // Media3's @UnstableApi is opted into via compiler args above.
        disable += "UnsafeOptInUsageError"
        // lifecycle's LiveData detector crashes with Kotlin 2.1 (we don't use LiveData).
        disable += "NullSafeMutableLiveData"
        abortOnError = false
        checkReleaseBuilds = false
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    // Core / lifecycle
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Compose (foundation lazy layouts are the officially recommended TV lists
    // since tv-foundation 1.0.0 removed TvLazyRow / TvLazyColumn)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")

    // Compose for TV
    implementation("androidx.tv:tv-material:1.0.1")
    implementation("androidx.tv:tv-foundation:1.0.0")

    // Media3 ExoPlayer (+ FFmpeg audio decoder for AC3/EAC3/DTS/TrueHD in MKVs)
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.5.0+1")

    // Images, scraping, coroutines
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
