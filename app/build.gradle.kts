import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.mediasearch"
    compileSdk = 35

    // Pin to the exactly-installed build-tools so AGP never tries to fetch another revision.
    // Installed locally: 35.0.0, 36.1.0, 37.0.0  (no plain "36.0.0").
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "dev.mediasearch"
        minSdk = 29
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0"

        testInstrumentationRunner = "dev.mediasearch.RuntimeProbe"
    }

    // Two ABI-specific APKs; no extra universal APK is produced.
    //  - arm64-v8a : distribution build for real devices
    //  - x86_64    : emulator build
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
        release {
            isMinifyEnabled = true
            // Keep resources for the validation build; code shrinking is enabled below.
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // TEST SIGNING ONLY — installable prototype; use a private key for production.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    lint {
        // Errors must block acceptance of both debug checks and the release build.
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Compose BOM 2025.07.00 -> ui 1.8.3 / material3 1.3.2, both verified minCompileSdk<=35.
    val composeBom = platform("androidx.compose:compose-bom:2025.07.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Chromium network stack. 143.7445.0 is the real artifact; the 500.x line is a
    // deprecated empty shim that only forwards to org.chromium.net:cronet-bundled.
    implementation("org.chromium.net:cronet-embedded:143.7445.0")

    // Needed by a WebView-based login flow (WebViewFeature / WebSettingsCompat).
    implementation("androidx.webkit:webkit:1.14.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // The unit tests are written against `kotlin.test`. kotlin("test") resolves to
    // org.jetbrains.kotlin:kotlin-test:<kotlin plugin version> and automatically selects
    // its JUnit4 variant, because junit:junit is on the test classpath.
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250107")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
