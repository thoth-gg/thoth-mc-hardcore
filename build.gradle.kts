import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

plugins {
    kotlin("jvm") version "2.3.20"
    id("com.gradleup.shadow") version "9.4.1"
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("net.dmulloy2:ProtocolLib:5.4.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}

kotlin {
    jvmToolchain(21)
}

abstract class SpigotRunTask : DefaultTask() {
    @get:InputFile
    abstract val pluginJar: RegularFileProperty

    @get:Internal
    abstract val serverDir: DirectoryProperty

    @get:Internal
    abstract val pluginsDir: DirectoryProperty

    @TaskAction
    fun runServer() {
        val serverDirFile = serverDir.asFile.get()
        val pluginsDirFile = pluginsDir.asFile.get().apply { mkdirs() }
        val sourceJar = pluginJar.asFile.get()
        val targetJar = pluginsDirFile.resolve(sourceJar.name)

        sourceJar.copyTo(targetJar, overwrite = true)
        logger.lifecycle("Copied plugin jar to ${targetJar.absolutePath}")

        val process = ProcessBuilder(
            "java",
            "-Xms4096M",
            "-Xmx4096M",
            "-jar",
            "spigot-26.1.1.jar",
            "nogui"
        )
            .directory(serverDirFile)
            .redirectErrorStream(true)
            .start()

        val stopRequested = AtomicBoolean(false)
        val outputPump = Executors.newSingleThreadExecutor()
        val outputFuture = outputPump.submit {
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    logger.lifecycle("[spigot] $line")
                }
            }
        }

        fun requestGracefulStop(reason: String) {
            if (!stopRequested.compareAndSet(false, true)) {
                return
            }

            logger.lifecycle(reason)
            runCatching {
                process.outputStream.bufferedWriter().use { writer ->
                    writer.write("stop")
                    writer.newLine()
                    writer.flush()
                }
            }.onFailure {
                logger.warn("Failed to send stop command to Spigot. Falling back to process termination.", it)
            }
        }

        val shutdownHook = Thread {
            requestGracefulStop("Gradle is shutting down. Sending graceful stop to Spigot...")
            if (!process.waitFor(20, TimeUnit.SECONDS) && process.isAlive) {
                logger.lifecycle("Spigot did not stop after graceful shutdown request. Forcing termination...")
                process.destroy()
                if (!process.waitFor(10, TimeUnit.SECONDS) && process.isAlive) {
                    process.destroyForcibly()
                }
            }
        }
        Runtime.getRuntime().addShutdownHook(shutdownHook)

        try {
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw org.gradle.api.GradleException("Spigot server exited with code $exitCode")
            }
        } catch (_: InterruptedException) {
            requestGracefulStop("Gradle task interrupted. Sending graceful stop to Spigot...")
            if (!process.waitFor(20, TimeUnit.SECONDS) && process.isAlive) {
                logger.lifecycle("Spigot did not stop in time after graceful shutdown request. Forcing termination...")
                process.destroy()
                if (!process.waitFor(10, TimeUnit.SECONDS) && process.isAlive) {
                    process.destroyForcibly()
                    process.waitFor()
                }
            }
            Thread.currentThread().interrupt()
        } finally {
            runCatching {
                Runtime.getRuntime().removeShutdownHook(shutdownHook)
            }

            if (process.isAlive) {
                requestGracefulStop("Run task is finishing while Spigot is still alive. Sending graceful stop...")
                if (!process.waitFor(20, TimeUnit.SECONDS) && process.isAlive) {
                    process.destroy()
                }
                if (!process.waitFor(10, TimeUnit.SECONDS) && process.isAlive) {
                    process.destroyForcibly()
                    process.waitFor()
                }
            }

            outputFuture.get(5, TimeUnit.SECONDS)
            outputPump.shutdownNow()
        }
    }
}

tasks {
    build {
        dependsOn(shadowJar)
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    register<SpigotRunTask>("run") {
        group = "application"
        description = "Builds the shaded plugin, copies it to server/plugins, and starts the Spigot server."
        dependsOn(build)
        pluginJar.set(shadowJar.flatMap { it.archiveFile })
        serverDir.set(layout.projectDirectory.dir("server"))
        pluginsDir.set(layout.projectDirectory.dir("server/plugins"))
    }
}
