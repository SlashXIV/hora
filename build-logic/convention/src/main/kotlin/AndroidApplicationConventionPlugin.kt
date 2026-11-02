import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")

            val extension = extensions.getByType<ApplicationExtension>()
            configureKotlinAndroid(extension)

            extension.apply {
                defaultConfig.apply {
                    targetSdk = libs.versionInt("targetSdk")
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                // Les versions suivent la pile commune de la famille (atelier/ANDROID.md),
                // montée pour toutes les apps à la fois : « une version plus récente
                // existe » n'est pas un défaut de Hora.
                lint.disable += setOf("AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable")
            }
        }
    }
}
