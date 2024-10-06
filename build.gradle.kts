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
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.10"
}

kotlin {
    jvmToolchain(17)
}

group = "nobility.downloader"
version = "1.0.5"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    maven("https://jitpack.io")
    google()
}

dependencies {
    implementation(compose.desktop.currentOs)
    //network
    implementation("org.jsoup:jsoup:1.16.2")
    implementation("org.seleniumhq.selenium:selenium-java:4.23.1")
    implementation("io.github.bonigarcia:webdrivermanager:5.9.2")
    //implementation("org.slf4j:slf4j-nop:2.1.0-alpha1")
    //implementation("org.slf4j:slf4j-api:2.0.4")
    implementation("ch.qos.logback:logback-classic:1.5.7")
    runtimeOnly("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0-RC2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0-RC2")
    //implementation("org.mozilla:rhino:1.7.15")
    //database
    val objectBox = "3.8.0"
    implementation("io.objectbox:objectbox-linux:$objectBox")
    implementation("io.objectbox:objectbox-macos:$objectBox")
    implementation("io.objectbox:objectbox-windows:$objectBox")
    implementation("io.objectbox:objectbox-linux-arm64:$objectBox")
    implementation("io.objectbox:objectbox-linux-armv7:$objectBox")
    implementation("com.darkrockstudios:mpfilepicker:3.1.0")
    //for url updating.
    implementation("com.google.http-client:google-http-client:1.44.2")
    //for undetected chrome
    implementation("com.alibaba:fastjson:2.0.51")
    //ui
    implementation(compose.material3)
    implementation("com.materialkolor:material-kolor:1.7.0")
    implementation("br.com.devsrsouza.compose.icons:eva-icons:1.1.0")
    //for unzipping assets
    implementation("net.lingala.zip4j:zip4j:2.11.5")
    //m3u8 downloading
    //implementation("io.github.kanglong1023:m3u8-catcher:1.0.0") {
      //  exclude("org.bytedeco", "ffmpeg-platform")
    //}
    implementation("org.bytedeco:ffmpeg:5.0-1.5.7")
    implementation("org.bytedeco:ffmpeg:5.0-1.5.7:windows-x86_64")
    implementation("org.bytedeco:ffmpeg:1.5.7:linux-x86_64")
    implementation("org.bytedeco:javacpp:1.5.7:windows-x86_64")
    implementation("org.bytedeco:javacpp:1.5.7:linux-x86_64")
    
    implementation("org.apache.commons:commons-lang3:3.6")
    implementation("org.apache.commons:commons-collections4:4.3")
    implementation("org.bouncycastle:bcprov-jdk18on:1.74")
    implementation("org.apache.httpcomponents.client5:httpclient5:5.2.1")
    implementation("io.netty:netty-common:4.1.113.Final")
    implementation("org.jctools:jctools-core:4.0.3")
}

apply(plugin = "io.objectbox")

compose.desktop {
    application {
        mainClass = "MainKt"
        jvmArgs += listOf("-Xmx3G")

        nativeDistributions {
            //in order to distribute for mac, we need to use xcode to sign the app.
            //i dont have a mac and we can't make a jar afaik. :(
            //might use a 3rd party later if mac is in demand
            targetFormats(
                TargetFormat.Exe,
                TargetFormat.Deb,
                TargetFormat.Rpm //i can make this too, I was just so tired. will make later.
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
