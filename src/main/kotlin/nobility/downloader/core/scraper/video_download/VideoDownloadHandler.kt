package nobility.downloader.core.scraper.video_download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nobility.downloader.core.BoxHelper.Companion.int
import nobility.downloader.core.Core
import nobility.downloader.core.scraper.video_download.Functions.downloadVideo
import nobility.downloader.core.scraper.video_download.Functions.fileSize
import nobility.downloader.core.scraper.video_download.MovieDownloader.handleMovie
import nobility.downloader.core.settings.Defaults
import nobility.downloader.core.settings.Quality
import nobility.downloader.utils.Constants
import nobility.downloader.utils.update

/**
 * The core handler of video downloading.
 * This class is used to organize the executions in an easy-to-read manner (for my sanity).
 */
class VideoDownloadHandler(
    temporaryQuality: Quality? = null
) {

    private val help = VideoDownloadHelper(temporaryQuality)
    private val data get() = help.data

    suspend fun run() = withContext(Dispatchers.IO) {
        while (Core.child.isRunning) {
            if (data.reachedMax()) continue
            if (!data.getNewEpisode()) break
            val slug = data.currentEpisode.slug
            if (slug.isEmpty()) {
                data.writeMessage("Skipping episode with no slug.")
                data.finishEpisode()
                continue
            }
            if (data.quickCheckVideoExists(slug)) continue
            val movie = Core.child.movieHandler.movieForSlug(slug)
            if (movie != null) {
                handleMovie(movie, data)
                continue
            }
            val parsedResult = help.parseQualities(slug)
            if (parsedResult.isFailed) {
                continue
            }
            val parsedData = parsedResult.data!!
            val downloadLink = parsedData.downloadLink
            val qualityOption = parsedData.quality
            val saveFile = data.generateEpisodeSaveFile(qualityOption)
            val created = data.createDownload(
                slug,
                qualityOption,
                saveFile
            )
            if (!created) {
                continue
            }
            data.logInfo("Successfully found video link with ${data.retries} retries.")
            try {
                if (downloadLink.endsWith(".m3u8")) {
                    help.handleM3U8(
                        parsedData,
                        saveFile
                    )
                } else {
                    var fileSizeRetries = Defaults.FILE_SIZE_RETRIES.int()
                    var originalFileSize = 0L
                    var headMode = true
                    data.logInfo("Checking video file size with $fileSizeRetries retries.")
                    for (i in 0..fileSizeRetries) {
                        originalFileSize = fileSize(
                            downloadLink,
                            data.userAgent,
                            headMode
                        )
                        if (originalFileSize <= Constants.minFileSize) {
                            headMode = headMode.not()
                            if (i == fileSizeRetries / 2) {
                                data.logError(
                                    "Failed to find video file size. Current retries: $i"
                                )
                            }
                            continue
                        } else {
                            break
                        }
                    }
                    if (originalFileSize <= Constants.minFileSize) {
                        if (data.qualityAndDownloads.isNotEmpty()) {
                            data.logError(
                                "Failed to find video file size after $fileSizeRetries retries. | Using another quality."
                            )
                            data.qualityAndDownloads.remove(
                                data.qualityAndDownloads.first {
                                    it.downloadLink == downloadLink
                                }
                            )
                            data.retries++
                            continue
                        } else {
                            data.logError(
                                "Failed to find video file size. There are no more qualities to check. | Skipping episode"
                            )
                            data.finishEpisode()
                            continue
                        }
                    }
                    if (saveFile.exists()) {
                        if (saveFile.length() >= originalFileSize) {
                            data.writeMessage("(IO) Skipping completed video.")
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
                            data.logError(
                                "Failed to create video file after 3 retries. | Skipping episode",
                                fileError
                            )
                            data.finishEpisode()
                            continue
                        }
                    }
                    data.writeMessage("Starting download with ${qualityOption.tag} quality.")
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
                        Core.child.incrementDownloadsFinished()
                        data.writeMessage("Successfully downloaded with ${qualityOption.tag} quality.")
                        help.handleSecondVideo()
                        data.finishEpisode()
                    }
                }
            } catch (e: Exception) {
                data.currentDownload.queued = true
                data.currentDownload.downloading = false
                data.currentDownload.update()
                if (e.message?.contains("Connection reset", true) == false) {
                    data.logError(
                        "Failed to download video. Retrying...",
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