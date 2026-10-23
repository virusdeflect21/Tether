plugins {
    id("com.android.library")
}

android {
    namespace = "dev.tether.core.data"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin { jvmToolchain(21) }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:lyrics"))
    implementation(libs.core.ktx)
    implementation(libs.documentfile)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.newpipe.extractor)
    implementation(libs.coroutines.android)
    implementation(libs.lifecycle.process)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
