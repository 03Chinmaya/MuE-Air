plugins {
    `kotlin-dsl`
}

group = "com.mue.buildlogic"

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.compiler.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.hilt.gradlePlugin)
    compileOnly(libs.detekt.plugin)
    compileOnly(libs.ktlint.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "mue.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "mue.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidFeature") {
            id = "mue.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("compose") {
            id = "mue.compose"
            implementationClass = "ComposeConventionPlugin"
        }
        register("hilt") {
            id = "mue.hilt"
            implementationClass = "HiltConventionPlugin"
        }
        register("kotlinSerialization") {
            id = "mue.kotlin.serialization"
            implementationClass = "KotlinSerializationConventionPlugin"
        }
        register("testing") {
            id = "mue.testing"
            implementationClass = "TestingConventionPlugin"
        }
    }
}

