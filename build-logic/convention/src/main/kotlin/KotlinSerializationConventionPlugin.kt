import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class KotlinSerializationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.plugin.serialization")
            }

            dependencies {
                add("implementation", "org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            }
        }
    }
}

