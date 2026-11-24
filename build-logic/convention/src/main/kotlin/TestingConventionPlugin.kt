import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class TestingConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            dependencies {
                add("testImplementation", "org.junit.jupiter:junit-jupiter:5.11.3")
                add("testImplementation", "io.mockk:mockk:1.13.13")
                add("testImplementation", "app.cash.turbine:turbine:1.2.0")
                add("testImplementation", "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
            }
        }
    }
}

