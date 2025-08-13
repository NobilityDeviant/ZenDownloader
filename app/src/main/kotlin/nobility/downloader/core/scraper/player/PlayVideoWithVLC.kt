package nobility.downloader.core.scraper.player

import Resource
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.driver.undetected_chrome.SysUtil
import nobility.downloader.core.settings.Defaults
import nobility.downloader.utils.fileExists
import java.io.IOException


//not supported without a proxy.
//will leave here just in case.
@Suppress("UNUSED")
object PlayVideoWithVLC {

    fun play(
        videoUrl: String,
        userAgent: String,
        referrer: String
    ): Resource<Boolean> {

        val vlcPath = findVlcPath()

        if (vlcPath == null) {
            return Resource.Error("Failed to find valid vlc path.")
        }

        val command = listOf(
            vlcPath,
            "--http-user-agent=$userAgent",
            "--http-referrer=$referrer",
            "--no-video-title-show",
            //"--file-logging",
            //"--logfile=${File("vlc.log").absolutePath}",
            //"--verbose=2",
            //"--play-and-exit",
            videoUrl
        )

        return try {
            ProcessBuilder(command)
                .inheritIO()
                .start()
            Resource.Success(true)
        } catch (e: Exception) {
            Resource.Error(e)
        }
    }

    private fun findVlcPath(
        customVlcPath: String? = Defaults.VLC_PATH.string()
    ): String? {

        if (customVlcPath?.fileExists() == true) {
            return customVlcPath
        }

        if (isVlcCommandAvailable()) {
            return "vlc"
        }

        val possibles = mutableSetOf<String>()

        if (SysUtil.isWindows) {
            possibles.add("C:\\Program Files\\VideoLAN\\VLC\\vlc.exe")
            possibles.add("C:\\Program Files (x86)\\VideoLAN\\VLC\\vlc.exe")
        } else if (SysUtil.isMacOs) {
            possibles.add("/Applications/VLC.app/Contents/MacOS/VLC")
        } else {
            possibles.add("/usr/bin/vlc")
            possibles.add("/usr/local/bin/vlc")
        }
        return possibles.firstOrNull { it.fileExists() }
    }

    private fun isVlcCommandAvailable(): Boolean {
        return try {
            val process = ProcessBuilder("vlc", "--version")
                .redirectErrorStream(true)
                .start()
            process.waitFor()
            process.exitValue() == 0
        } catch (_: IOException) {
            false
        } catch (_: InterruptedException) {
            false
        }
    }
}