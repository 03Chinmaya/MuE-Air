const fs = require('fs');
const path = require('path');

function ensureDir(p) {
  if (!fs.existsSync(p)) fs.mkdirSync(p, { recursive: true });
}

function writeGradle(modPath, content) {
  ensureDir(modPath);
  fs.writeFileSync(path.join(modPath, 'build.gradle.kts'), content.trim() + '\n', 'utf8');
  console.log('Scaffolded module:', modPath);
}

// 1. :app module
writeGradle('app', `
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.mue.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mue.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
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
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:design"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:datastore"))
    implementation(project(":core:player"))
    implementation(project(":core:providers"))
    implementation(project(":core:auth"))
    implementation(project(":core:e2e"))
    implementation(project(":core:chat"))
    implementation(project(":core:social"))
    implementation(project(":core:updates"))

    implementation(project(":feature:onboarding"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:home"))
    implementation(project(":feature:search"))
    implementation(project(":feature:player"))
    implementation(project(":feature:library"))
    implementation(project(":feature:chat"))
    implementation(project(":feature:social"))
    implementation(project(":feature:settings"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
}
`);

ensureDir('app/src/main/kotlin/com/mue/app');
ensureDir('app/src/main/res/values');
fs.writeFileSync('app/src/main/AndroidManifest.xml', `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:name=".MuEApplication"
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="MuE"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.MuE">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.MuE">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
`, 'utf8');

// 2. Core modules definitions
const coreModules = [
  ['core/model', 'com.mue.core.model', `
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.mue.core.model"
    compileSdk = 35
    defaultConfig { minSdk = 29 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
`],
  ['core/common', 'com.mue.core.common', `
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.mue.core.common"
    compileSdk = 35
    defaultConfig { minSdk = 29 }
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
}
`],
  ['core/database', 'com.mue.core.database', `
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.mue.core.database"
    compileSdk = 35
    defaultConfig { minSdk = 29 }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(project(":core:testing"))
    testImplementation(libs.androidx.room.testing)
}
`],
  ['core/network', 'com.mue.core.network', `
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.mue.core.network"
    compileSdk = 35
    defaultConfig { minSdk = 29 }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.auth)
    implementation(libs.ktor.client.logging)
}
`],
  ['core/datastore', 'com.mue.core.datastore', `
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.mue.core.datastore"
    compileSdk = 35
    defaultConfig { minSdk = 29 }
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
}
`],
  ['core/player', 'com.mue.core.player', `
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.mue.core.player"
    compileSdk = 35
    defaultConfig { minSdk = 29 }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
}
`],
  ['core/providers', 'com.mue.core.providers', `
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.mue.core.providers"
    compileSdk = 35
    defaultConfig { minSdk = 29 }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:network"))
}
`],
  ['core/auth', 'com.mue.core.auth', `
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.mue.core.auth"
    compileSdk = 35
    defaultConfig { minSdk = 29 }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":core:datastore"))
}
`],
  ['core/e2e', 'com.mue.core.e2e', `
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.mue.core.e2e"
    compileSdk = 35
    defaultConfig { minSdk = 29 }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.libsignal.client)
}
`],
  ['core/chat', 'com.mue.core.chat', `
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.mue.core.chat"
    compileSdk = 35
    defaultConfig { minSdk = 29 }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(project(":core:e2e"))
}
`],
  ['core/social', 'com.mue.core.social', `
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.mue.core.social"
    compileSdk = 35
    defaultConfig { minSdk = 29 }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
}
`],
  ['core/updates', 'com.mue.core.updates', `
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.mue.core.updates"
    compileSdk = 35
    defaultConfig { minSdk = 29 }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:network"))
}
`],
  ['core/design', 'com.mue.core.design', `
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.mue.core.design"
    compileSdk = 35
    defaultConfig { minSdk = 29 }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
`],
  ['core/testing', 'com.mue.core.testing', `
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.mue.core.testing"
    compileSdk = 35
    defaultConfig { minSdk = 29 }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.junit.jupiter)
    implementation(libs.mockk)
    implementation(libs.kotlinx.coroutines.test)
    implementation(libs.turbine)
}
`]
];

coreModules.forEach(([modPath, namespace, content]) => {
  writeGradle(modPath, content);
  const pkgPath = path.join(modPath, 'src/main/kotlin', namespace.replace(/\./g, '/'));
  ensureDir(pkgPath);
});

// 3. Feature modules definitions
const featureNames = [
  'onboarding',
  'auth',
  'home',
  'search',
  'player',
  'library',
  'chat',
  'social',
  'settings'
];

featureNames.forEach(feat => {
  const modPath = path.join('feature', feat);
  const namespace = `com.mue.feature.${feat}`;
  const content = `
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
}

android {
    namespace = "${namespace}"
    compileSdk = 35
    defaultConfig { minSdk = 29 }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:design"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)

    testImplementation(project(":core:testing"))
}
`;
  writeGradle(modPath, content);
  const pkgPath = path.join(modPath, 'src/main/kotlin', namespace.replace(/\./g, '/'));
  ensureDir(pkgPath);
});

console.log('All modules scaffolded successfully!');

