plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.moneykeeper.core.domain"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.coroutines.android)
}
