plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.mue.core.network"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
        buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:4000\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:4000\"")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"https://mue-backend.onrender.com\"")
        }
    }

    buildFeatures {
        buildConfig = true
    }

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
    implementation(project(":core:database"))
    implementation(libs.androidx.work.runtime.ktx)
    api(project(":core:datastore"))
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.auth)
    implementation(libs.ktor.client.logging)
    implementation(libs.socketio.client)
}
