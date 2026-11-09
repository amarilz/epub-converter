import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.tasks.WriteProperties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

val appVersion = providers.gradleProperty("appVersion").get()
val appVersionResourcesDirectory = layout.buildDirectory.dir("generated/appVersionResources")

val generateAppVersion by tasks.registering(WriteProperties::class) {
    destinationFile = appVersionResourcesDirectory
        .get()
        .file("app-version.properties")
        .asFile
    property("appVersion", appVersion)
}

kotlin {
    jvmToolchain(21)
    jvm()

    sourceSets.named("jvmMain") {
        resources.srcDir(appVersionResourcesDirectory)
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            implementation(libs.filekit.core)
            implementation(libs.filekit.dialogs)
            implementation(libs.filekit.dialogs.compose)
            implementation(libs.filekit.coil)
            implementation(libs.lucide.icons)

            implementation(libs.multiplatform.markdown.renderer)
            implementation(libs.multiplatform.markdown.renderer.m3)
            implementation(libs.multiplatform.markdown.renderer.code)

            implementation("nl.siegmann.epublib:epublib-core:3.1")
            implementation("nl.siegmann.epublib:epublib-parent:3.1")
            implementation("org.jsoup:jsoup:1.21.2")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
    }
}

tasks.named("jvmProcessResources") {
    dependsOn(generateAppVersion)
}

compose.desktop {
    application {
        mainClass = "com.amarildo.epubconverter.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "EpubConverter"
            packageVersion = appVersion

            macOS {
                bundleID = "com.amarildo.epubconverter"
            }
        }
    }
}
