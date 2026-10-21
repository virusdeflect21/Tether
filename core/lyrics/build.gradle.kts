plugins {
    id("com.android.library")
}

android {
    namespace = "dev.tether.core.lyrics"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin { jvmToolchain(21) }
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.coroutines.android)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
}
