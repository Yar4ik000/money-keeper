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
    // JSR-330 inject annotations only — no framework runtime, just @Inject/@Singleton
    implementation("javax.inject:javax.inject:1")

    testImplementation(libs.junit)
}
