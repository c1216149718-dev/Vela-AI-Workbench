import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("androidx.navigation.safeargs.kotlin")
}

android {
    namespace = "com.deepseek.widget"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.deepseek.widget"
        minSdk = 26
        targetSdk = 36
        versionCode = 29
        versionName = "1.22.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        compose = true
    }

    sourceSets {
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.appcompat:appcompat:1.6.1")

    // Fragments
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    // RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Material Design
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")

    // Compose 1.10 aligns with the stable Haze 1.7 material implementation.
    // 2026.03.01 aligns Runtime/Foundation/UI on Compose 1.10.6. This patch
    // fixes the lazy-layout reuse crash present in 1.10.5 without moving the
    // application onto a new Compose feature line.
    val composeBom = platform("androidx.compose:compose-bom:2026.03.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("dev.chrisbanes.haze:haze:1.7.2")
    implementation("dev.chrisbanes.haze:haze-materials:1.7.2")
    implementation("com.patrykandpatrick.vico:compose-m3:2.1.3")
    implementation("com.github.Dimezis:BlurView:version-3.2.0")
    implementation("io.github.sceneview:sceneview:4.1.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // ConstraintLayout
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // OkHttp for API calls
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // kotlinx.serialization (JSON parsing)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // WorkManager for periodic background updates
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // DataStore Preferences for local storage
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")

    // Navigation Component
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")

    // Room
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    androidTestImplementation("androidx.room:room-testing:2.8.4")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.work:work-testing:2.9.0")
    androidTestImplementation("androidx.arch.core:core-testing:2.2.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}
