import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("mue.android.library")
                apply("mue.compose")
                apply("mue.hilt")
            }

            dependencies {
                add("implementation", project(":core:model"))
                add("implementation", project(":core:common"))
                add("implementation", project(":core:design"))
            }
        }
    }
}

