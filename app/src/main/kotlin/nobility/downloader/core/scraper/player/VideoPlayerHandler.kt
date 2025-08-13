package nobility.downloader.core.scraper.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import nobility.downloader.core.BoxHelper.Companion.int
import nobility.downloader.core.Core
import nobility.downloader.core.entities.Episode
import nobility.downloader.core.scraper.video_download.Functions
import nobility.downloader.core.scraper.video_download.Functions.fileSize
import nobility.downloader.core.scraper.video_download.VideoDownloadHelper
import nobility.downloader.core.settings.Defaults
import nobility.downloader.core.settings.Quality
import nobility.downloader.utils.Constants
import nobility.downloader.utils.FrogLog

object VideoPlayerHandler {

    private enum class ReturnCheck {
        SUCCESS, ERROR, RETRY
    }

    suspend fun playEpisode(
        episode: Episode,
        temporaryQuality: Quality?
    ) = withContext(Dispatchers.IO) {

        val help = VideoDownloadHelper(
            episode,
            temporaryQuality
        )
        val data = help.videoDownloadData

        suspend fun run(): ReturnCheck {
            if (data.retries >= Defaults.VIDEO_RETRIES.int()) {
                data.error("Reached max retries. Failed to play video online.")
                return ReturnCheck.ERROR
            }

            data.message("Attempting to find video to play online. Retries: ${data.retries}")

            val slug = episode.slug
            if (slug.isEmpty()) {
                data.error("Failed to play episode with no slug.")
                return ReturnCheck.ERROR
            }

            val movie = Core.child.movieHandler.movieForSlug(slug)
            if (movie != null) {
                data.error("Playing movies online isn't supported right now.")
                return ReturnCheck.ERROR
            }

            val parsedResult = help.parseQualities(slug)

            if (parsedResult.isFailed) {
                if (!parsedResult.message.isNullOrEmpty()) {
                    data.error(
                        "Failed to parse qualities for video playing. | Retrying...",
                        parsedResult.message
                    )
                }
                return ReturnCheck.RETRY
            }
            val preferredDownload = parsedResult.data!!
            val downloadLink = preferredDownload.downloadLink
            val qualityOption = preferredDownload.quality

            if (downloadLink.endsWith(".m3u8")) {
                FrogLog.info("Found m3u8. Attempting to play it with ffplay on the default stream.")
                val play = PlayVideoWithFfplay.play(
                    downloadLink,
                    data.userAgent,
                    episode.name + " (${qualityOption.tag})"
                )
                if (play.isFailed) {
                    data.error(
                        "Failed to play m3u8 video with ffplay.",
                        play.message
                    )
                    return ReturnCheck.ERROR
                } else {
                    return ReturnCheck.SUCCESS
                }
            } else {
                val fileSizeRetries = Defaults.FILE_SIZE_RETRIES.int()
                var originalFileSize = 0L
                var headMode = true
                data.debug("Checking video file size with $fileSizeRetries max retries.")
                var fileSizeErrorLogged = false
                for (i in 0..fileSizeRetries) {
                    originalFileSize = fileSize(
                        downloadLink,
                        data.userAgent,
                        headMode
                    )
                    if (originalFileSize <= Constants.minFileSize) {
                        headMode = headMode.not()
                        if (i == fileSizeRetries / 2 && !fileSizeErrorLogged) {
                            data.error(
                                "Failed to find video file size. Current retries: $i",
                                important = true
                            )
                            fileSizeErrorLogged = true
                        }
                        continue
                    } else {
                        break
                    }
                }
                if (originalFileSize <= Constants.minFileSize) {
                    if (data.downloadDatas.isNotEmpty()) {
                        data.error(
                            "Failed to find video file size after $fileSizeRetries retries. | Using another quality."
                        )
                        data.downloadDatas.remove(preferredDownload)
                        if (data.downloadDatas.isEmpty()) {
                            data.error(
                                "Failed to play video online. There are no more qualities to check."
                            )
                            return ReturnCheck.ERROR
                        }
                        return ReturnCheck.RETRY
                    } else {
                        data.error(
                            "Failed to play video online. There are no more qualities to check."
                        )
                        return ReturnCheck.ERROR
                    }
                }
                data.message("Found valid video link. Attempting to play it with ffplay.")

                val con = Functions.wcoConnection(
                    downloadLink,
                    data.userAgent
                )
                val play = PlayVideoWithFfplay.play(
                    downloadLink,
                    con,
                    episode.name + " (${qualityOption.tag})"
                )
                if (play.isFailed) {
                    data.error(
                        "Failed to play video with ffplay.",
                        play.message
                    )
                    return ReturnCheck.ERROR
                } else {
                    return ReturnCheck.SUCCESS
                }
            }
        }

        while (true) {
            val result = run()
            if (result == ReturnCheck.SUCCESS) {
                delay(5000)
                data.base.killDriver()
                break
            } else if (result == ReturnCheck.ERROR) {
                data.base.killDriver()
                break
            } else if (result == ReturnCheck.RETRY) {
                data.retries++
                delay(500)
                continue
            }
        }

    }

}