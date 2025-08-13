package nobility.downloader.utils

import AppInfo
import Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.net.ssl.HttpsURLConnection

object FFmpegDownloader {

    enum class OperatingSystem {
        WINDOWS,
        MAC,
        LINUX,
        UNSUPPORTED
    }

    enum class Architecture {
        AMD64,
        ARM64,
        UNSUPPORTED
    }

    data class ComputerInfo(
        val operatingSystem: OperatingSystem,
        val architecture: Architecture
    ) {
        override fun toString(): String {
            return operatingSystem.name + ":" + architecture.name
        }
    }

    private fun myPC(): ComputerInfo {

        val osName = System.getProperty("os.name").lowercase()
        val osArch = System.getProperty("os.arch").lowercase()

        val operatingSystem: OperatingSystem = when {
            osName.contains("win") -> OperatingSystem.WINDOWS
            osName.contains("mac") -> OperatingSystem.MAC
            osName.contains("nux") || osName.contains("nix") -> OperatingSystem.LINUX
            else -> OperatingSystem.UNSUPPORTED
        }

        val architecture: Architecture = when {
            osArch.contains("aarch64") || osArch.contains("arm64") -> Architecture.ARM64
            osArch.contains("64") -> Architecture.AMD64
            else -> Architecture.UNSUPPORTED
        }

        return ComputerInfo(operatingSystem, architecture)
    }

    suspend fun downloadLatestFFmpeg(
        computerInfo: ComputerInfo = myPC()
    ): Resource<Boolean> = withContext(Dispatchers.IO) {
        val link = fetchLinkForPC(computerInfo)
        if (link.isEmpty()) {
            return@withContext Resource.Error("This PC isn't supported.")
        }
        val downloadFile = File(AppInfo.databasePath, "ffmpeg.zip")
        val outputDirectoryFile = File(AppInfo.databasePath)
        outputDirectoryFile.mkdirs()
        val currentFiles = outputDirectoryFile.listFiles()
        var hasFfmpeg = false
        var hasffPlay = false
        currentFiles?.forEach {
            if (it.name.contains("ffmpeg", true)) {
                hasFfmpeg = true
            }
            if (it.name.contains("ffplay", true)) {
                hasffPlay = true
            }
            if (it.name.contains("ffprobe", true)) {
                it.delete()
            }
        }
        if (hasFfmpeg && hasffPlay) {
            FrogLog.info("FFmpeg & FFplay already found.")
            return@withContext Resource.Success(true)
        }
        if (downloadFile.exists()) {
            downloadFile.delete()
        }
        var con: HttpsURLConnection? = null
        try {
            con = Tools.openFollowingRedirects(link)
            val expectedSize = con.contentLengthLong
            //FrogLog.message("Found FFmpeg zip. Size: $expectedSize")
            FrogLog.message("Downloading FFmpeg for: $computerInfo")

            con.inputStream.use { input ->
                BufferedOutputStream(FileOutputStream(downloadFile)).use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        total += bytesRead
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }

            val actualSize = downloadFile.length()
            if (actualSize != expectedSize) {
                return@withContext Resource.Error(
                    "Download incomplete or corrupted. Expected Length: $expectedSize Downloaded Length: $actualSize"
                )
            }
            FrogLog.message("Finished downloading FFmpeg. Extracting files...")
            Tools.unzipFile(
                downloadFile,
                outputDirectoryFile
            )
            downloadFile.delete()
            val files = outputDirectoryFile.listFiles()
            files?.forEach {
                if (it.name.contains("ffprobe", true)) {
                    it.delete()
                    return@forEach
                }
            }
        } catch (e: Exception) {
            return@withContext Resource.Error(e)
        } finally {
            try {
                con?.disconnect()
            } catch (_: Exception) {}
        }

        return@withContext Resource.Success(true)
    }

    private fun fetchLinkForPC(info: ComputerInfo): String {
        val base = "https://github.com/Tyrrrz/FFmpegBin/releases/download/7.1.1/ffmpeg-%s-%s.zip"
        if (info.architecture == Architecture.UNSUPPORTED
            || info.operatingSystem == OperatingSystem.UNSUPPORTED) {
            return ""
        }
        val osName = when (info.operatingSystem) {
            OperatingSystem.WINDOWS -> "windows"
            OperatingSystem.MAC -> "osx"
            OperatingSystem.LINUX -> "linux"
            else -> ""
        }

        val archName = when (info.architecture) {
            Architecture.AMD64 -> "x64"
            Architecture.ARM64 -> "arm64"
            else -> ""
        }
        return base.format(osName, archName)
    }

}