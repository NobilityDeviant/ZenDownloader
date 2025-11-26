package nobility.downloader.core.scraper.video_download.m3u8_downloader.util

import AppInfo
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.CollUtil.newArrayListWithCapacity
import nobility.downloader.utils.FrogLog
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

object VideoUtil {

    private val log: Logger = LoggerFactory.getLogger(VideoUtil::class.java)

    private fun findDatabaseFfmpeg(): String {
        val database = File(AppInfo.databasePath)
        val children = database.listFiles()
        children?.forEach {
            if (it.name.contains("ffmpeg", true)) {
                return it.absolutePath
            }
        }
        return ""
    }

    private fun loadFfmpegPath(): String {
        val databaseFfmpeg = findDatabaseFfmpeg()
        if (databaseFfmpeg.isNotEmpty()) {
            return databaseFfmpeg
        }
        if (execCommand(listOf("ffmpeg", "-version"))) {
            return "ffmpeg"
        }
        return ""
    }

    fun convertToMp4(
        destVideoPath: File,
        sourceVideoPaths: List<Path>
    ): Boolean {
        val ffmpegPath = FfmpegPathHolder.ffmpegPath
        if (ffmpegPath.isEmpty()) {
            log.error("ffmpeg not found.")
            return false
        }
        Preconditions.checkNotBlank(ffmpegPath, "ffmpeg path")
        if (sourceVideoPaths.isEmpty()) {
            log.error("sourceVideoPaths are empty.")
            return false
        }

        val startTime = System.currentTimeMillis()
        log.info("convert to ({}) start", destVideoPath.absolutePath)

        val allTsFile: Path
        if (sourceVideoPaths.size > 1) {
            allTsFile = sourceVideoPaths[0].resolveSibling("all.ts")
            val listFile = sourceVideoPaths[0].resolveSibling("list.txt")

            val contents = newArrayListWithCapacity<String>(sourceVideoPaths.size)
            for (path in sourceVideoPaths) {
                val content = String.format("file '%s'", path.toString())
                contents.add(content)
            }
            try {
                Files.deleteIfExists(listFile)
                Files.deleteIfExists(allTsFile)
                Files.write(listFile, contents, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            } catch (e: Exception) {
                log.error(e.message, e)
            }

            val command = mutableListOf<String>()
            command.add(ffmpegPath)

            command.add("-protocol_whitelist")
            command.add("concat,file,http,https,tcp,tls,crypto")

            command.add("-f")
            command.add("concat")

            command.add("-safe")
            command.add("0")

            command.add("-analyzeduration")
            command.add("2147483647")

            command.add("-probesize")
            command.add("2147483647")

            command.add("-fflags")
            command.add("+genpts+igndts+discardcorrupt")

            command.add("-i")
            command.add(listFile.toString())

            command.add("-c")
            command.add("copy")

            command.add(allTsFile.toString())

            val res = execCommand(command)
            if (!res) {
                log.error("convert failed when concat to {}", allTsFile.fileName)
                return false
            }
        } else {
            allTsFile = sourceVideoPaths[0]
        }

        val command = mutableListOf<String>()
        command.add(ffmpegPath)
        command.add("-y")
        command.add("-i")
        command.add(allTsFile.toString())

        command.add("-c:v")
        command.add("copy")

        command.add("-c:a")
        command.add("copy")

        command.add("-movflags")
        command.add("+faststart")

        command.add(destVideoPath.toString())

        try {
            val res = execCommand(command)
            if (res) {
                log.info("Convert succeeded")
            } else {
                log.error("Convert failed")
            }
            Files.deleteIfExists(allTsFile)
            return res
        } catch (e: Exception) {
            log.error(e.message, e)
            return false
        } finally {
            val endTime = System.currentTimeMillis()
            log.info("convert cost {} seconds", (endTime - startTime) / 1000.0)
        }
    }

    fun mergeVideoAndAudio(
        videoFile: File,
        audioFile: File,
        destVideoFile: File
    ): Boolean {
        val ffmpegPath = FfmpegPathHolder.ffmpegPath
        if (ffmpegPath.isEmpty()) {
            log.error("ffmpeg not found..")
            return false
        }
        val startTime = System.currentTimeMillis()
        //ffmpeg -i video.mp4 -i audio.m4a -acodec copy -vcodec copy output.mp4
        val command = mutableListOf<String>()
        command.add(ffmpegPath)

        command.add("-analyzeduration")
        command.add("2147483647")
        command.add("-probesize")
        command.add("2147483647")

        command.add("-fflags")
        command.add("+genpts+discardcorrupt")

        command.add("-i")
        command.add(videoFile.absolutePath)
        command.add("-i")
        command.add(audioFile.absolutePath)

        command.add("-c:v")
        command.add("copy")
        command.add("-c:a")
        command.add("copy")

        command.add("-movflags")
        command.add("+faststart")

        command.add(destVideoFile.absolutePath)

        try {
            val res = execCommand(command)
            if (!res) {
                FrogLog.error(
                    "Failed to merge video and audio.",
                    "Command returned 1."
                )
            }
            return res
        } catch (e: Exception) {
            FrogLog.error(
                "Failed to merge video and audio.",
                e
            )
            return false
        } finally {
            val endTime = System.currentTimeMillis()
            log.info("Merge cost {} seconds", (endTime - startTime) / 1000.0)
        }
    }

    private fun execCommand(commands: List<String>): Boolean {
        //FrogLog.info("execCommand ${commands.joinToString(" ")}")
        try {
            val videoProcess = ProcessBuilder(commands)
                .redirectErrorStream(true)
                .start()
            var s: String?
            val stdInput = BufferedReader(InputStreamReader(videoProcess.inputStream))
            while ((stdInput.readLine().also { s = it }) != null) {
                log.warn(s)
            }
            val code = videoProcess.waitFor()
            //FrogLog.info("execCommand code=$code")
            return code == 0
        } catch (e: Exception) {
            FrogLog.error(
                "Failed to execute ffmpeg command.",
                e
            )
            return false
        }
    }

    private fun findDatabaseFfplay(): String {
        val database = File(AppInfo.databasePath)
        val children = database.listFiles()
        children?.forEach {
            if (it.name.contains("ffplay", true)) {
                return it.absolutePath
            }
        }
        return ""
    }

    private fun loadFfplayPath(): String {
        val databaseFfplay = findDatabaseFfplay()
        if (databaseFfplay.isNotEmpty()) {
            return databaseFfplay
        }
        if (execCommand(listOf("ffplay", "-version"))) {
            //FrogLog.info("Found ffplay installed.")
            return "ffplay"
        }
        return ""
    }

    object FfmpegPathHolder {
        val ffmpegPath = loadFfmpegPath()
        val ffplayPath = loadFfplayPath()
    }
}
