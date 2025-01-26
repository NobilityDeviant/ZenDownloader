package nobility.downloader.core.scraper.video_download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nobility.downloader.core.Core
import nobility.downloader.core.scraper.video_download.Functions.downloadVideo
import nobility.downloader.core.scraper.video_download.Functions.fileSize
import nobility.downloader.core.scraper.video_download.MovieDownloader.handleMovie
import nobility.downloader.core.settings.Quality
import nobility.downloader.utils.Constants

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
            //if (data.reachedMaxSimple()) continue
            if (data.reachedMaxM3U8()) continue
            if (!data.getNewEpisode()) break
            val slug = data.currentEpisode.slug
            if (slug.isEmpty()) {
                data.writeMessage("Skipping video with no slug.")
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
                    var originalFileSize = fileSize(downloadLink, data.userAgent)
                    if (originalFileSize <= 5000) {
                        if (data.retries < 2) {
                            data.writeMessage("Failed to determine file size. Retrying...")
                            data.retries++
                            continue
                        } else if (data.retries in 2..Constants.maxRetries - 1) {
                            data.writeMessage("Failed to determine file size. Retrying with a different quality...")
                            data.qualityAndDownloads.remove(
                                data.qualityAndDownloads.first {
                                    it.downloadLink == downloadLink
                                }
                            )
                            data.retries++
                            continue
                        }
                    }
                    if (saveFile.exists()) {
                        if (originalFileSize > 0 && saveFile.length() >= originalFileSize) {
                            data.writeMessage("(IO) Skipping completed video.")
                            data.currentDownload.downloadPath = saveFile.absolutePath
                            data.currentDownload.fileSize = originalFileSize
                            data.currentDownload.downloading = false
                            data.currentDownload.queued = false
                            Core.child.updateDownloadInDatabase(
                                data.currentDownload,
                                true
                            )
                            data.finishEpisode()
                            continue
                        }
                    } else {
                        try {
                            val created = saveFile.createNewFile()
                            if (!created) {
                                throw Exception("No error thrown.")
                            }
                        } catch (e: Exception) {
                            data.logError(
                                "Failed to create video file. Retrying...",
                                e,
                                true
                            )
                            data.retries++
                            continue
                        }
                    }
                    data.writeMessage("Starting download with ${qualityOption.tag} quality.")
                    data.currentDownload.queued = false
                    data.currentDownload.downloading = true
                    data.currentDownload.fileSize = originalFileSize
                    Core.child.addDownload(data.currentDownload)
                    Core.child.updateDownloadInDatabase(data.currentDownload, true)
                    data.driver.navigate().to("https://blank.org")
                    //data.driver.navigate().back()
                    downloadVideo(
                        downloadLink,
                        saveFile,
                        data
                    )
                    //originalFileSize = saveFile.length()
                    data.currentDownload.downloading = false
                    //second time to ensure ui update
                    Core.child.updateDownloadInDatabase(
                        data.currentDownload,
                        true
                    )
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
                Core.child.updateDownloadInDatabase(
                    data.currentDownload,
                    true
                )
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