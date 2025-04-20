import org.jetbrains.compose.desktop.application.dsl.TargetFormat

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        //4.1.0 breaks the jvm
        //default to 4.0.3 if it breaks
        classpath("io.objectbox:objectbox-gradle-plugin:4.0.3")
    }
}

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose").version("2.1.10")
}

kotlin {
    jvmToolchain(21)
}

group = "nobility.downloader"
version = "1.1.1"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    maven("https://jitpack.io")
    google()
}

dependencies {
    implementation(compose.desktop.currentOs)
    //network
    implementation("org.jsoup:jsoup:1.19.1")
    implementation("org.seleniumhq.selenium:selenium-java:4.31.0")
    implementation("io.github.bonigarcia:webdrivermanager:6.0.1")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    runtimeOnly("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    //database
    val objectBox = "4.0.3"
    implementation("io.objectbox:objectbox-linux:$objectBox")
    implementation("io.objectbox:objectbox-macos:$objectBox")
    implementation("io.objectbox:objectbox-windows:$objectBox")
    implementation("io.objectbox:objectbox-linux-arm64:$objectBox")
    implementation("io.objectbox:objectbox-linux-armv7:$objectBox")
    implementation("com.darkrockstudios:mpfilepicker:3.1.0")
    //for url updating.
    implementation("com.google.http-client:google-http-client:1.46.3")
    //for undetected chrome
    implementation("com.alibaba:fastjson:2.0.57")
    //ui
    implementation(compose.material3)
    implementation("com.materialkolor:material-kolor:2.1.1")
    implementation("br.com.devsrsouza.compose.icons:eva-icons:1.1.1")
    implementation("io.coil-kt.coil3:coil-compose:3.1.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.1.0")
    //for unzipping assets
    implementation("net.lingala.zip4j:zip4j:2.11.5")
    //m3u8 downloading
    implementation("org.apache.commons:commons-lang3:3.17.0")
    implementation("org.apache.commons:commons-collections4:4.4")
    implementation("org.bouncycastle:bcprov-jdk18on:1.80")
    implementation("org.apache.httpcomponents.client5:httpclient5:5.4.3")
    implementation("io.netty:netty-common:4.2.0.Final")
    implementation("org.jctools:jctools-core:4.0.5")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
}

apply(plugin = "io.objectbox")

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
                TargetFormat.Rpm //will make if someone asks
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
