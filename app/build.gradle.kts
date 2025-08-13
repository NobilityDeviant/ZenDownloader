@file:OptIn(ExperimentalStdlibApi::class)

import org.jetbrains.kotlin.org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.zip.GZIPInputStream

val objectBox = "4.3.0"
val currentOs: org.gradle.internal.os.OperatingSystem = org.gradle.internal.os.OperatingSystem.current()

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("io.objectbox:objectbox-gradle-plugin:4.3.0")
    }
}

plugins {
    kotlin("jvm")
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kapt)
    alias(libs.plugins.objectbox)
}

kotlin {
    jvmToolchain(21)
}

group = "nobility.downloader"
version = "1.2.6"

repositories {
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    maven("https://jitpack.io")
    google()
}

fun isFatJarBuild(): Boolean {
    return project.gradle.startParameter.taskNames.any {
        it.contains("packageFatJar", ignoreCase = true) ||
                it.contains("runFatJar", ignoreCase = true) ||
                it.contains("packageJARDistributables", ignoreCase = true)
    }
}


dependencies {

    implementation(project(":common"))

    when {
        currentOs.isLinux -> {
            implementation("io.objectbox:objectbox-linux:$objectBox")
            implementation("io.objectbox:objectbox-linux-arm64:$objectBox")
            implementation("io.objectbox:objectbox-linux-armv7:$objectBox")
        }

        currentOs.isMacOsX -> implementation("io.objectbox:objectbox-macos:$objectBox")
        currentOs.isWindows -> implementation("io.objectbox:objectbox-windows:$objectBox")
    }

    val allObjectBoxLibs = listOf(
        "io.objectbox:objectbox-linux:$objectBox",
        "io.objectbox:objectbox-linux-arm64:$objectBox",
        "io.objectbox:objectbox-linux-armv7:$objectBox",
        "io.objectbox:objectbox-macos:$objectBox",
        "io.objectbox:objectbox-windows:$objectBox"
    )

    val skikoVersion = "0.9.17"

    val allSkikoLibs = listOf(
        "org.jetbrains.skiko:skiko-awt-runtime-windows-x64:$skikoVersion",
        "org.jetbrains.skiko:skiko-awt-runtime-linux-x64:$skikoVersion",
        "org.jetbrains.skiko:skiko-awt-runtime-linux-arm64:$skikoVersion",
        "org.jetbrains.skiko:skiko-awt-runtime-macos-x64:$skikoVersion",
        "org.jetbrains.skiko:skiko-awt-runtime-macos-arm64:$skikoVersion"
    )

    if (isFatJarBuild()) {
        allObjectBoxLibs.forEach {
            implementation(it)
        }
        allSkikoLibs.forEach {
            implementation(it)
        }
    } else {
        implementation(compose.desktop.currentOs)
    }

    kapt("io.objectbox:objectbox-processor:$objectBox")

    //network
    implementation("org.jsoup:jsoup:1.20.1")
    implementation("org.seleniumhq.selenium:selenium-java:4.33.0")
    implementation("io.github.bonigarcia:webdrivermanager:6.1.0")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    runtimeOnly("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("com.google.http-client:google-http-client:1.47.0")

    //for undetected chrome
    implementation("com.alibaba:fastjson:2.0.57")
    //ui
    implementation(compose.material3)
    implementation("com.materialkolor:material-kolor:2.1.1")
    implementation("br.com.devsrsouza.compose.icons:eva-icons:1.1.1")
    implementation("io.coil-kt.coil3:coil-compose:3.2.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.2.0")
    implementation("com.darkrockstudios:mpfilepicker:3.1.0")
    //m3u8 downloading
    implementation("org.apache.commons:commons-lang3:3.17.0")
    implementation("org.apache.commons:commons-collections4:4.4")
    implementation("org.bouncycastle:bcprov-jdk18on:1.80")
    implementation("org.apache.httpcomponents.client5:httpclient5:5.5")
    implementation("io.netty:netty-common:4.2.1.Final")
    implementation("org.jctools:jctools-core:4.0.5")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
}

compose.desktop {
    application {
        mainClass = "MainKt"
        jvmArgs += listOf("-Xmx3G")
    }
}

interface InjectedExecOps {
    @get:Inject
    val execOps: ExecOperations
}

val graalVersion = "21.0.2"
val baseUrl = "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-$graalVersion"
val projectName = "ZenDownloader"

enum class GraalJDK(
    val folderName: String,
    val filePattern: String,
    val extension: String
) {

    WINDOWS_AMD64(
        "windows-amd64",
        "graalvm-community-jdk-%s_windows-x64_bin.zip",
        "zip"
    ),
    LINUX_AMD64(
        "linux-amd64",
        "graalvm-community-jdk-%s_linux-x64_bin.tar.gz",
        "tar.gz"
    ),
    LINUX_ARM64(
        "linux-arm64",
        "graalvm-community-jdk-%s_linux-aarch64_bin.tar.gz",
        "tar.gz"
    ),
    MAC_AMD64(
        "mac-amd64",
        "graalvm-community-jdk-%s_macos-x64_bin.tar.gz",
        "tar.gz"
    ),
    MAC_ARM64(
        "mac-arm64",
        "graalvm-community-jdk-%s_macos-aarch64_bin.tar.gz",
        "tar.gz"
    );

    fun resolvedFileName(version: String): String {
        return filePattern.format(version)
    }
}

tasks.register("downloadJdks") {

    group = "custom jar"
    description = "Downloads and extracts JDKs into jdks/runtime-*"

    doLast {

        val jdksDir = file("jdks")
        jdksDir.mkdirs()

        for (jdk in GraalJDK.values()) {

            val platform = jdk.folderName
            val fileName = jdk.resolvedFileName(graalVersion)
            val ext = jdk.extension

            val downloadUrl = "$baseUrl/$fileName"
            val outputFile = File(jdksDir, fileName)
            val runtimeDir = File(jdksDir, "runtime-$platform")

            if (runtimeDir.exists()) {
                println("Skipping $platform. JDK already exists.")
                continue
            }

            val connection = URI(downloadUrl).toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            val remoteSize = connection.contentLengthLong
            if (outputFile.exists() && outputFile.length() == remoteSize) {
                println("File $fileName already downloaded.")
            } else {
                println("Downloading $platform JDK ($fileName)")
                outputFile.outputStream().use { out ->
                    URI(downloadUrl).toURL().openStream().use { input ->
                        input.copyTo(out)
                    }
                }
            }

            println("Extracting $platform JDK")

            if (ext == "zip") {
                copy {
                    from(zipTree(outputFile))
                    into(jdksDir)
                }
            } else if (ext == "tar.gz") {
                TarArchiveInputStream(GZIPInputStream(outputFile.inputStream())).use { tarIn ->
                    var entry = tarIn.nextEntry
                    while (entry != null) {
                        val outFile = File(jdksDir, entry.name)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile.mkdirs()
                            outFile.outputStream().use { out ->
                                tarIn.copyTo(out)
                            }
                        }
                        entry = tarIn.nextEntry
                    }
                }
            }

            val extractedDir = jdksDir.listFiles()?.firstOrNull {
                it.name.startsWith("graalvm-community") && it.isDirectory
            }

            val contentsHome = File(extractedDir, "Contents/Home")
            val isMac = contentsHome.exists()

            val runtimeTarget = if (isMac) contentsHome else extractedDir
            runtimeTarget?.renameTo(runtimeDir)

            if (isMac) {
                extractedDir?.deleteRecursively()
            }

            val debloatTargets = listOf(
                "lib/src.zip",
                "lib/missioncontrol",
                "lib/visualvm",
                "lib/plugin.jar",
                "jmods",
                "lib/jfr",
                "lib/oblique-fonts",
                "lib/javafx",
                "release",
                "bin/jaotc",
                "lib/installer",
                "lib/classlist",
                "lib/dt.jar"
            )

            debloatTargets.forEach { relativePath ->
                File(runtimeDir, relativePath).deleteRecursively()
            }

            if (isMac) {
                listOf(
                    File(runtimeDir.parentFile, "Contents/MacOS"),
                    File(runtimeDir.parentFile, "Contents/Info.plist")
                ).forEach { f ->
                    if (f.exists()) {
                        f.deleteRecursively()
                    }
                }
            }
        }
    }
}


