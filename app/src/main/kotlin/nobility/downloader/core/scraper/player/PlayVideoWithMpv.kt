package nobility.downloader.core.scraper.player

import Resource
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.Core
import nobility.downloader.core.settings.Defaults
import nobility.downloader.utils.FrogLog

object PlayVideoWithMpv {

    fun isMpvInstalled(
        mpvPath: String = "mpv"
    ): Boolean {
        return try {
            val process = ProcessBuilder(mpvPath, "--version")
                .redirectErrorStream(true)
                .start()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (_: Exception) {
            false
        }
    }

    fun play(
        url: String,
        userAgent: String,
        windowTitle: String = url,
        mpvPath: String = Defaults.MPV_PATH.string().ifEmpty { "mpv" }
    ): Resource<Boolean> {

        if (!isMpvInstalled(mpvPath)) {
            return Resource.Error("mpv not found or not executable.")
        }

        FrogLog.debug(
            "Using mpv path: $mpvPath"
        )

        val command = listOf(
            mpvPath,
            "--force-window=yes",
            "--title=$windowTitle",
            "--volume=50",
            "--geometry=800x600",
            "--autofit=800x600",
            "--user-agent=$userAgent",
            "--cookies=yes",
            "--quiet",
            "\"$url\""
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
