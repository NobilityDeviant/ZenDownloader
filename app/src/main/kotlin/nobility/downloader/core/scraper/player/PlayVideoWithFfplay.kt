package nobility.downloader.core.scraper.player

import Resource
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.VideoUtil
import javax.net.ssl.HttpsURLConnection

object PlayVideoWithFfplay {

    //todo add proccesses to a list to close them like drivers

    fun play(
        url: String,
        con: HttpsURLConnection,
        windowTitle: String = url,
        ffplayPath: String = VideoUtil.FfmpegPathHolder.ffplayPath
    ): Resource<Boolean> {

        if (ffplayPath.isEmpty()) {
            return Resource.Error("Ffplay not found.")
        }

        return try {

            val headerString = con.requestProperties
                .flatMap { (key, values) ->
                    values.map { value -> "$key: $value" }
                }
                .joinToString("\r\n")

            val command = listOf(
                ffplayPath,
                "-volume",
                "50",
                "-window_title", windowTitle,
                "-headers", headerString,
                "-autoexit",
                "-x", "800",
                "-y", "600",
                //"-loglevel", "info",
                url
            )

            ProcessBuilder(command)
                .inheritIO()
                .redirectErrorStream(true)
                .start()

            Resource.Success(true)
        } catch (e: Exception) {
            Resource.Error(e)
        }
    }

    //0, 9 volume keys
    fun play(
        url: String,
        userAgent: String,
        windowTitle: String = url,
        ffplayPath: String = VideoUtil.FfmpegPathHolder.ffplayPath,
    ): Resource<Boolean> {

        if (ffplayPath.isEmpty()) {
            return Resource.Error("Ffplay not found.")
        }

        val headerString = headers
            .plus("User-Agent" to userAgent)
            .entries
            .joinToString("\r\n") {
            "${it.key}: ${it.value}"
        }

        val command = listOf(
            ffplayPath,
            "-volume",
            "50",
            "-window_title", windowTitle,
            "-headers", headerString,
            "-autoexit",
            "-x", "800",
            "-y", "600",
            //"-loglevel", "info",
            url
        )

        return try {
            ProcessBuilder(command)
                .inheritIO()
                .redirectErrorStream(true)
                .start()
            Resource.Success(true)
        } catch (e: Exception) {
            Resource.Error(e)
        }
    }



    private val headers get() = mapOf(
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9",
        "Accept-Encoding" to "gzip, deflate, br",
        "Accept-Language" to "en-US,en;q=0.9",
        "Connection" to "keep-alive",
        "Sec-Fetch-Dest" to "document",
        "Sec-Fetch-Mode" to "navigate",
        "Sec-Fetch-Site" to "cross-site",
        "Sec-Fetch-User" to "?1",
        "Upgrade-Insecure-Requests" to "1"
    )

}