tasks.register("packageJARDistributables") {

    group = "custom jar"
    description = "Creates a distributable folder for each OS with bundled JDK and launch scripts."

    dependsOn("downloadJdks")
    dependsOn("packageFatJar")
    dependsOn(":bootstrap:packageBootstrapJar")

    doLast {

        val jarName = "$projectName-${project.version}.jar"
        val outputJar = file("build/custom-jars/$jarName")
        val jdkFolder = file("jdks")
        val distRoot = file("build/distributions")

        distRoot.listFiles()?.forEach { it.deleteRecursively() }

        for (graalJDK in GraalJDK.values()) {

            val runtimeDir = File(
                jdkFolder,
                "runtime-${graalJDK.folderName}"
            )

            if (!runtimeDir.exists()) {
                println("Skipping ${graalJDK.folderName}. JDK not found at: ${runtimeDir.absolutePath}")
                continue
            }

            val distDir = File(
                distRoot,
                "ZenDownloader-${graalJDK.folderName}"
            )
            distDir.deleteRecursively()
            distDir.mkdirs()

            val appDir = File(distDir, "app").also { it.mkdirs() }

            val bootstrapJar = project(":bootstrap")
                .tasks
                .named("packageBootstrapJar")
                .get()
                .outputs
                .files
                .singleFile

            bootstrapJar.copyTo(File(distDir, "launcher.jar"), overwrite = true)
            outputJar.copyTo(File(appDir, jarName), overwrite = true)

            File(distDir, "runtime").also {
                runtimeDir.copyRecursively(it, overwrite = true)
            }

            val isWindows = graalJDK.folderName.startsWith("windows")
            if (isWindows) {
                File(distDir, "run.bat").writeText(
                    """
                    @echo off
                    set DIR=%~dp0
                    "%DIR%runtime\bin\java.exe" -jar "%DIR%\launcher.jar"
                    pause
                    """.trimIndent()
                )
            } else {
                File(distDir, "run.sh").apply {
                    writeText(
                        """
                        #!/bin/bash
                        DIR="$(cd "$(dirname "$0")" && pwd)"
                        "${'$'}DIR/runtime/bin/java" -jar "${'$'}DIR/launcher.jar"
                        """.trimIndent()
                    )
                    setExecutable(true)
                }
            }

            val zipFile = File(distRoot, "$projectName-${graalJDK.folderName}.zip")
            ant.withGroovyBuilder {
                "zip"("destfile" to zipFile.absolutePath) {
                    "fileset"(
                        "dir" to distRoot.absolutePath,
                        "includes" to "${distDir.name}/**"
                    )
                }
            }

            println("Created distributable: ${zipFile.name}")

        }

    }
}

