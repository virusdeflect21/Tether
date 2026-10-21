plugins {
    id("com.android.library")
}

android {
    namespace = "dev.tether.core.downloads"
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
    implementation(project(":core:database"))
    implementation(project(":core:data"))
    implementation(project(":core:lyrics"))
    implementation(libs.core.ktx)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.work.runtime.ktx)
    implementation(libs.okhttp)
    implementation(libs.coroutines.android)
    implementation(libs.media3.exoplayer)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
