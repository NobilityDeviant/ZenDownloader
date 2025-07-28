package nobility.downloader.core.scraper.video_download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nobility.downloader.core.BoxHelper.Companion.int
import nobility.downloader.core.Core
import nobility.downloader.core.entities.Episode
import nobility.downloader.core.scraper.video_download.Functions.downloadVideo
import nobility.downloader.core.scraper.video_download.Functions.fileSize
import nobility.downloader.core.scraper.video_download.MovieDownloader.handleMovie
import nobility.downloader.core.settings.Defaults
import nobility.downloader.core.settings.Quality
import nobility.downloader.utils.Constants
import nobility.downloader.utils.Tools
import nobility.downloader.utils.update

/**
 * The core handler of video downloading.
 * This class is used to organize the executions in an easy-to-read manner (for my sanity).
 */
class VideoDownloadHandler(
    private val episode: Episode,
    temporaryQuality: Quality? = null
) {

    private val help = VideoDownloadHelper(
        episode,
        temporaryQuality
    )

    private val data get() = help.videoDownloadData

    suspend fun run() = withContext(Dispatchers.IO) {
        Core.child.downloadThread.incrementDownloadsInProgress()
        while (Core.child.isRunning) {
            if (data.reachedMax()) {
                break
            }
            if (data.finished) {
                break
            }
            val slug = episode.slug
            if (slug.isEmpty()) {
                data.message("Skipping episode with no slug.")
                data.finishEpisode()
                continue
            }
            if (data.quickCheckVideoExists(slug)) {
                continue
            }
            val movie = Core.child.movieHandler.movieForSlug(slug)
            if (movie != null) {
                handleMovie(movie, data)
                continue
            }
            val parsedResult = help.parseQualities(slug)
            if (parsedResult.isFailed) {
                if (parsedResult.errorCode == ErrorCode.FFMPEG_NOT_INSTALLED.code) {
                    data.finishEpisode()
                }
                if (!parsedResult.message.isNullOrEmpty()) {
                    help.videoDownloadData.error(
                        "Failed to parse qualities.",
                        parsedResult.message
                    )
                }
                continue
            }
            val preferredDownload = parsedResult.data!!
            val downloadLink = preferredDownload.downloadLink
            val qualityOption = preferredDownload.quality
            val saveFile = data.generateEpisodeSaveFile(qualityOption)
            val created = data.createDownload(
                slug,
                qualityOption,
                saveFile
            )
            if (!created) {
                continue
            }
            data.info("Successfully found video link with ${data.retries} retries.")
            try {
                if (downloadLink.endsWith(".m3u8")) {
                    help.handleM3U8(
                        preferredDownload,
                        saveFile
                    )
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
                            data.resetDownload()
                            data.downloadDatas.remove(preferredDownload)
                            if (data.mCurrentDownload != null) {
                                Core.child.removeDownload(data.currentDownload)
                                data.mCurrentDownload = null
                            }
                            data.retries++
                            if (data.downloadDatas.isEmpty()) {
                                data.error(
                                    "There are no more qualities to check. | Skipping episode"
                                )
                                data.finishEpisode()
                            }
                            continue
                        } else {
                            data.error(
                                "Failed to find video file size. There are no more qualities to check. | Skipping episode"
                            )
                            data.finishEpisode()
                            continue
                        }
                    }
                    data.debug(
                        "Successfully found video file size of: ${Tools.bytesToString(originalFileSize)}"
                    )
                    if (saveFile.exists()) {
                        if (saveFile.length() >= originalFileSize) {
                            data.message("(IO) Skipping completed video.")
                            data.currentDownload.downloadPath = saveFile.absolutePath
                            data.currentDownload.fileSize = originalFileSize
                            data.currentDownload.downloading = false
                            data.currentDownload.queued = false
                            data.currentDownload.update()
                            data.finishEpisode()
                            continue
                        }
                    } else {
                        var fileRetries = 0
                        var fileError: Exception? = null
                        for (i in 0..3) {
                            try {
                                val created = saveFile.createNewFile()
                                if (!created) {
                                    throw Exception("No error thrown. $i")
                                }
                            } catch (e: Exception) {
                                fileError = e
                                fileRetries++
                                continue
                            }
                        }
                        if (!saveFile.exists()) {
                            data.error(
                                "Failed to create video file after 3 retries. | Skipping episode",
                                fileError
                            )
                            data.finishEpisode()
                            continue
                        }
                    }
                    data.message("Starting download with ${qualityOption.tag} quality.")
                    data.currentDownload.queued = false
                    data.currentDownload.downloading = true
                    data.currentDownload.fileSize = originalFileSize
                    Core.child.addDownload(data.currentDownload)
                    data.currentDownload.update()
                    data.undriver.blank()
                    downloadVideo(
                        downloadLink,
                        saveFile,
                        data
                    )
                    data.currentDownload.downloading = false
                    //second time to ensure ui update
                    data.currentDownload.update()
                    if (saveFile.exists() && saveFile.length() >= originalFileSize) {
                        Core.child.downloadThread.incrementDownloadsFinished()
                        data.message("Successfully downloaded with ${qualityOption.tag} quality.")
                        help.handleSecondVideo()
                        data.finishEpisode()
                    }
                }
            } catch (e: Exception) {
                data.currentDownload.queued = true
                data.currentDownload.downloading = false
                data.currentDownload.update()
                if (e.message?.contains("Connection reset", true) == false) {
                    data.error(
                        "Failed to download video. | Retrying...",
                        e,
                        true
                    )
                }
                data.retries++
            }
        }
        killDriver()
    }

    fun killDriver() {
        data.base.killDriver()
    }
}