tasks.register<Jar>("packageFatJar") {

    group = "custom jar"
    description = "Builds a fat JAR."
    archiveBaseName.set(projectName)
    //archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    destinationDirectory.set(layout.buildDirectory.dir("custom-jars"))

    manifest {
        attributes["Main-Class"] = "MainKt"
    }

    doFirst {
        val jarFile = destinationDirectory.get().file(
            "$projectName-${project.version}.jar"
        ).asFile
        if (jarFile.exists()) {
            jarFile.delete()
        }
    }

    sourceSets["main"].java.srcDir("build/generated/source/kapt/main")
    dependsOn("kaptKotlin")
    dependsOn(configurations.runtimeClasspath)

    from(sourceSets["main"].output)

    from({
        configurations.runtimeClasspath.get().map { file ->
            if (file.isDirectory) {
                file
            } else {
                zipTree(file).matching {
                    exclude(
                        "META-INF/*.SF",
                        "META-INF/*.DSA",
                        "META-INF/*.RSA",
                        "META-INF/*.EC"
                    )
                }
            }
        }
    })

    from(sourceSets["main"].resources)

    doLast {
        val jarFile = destinationDirectory.get().file("$projectName-${project.version}.jar").asFile
        if (jarFile.exists()) {
            println("The fatJar has been created in: ${jarFile.absolutePath}")
        } else {
            println("Failed to find the created fatJar.")
        }
    }
}

tasks.register("runFatJar") {

    group = "custom jar"
    description = "Builds and runs the fat JAR."

    dependsOn("packageFatJar")

    doLast {
        val jarName = "$projectName-${project.version}.jar"
        val jarFile = layout.buildDirectory.file("custom-jars/$jarName").get().asFile

        if (jarFile.exists()) {

            val injected = project.objects.newInstance<InjectedExecOps>()

            println("Launching: ${jarFile.absolutePath}")

            injected.execOps.javaexec {
                mainClass.set("-jar")
                args = listOf(jarFile.absolutePath)
            }

        } else {
            throw GradleException("fatJar not found at: ${jarFile.absolutePath}")
        }
    }
}