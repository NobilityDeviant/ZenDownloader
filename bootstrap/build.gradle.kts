plugins {
    application
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("MainKt")
}

dependencies {
    implementation(project(":common"))
}

tasks.register<Jar>("packageBootstrapJar") {

    group = "custom jar"
    description = "Creates a JAR for the bootstrap."

    archiveBaseName.set("launcher")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = "MainKt"
    }

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
        val jarFile = destinationDirectory.get().file("launcher.jar").asFile
        if (jarFile.exists()) {
            println("The bootstrap jar has been created in: ${jarFile.absolutePath}")
        } else {
            println("Failed to find the created bootstrap jar.")
        }
    }
}
