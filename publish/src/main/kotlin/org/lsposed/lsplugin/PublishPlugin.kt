package org.lsposed.lsplugin

import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublicationContainer
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.*
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension
import org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin
import com.vanniktech.maven.publish.MavenPublishPlugin as CentralMavenPublishPlugin
import com.vanniktech.maven.publish.MavenPublishBaseExtension as CentralMavenPublishExtension

inline fun Project.configRepository(crossinline setup: MavenArtifactRepository.() -> Unit) {
    plugins.withType(MavenPublishPlugin::class.java) {
        extensions.configure(PublishingExtension::class.java) {
            repositories {
                maven {
                    setup()
                }
            }
        }
    }
}

open class PublishExtensionImpl(private val project: Project) : PublishExtension {
    override var githubRepo: String? = null
        set(value) {
            field = value
            project.configRepository {
                name = "GithubPackages"
                url = project.uri("https://maven.pkg.github.com/$githubRepo")
                credentials {
                    username = System.getenv("GITHUB_ACTOR")
                    password = System.getenv("GITHUB_TOKEN")
                }
            }
        }


    override fun publications(artifactId: String, action: Action<in MavenPom>) {
        project.run {
            extensions.configure(CentralMavenPublishExtension::class.java) {
                publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
                findProperty("signingKey")?.let {
                    signAllPublications()
                }
                pom(action)
                coordinates(artifactId=artifactId)
            }
        }
    }

    override fun publishPlugin(artifactId: String, implementationClass: String, action: Action<in MavenPom>) {
        project.run {
            publications(artifactId, action)
            plugins.withType(JavaGradlePluginPlugin::class.java) {
                extensions.configure(GradlePluginDevelopmentExtension::class.java) {
                    plugins {
                        register(name) {
                            this@register.id = "${group}.${artifactId}"
                            this@register.implementationClass = implementationClass
                        }
                    }
                }
            }
        }
        project.afterEvaluate {
            plugins.withType(JavaGradlePluginPlugin::class.java) {
                extensions.configure(PublishingExtension::class.java) {
                    publications {
                        named<MavenPublication>("pluginMaven") {
                            pom(action)
                        }
                        named<MavenPublication>("${name}PluginMarkerMaven") {
                            pom(action)
                        }
                    }
                }
            }
        }
    }
}

@Suppress("unused")
class PublishPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.subprojects {
            plugins.apply(CentralMavenPublishPlugin::class.java)
            extensions.create(PublishExtensionImpl::class.java, "publish", PublishExtensionImpl::class.java, this)
            extra.set("signingInMemoryKey", findProperty("signingKey") as String?)
            extra.set("signingInMemoryKeyPassword", findProperty("signingPassword") as String?)
        }
    }
}
