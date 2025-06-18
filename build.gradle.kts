import org.jetbrains.compose.desktop.application.dsl.TargetFormat

val objectBox = "4.3.0"
val currentOs: org.gradle.internal.os.OperatingSystem = org.gradle.internal.os.OperatingSystem.current()

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        //4.3.0 finally doesn't break!
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
version = "1.2.1"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    maven("https://jitpack.io")
    google()
}

dependencies {

    implementation(compose.desktop.currentOs)

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

    if (project.gradle.startParameter.taskNames.any { it.contains("packageFatJar") }) {
        allObjectBoxLibs.forEach {
            implementation(it)
        }
    }

    kapt("io.objectbox:objectbox-processor:$objectBox")

    //network
    implementation("org.jsoup:jsoup:1.20.1")
    implementation("org.seleniumhq.selenium:selenium-java:4.33.0")
    implementation("io.github.bonigarcia:webdrivermanager:6.1.0")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    runtimeOnly("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    //for url updating.
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
    //for unzipping assets
    implementation("net.lingala.zip4j:zip4j:2.11.5")
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

        nativeDistributions {
            //mac building isn't possible without a mac.
            //if you have a mac and read this and are willing to build main versions, let me know
            targetFormats(
                TargetFormat.Exe,
                TargetFormat.Deb,
                TargetFormat.Rpm
            )
            packageName = "ZenDownloader"
            packageVersion = version.toString()
            description = "ZenDownloader"
            vendor = "NobilityDev"
            modules(
                "java.compiler",
                "java.instrument",
                "java.management",
                "java.naming",
                "java.net.http",
                "java.security.jgss",
                "java.sql",
                "jdk.unsupported"
            )
            windows {
                dirChooser = true
                perUserInstall = true
                shortcut = true
                //never change this
                upgradeUuid = "b30e4c5c-592d-436f-a564-5324af8addc9"
                iconFile.set(project.file("src/main/resources/images/icon.ico"))
            }

            macOS {
                bundleID = "nobility.downloader"
                iconFile.set(project.file("src/main/resources/images/icon.icns"))
            }

            linux {
                debMaintainer = "nobilitydev@protonmail.com"
                menuGroup = "nobility-zendownloader"
                appCategory = "NobilityDev"
                iconFile.set(project.file("src/main/resources/images/icon.png"))
            }
        }
    }
}

//attempting to create a universal jar.
//keeps throwing io.objectbox.EntityInfo not found error.
//but all other errors have been fixed so far.
tasks.register<Jar>("packageFatJar") {
    println("Project Name: " + project.name)
    group = "compose desktop"
    description = "Builds a fat JAR."
    archiveBaseName.set("ZenDownloader")
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    destinationDirectory.set(layout.buildDirectory.dir("custom-jars"))

    manifest {
        attributes["Main-Class"] = "MainKt"
    }

    doFirst {
        val jarFile = destinationDirectory.get().file("ZenDownloader-${project.version}-all.jar").asFile
        if (jarFile.exists()) {
            jarFile.delete()
        }
    }

    sourceSets["main"].java.srcDir("build/generated/source/kapt/main")
    dependsOn("kaptKotlin")
    dependsOn(configurations.runtimeClasspath)

    from(sourceSets["main"].output)

    val preservedJars = listOf(
        "skiko",
        "skia",
        "org.jetbrains.compose",
        "objectbox"
    )

    from({
        configurations.runtimeClasspath.get().flatMap { file ->
            val name = file.name
            val keepAsIs = preservedJars.any {
                name.contains(it, ignoreCase = true)
            }
            if (keepAsIs) {
                listOf(file)
            } else if (file.isDirectory) {
                listOf(file)
            } else {
                zipTree(file).matching {
                    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
                }
            }
        }
    })

    //fail-safe even though they're generated or exported on first run
    from({
        configurations.runtimeClasspath.get().filter { file ->
            file.name.contains("objectbox") && file.extension in listOf("so", "dll", "dylib")
        }
    })

    from(sourceSets["main"].resources)

    doLast {
        val jarFile = destinationDirectory.get().file("ZenDownloader-${project.version}-all.jar").asFile
        if (jarFile.exists()) {
            println("The fatJar has been created in: ${jarFile.absolutePath}")
        } else {
            println("Failed to find the created fatJar.")
        }
    }
}

interface InjectedExecOps {
    @get:Inject
    val execOps: ExecOperations
}

tasks.register("runFatJar") {
    group = "compose desktop"
    description = "Builds and runs the fat JAR."

    dependsOn("packageFatJar")

    doLast {
        val jarName = "ZenDownloader-${project.version}-all.jar"
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



