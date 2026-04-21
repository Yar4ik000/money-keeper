import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // Kotlin встроен в AGP 9.0+ — см. https://kotl.in/gradle/agp-built-in-kotlin
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// Читаем секреты на этапе конфигурации.
// Если keystore.properties отсутствует — signingConfig.release не создаётся,
// и assembleRelease упадёт с понятной ошибкой "Keystore file not set".
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.moneykeeper.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.moneykeeper.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 104
        versionName = "1.3.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.process)

    // WorkManager + Hilt integration
    implementation(libs.workmanager.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Feature modules
    implementation(project(":feature:dashboard"))
    implementation(project(":feature:accounts"))
    implementation(project(":feature:transactions"))
    implementation(project(":feature:analytics"))
    implementation(project(":feature:forecast"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:auth"))
    implementation(project(":core:ui"))
    implementation(project(":core:domain"))
    implementation(project(":core:database"))

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
