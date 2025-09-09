package nobility.downloader.core.scraper.player

import Resource
import nobility.downloader.core.Core
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.VideoUtil

object PlayVideoWithFfplay {

    //0, 9 volume keys
    fun play(
        url: String,
        userAgent: String,
        windowTitle: String = url,
        ffplayPath: String = VideoUtil.FfmpegPathHolder.ffplayPath,
    ): Resource<Boolean> {

        if (ffplayPath.isEmpty()) {
            return Resource.Error("ffplay not found.")
        }

        val command = listOf(
            ffplayPath,
            "-volume", "50",
            "-window_title", windowTitle,
            "-headers", "User-Agent: $userAgent",
            //"-autoexit",
            "-x", "800",
            "-y", "600",
            //"-loglevel", "info",
            url
        )

        return try {

            val process = ProcessBuilder(command)
                .inheritIO()
                .redirectErrorStream(true)
                .start()

            Core.child.runningProcesses.add(process)

            Resource.Success(true)
        } catch (e: Exception) {
            Resource.Error(e)
        }
    }
}