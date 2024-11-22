package nobility.downloader.core.scraper.video_download.m3u8_downloader.util

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

    private fun loadFfmpegPath(): String {
        if (FfmpegPathHolder.useLocalFfmpeg) {
            if (execCommand(mutableListOf("ffmpeg", "-version"))) {
                return "ffmpeg"
            }
            throw RuntimeException("use Local ffmpeg, can not find ffmpeg tool")
        }

        try {
            // try load from lib
            val ffmpegClazz = Class.forName("org.bytedeco.ffmpeg.ffmpeg")
            val loaderClazz = Class.forName("org.bytedeco.javacpp.Loader")
            // Loader.load(org.bytedeco.ffmpeg.ffmpeg.class);
            val loadMethod = loaderClazz.getDeclaredMethod("load", Class::class.java)
            val result = loadMethod.invoke(loaderClazz, ffmpegClazz)
            if (result is String) {
                return result
            }
        } catch (_: ClassNotFoundException) {
            // try use ffmpeg from env:PATH
            if (execCommand(mutableListOf("ffmpeg", "-version"))) {
                return "ffmpeg"
            }
        } catch (e: Exception) {
            throw RuntimeException(e)
        }

        throw RuntimeException("can't find ffmpeg tool")
    }

    fun convertToMp4(
        destVideoPath: File,
        sourceVideoPaths: List<Path>
    ): Boolean {
        val ffmpegPath = FfmpegPathHolder.ffmpegPath
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

        command.add("-acodec")
        command.add("copy")

        command.add("-vcodec")
        command.add("copy")

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
        //Preconditions.checkNotBlank(ffmpegPath, "ffmpeg path")
        val startTime = System.currentTimeMillis()
        //ffmpeg -i video.mp4 -i audio.m4a -acodec copy -vcodec copy output.mp4

        val command = mutableListOf<String>()
        command.add(ffmpegPath)
        command.add("-i")
        command.add(videoFile.absolutePath)
        command.add("-i")
        command.add(audioFile.absolutePath)
        command.add("-acodec")
        command.add("copy")
        command.add("-vcodec")
        command.add("copy")
        command.add(destVideoFile.absolutePath)

        try {
            val res = execCommand(command)
            if (!res) {
                FrogLog.logError(
                    "Failed to merge video and audio.",
                    "Command returned 1."
                )
            }
            return res
        } catch (e: Exception) {
            FrogLog.logError(
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
        FrogLog.logInfo("execCommand ${commands.joinToString(" ")}")
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
            FrogLog.logInfo("execCommand code=$code")
            return code == 0
        } catch (e: Exception) {
            FrogLog.logError(
                "Failed to execute ffmpeg command.",
                e
            )
            return false
        }
    }

    private object FfmpegPathHolder {
        val useLocalFfmpeg: Boolean =
            java.lang.Boolean.getBoolean(
                "nobility.downloader.core.scraper.video_download.m3u8_downloader.util.VideoUtil.useLocalFfmpeg"
            )

        val ffmpegPath: String = loadFfmpegPath()
    }
}
