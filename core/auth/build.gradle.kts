plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.mue.core.auth"
    compileSdk = 35
    defaultConfig { minSdk = 26 }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":core:datastore"))
    implementation(project(":core:database"))
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.ktor.client.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
