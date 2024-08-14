import org.jetbrains.compose.desktop.application.dsl.TargetFormat

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("io.objectbox:objectbox-gradle-plugin:3.8.0")
    }
}

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
}

kotlin {
    jvmToolchain(17)
}

group = "nobility.downloader"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    maven("https://jitpack.io")
    google()
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("br.com.devsrsouza.compose.icons:eva-icons:1.1.0")
    implementation("org.jsoup:jsoup:1.16.2")
    implementation("org.seleniumhq.selenium:selenium-java:4.17.0")
    implementation("io.github.bonigarcia:webdrivermanager:5.7.0")
    implementation("org.slf4j:slf4j-api:2.0.4")
    implementation("org.slf4j:slf4j-simple:2.0.4")
    runtimeOnly("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0-RC2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0-RC2")
    val objectBox = "3.8.0"
    implementation("io.objectbox:objectbox-linux:$objectBox")
    implementation("io.objectbox:objectbox-macos:$objectBox")
    implementation("io.objectbox:objectbox-windows:$objectBox")
    implementation("io.objectbox:objectbox-linux-arm64:$objectBox")
    implementation("io.objectbox:objectbox-linux-armv7:$objectBox")
    implementation("com.darkrockstudios:mpfilepicker:3.1.0")
    //for url updating.
    implementation("com.google.http-client:google-http-client:1.44.2")
    implementation("com.alibaba:fastjson:2.0.51")
    //implementation("io.grpc:grpc-context:1.65.1")
    //implementation("org.jcodec:jcodec:0.2.5")
    //implementation("org.bytedeco:javacv-platform:1.5.10")
    //implementation("net.bramp.ffmpeg:ffmpeg:0.8.0")
}

apply(plugin = "io.objectbox")

compose {
    kotlinCompilerPlugin.set("1.5.8")
}

compose.desktop {
    application {
        mainClass = "MainKt"
        jvmArgs += listOf("-Xmx4G")

        nativeDistributions {
            //in order to distribute for mac, we need to use xcode to sign the app.
            //i dont have a mac and we can't make a jar afaik. :(
            //might use a 3rd party later if mac is in demand
            targetFormats(TargetFormat.Exe, TargetFormat.AppImage)
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
                iconFile.set(project.file("/src/main/resources/images/icon.ico"))
            }

            macOS {
                bundleID = "nobility.downloader"
                iconFile.set(project.file("/src/main/resources/images/icon.icns"))
            }

            linux {
                iconFile.set(project.file("/src/main/resources/images/icon.png"))
            }
        }
    }
}
