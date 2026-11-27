plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    // REMOVED: google.services (Firebase)
}

android {
    namespace = "com.mue.app"                 // packages stay; only applicationId changes
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mueair.app"      // MUST differ from com.mue.app
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"                   // v1.0
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64") }
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("MU_AIR_KEYSTORE") ?: "release.keystore"
            val keystoreFile = file(keystorePath)
            val storePw = System.getenv("MU_AIR_STORE_PW")
            val alias = System.getenv("MU_AIR_KEY_ALIAS") ?: "mueair"
            val keyPw = System.getenv("MU_AIR_KEY_PW")
            if (keystoreFile.exists() && !storePw.isNullOrBlank() && !keyPw.isNullOrBlank()) {
                storeFile = keystoreFile
                storePassword = storePw
                keyAlias = alias
                keyPassword = keyPw
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures { compose = true; buildConfig = true }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    dependenciesInfo { includeInApk = false; includeInBundle = false }

    compileOptions {
        isCoreLibraryDesugaringEnabled = false        // minSdk 26 has java.time
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        jniLibs { useLegacyPackaging = false }
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/*.version",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
                "**/*.kotlin_metadata"
            )
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:datastore"))
    implementation(project(":core:design"))
    implementation(project(":core:player"))
    implementation(project(":core:providers"))
    implementation(project(":core:lyrics"))
    implementation(project(":core:auth"))
    implementation(project(":core:artist"))

    implementation(project(":feature:auth"))
    implementation(project(":feature:home"))
    implementation(project(":feature:search"))
    implementation(project(":feature:player"))
    implementation(project(":feature:library"))
    implementation(project(":feature:listen"))
    implementation(project(":feature:artist"))
    implementation(project(":feature:settings"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)   // see Phase 8
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.timber)

    // REMOVED: libsignal-client, firebase-bom, firebase-messaging,
    //          glance-appwidget, glance-material3, biometric, unifiedpush,
    //          coreLibraryDesugaring
}


gradle.taskGraph.whenReady {
    val isRelease = allTasks.any {
        it.name.contains("Release") &&
            (it.name.startsWith("assemble") || it.name.startsWith("package") || it.name.startsWith("bundle"))
    }
    if (isRelease) {
        val keystorePath = System.getenv("MU_AIR_KEYSTORE") ?: "release.keystore"
        val keystoreFile = file(keystorePath)
        val storePw = System.getenv("MU_AIR_STORE_PW")
        val keyPw = System.getenv("MU_AIR_KEY_PW")
        if (!keystoreFile.exists() || storePw.isNullOrBlank() || keyPw.isNullOrBlank()) {
            throw GradleException(
                "Release signing requires a valid keystore ($keystorePath) and non-blank MU_AIR_STORE_PW and MU_AIR_KEY_PW environment variables."
            )
        }
    }
}
