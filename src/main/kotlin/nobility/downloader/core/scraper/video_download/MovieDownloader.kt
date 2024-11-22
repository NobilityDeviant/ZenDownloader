package nobility.downloader.core.scraper.video_download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nobility.downloader.core.BoxHelper.Companion.downloadForSlugAndQuality
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.Core
import nobility.downloader.core.entities.Download
import nobility.downloader.core.scraper.MovieHandler
import nobility.downloader.core.scraper.video_download.Functions.downloadVideo
import nobility.downloader.core.scraper.video_download.Functions.fileSize
import nobility.downloader.core.settings.Defaults
import nobility.downloader.core.settings.Quality
import nobility.downloader.utils.fixForFiles
import nobility.downloader.utils.slugToLink
import org.openqa.selenium.By
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.io.File
import java.io.IOException
import java.time.Duration

object MovieDownloader {

    suspend fun handleMovie(
        movie: MovieHandler.Movie,
        data: VideoDownloadData
    ) = withContext(Dispatchers.IO) {
        val downloadFolderPath = Defaults.SAVE_FOLDER.string()
        var saveFolder = File(
            downloadFolderPath + File.separator + "Movies"
        )
        if (!saveFolder.exists() && !saveFolder.mkdirs()) {
            data.writeMessage(
                "Failed to create movies save folder: ${saveFolder.absolutePath} " +
                        "Defaulting to $downloadFolderPath"
            )
            saveFolder = File(downloadFolderPath + File.separator)
        }
        val episodeName = movie.name.fixForFiles()
        val saveFile = File(
            saveFolder.absolutePath + File.separator
                    + "$episodeName.mp4"
        )
        data.mCurrentDownload = downloadForSlugAndQuality(
            movie.slug,
            Quality.LOW
        )
        data.writeMessage("Detected movie for ${movie.slug}. Using movie mode.")
        if (data.mCurrentDownload != null) {
            if (data.currentDownload.downloadPath.isEmpty()
                || !File(data.currentDownload.downloadPath).exists()
                || data.currentDownload.downloadPath != saveFile.absolutePath
            ) {
                data.currentDownload.downloadPath = saveFile.absolutePath
            }
            Core.child.addDownload(data.currentDownload)
            if (data.currentDownload.isComplete) {
                data.writeMessage("[DB] Skipping completed video: " + movie.name)
                data.currentDownload.downloading = false
                data.currentDownload.queued = false
                Core.child.updateDownloadInDatabase(
                    data.currentDownload,
                    true
                )
                data.finishEpisode()
                return@withContext
            } else {
                data.currentDownload.queued = true
                Core.child.updateDownloadProgress(data.currentDownload)
            }
        }
        val link = MovieHandler.wcoMoviePlaylistLink + "${movie.tag}/${movie.slug}"
        data.driver.navigate().to(link)
        val wait = WebDriverWait(data.driver, Duration.ofSeconds(15))
        val downloadLink: String
        val videoLinkError: String
        try {

            val videoPlayer = data.driver.findElement(
                By.xpath("//*[@id=\"my-video\"]/div[2]/video")
            )
            wait.pollingEvery(Duration.ofSeconds(1))
                .withTimeout(Duration.ofSeconds(15))
                .until(ExpectedConditions.attributeToBeNotEmpty(videoPlayer, "src"))
            downloadLink = videoPlayer.getAttribute("src")
            videoLinkError = videoPlayer.getAttribute("innerHTML")
        } catch (_: Exception) {
            data.writeMessage(
                """
                    Failed to find video player for movie: $link
                    Retrying...
                """.trimIndent()
            )
            data.retries++
            return@withContext
        }
        if (downloadLink.isEmpty()) {
            if (videoLinkError.isNotEmpty()) {
                data.logInfo("Movie mode empty link source: \n${videoLinkError.trim()}")
            }
            data.writeMessage(
                "Failed to find video link for movie: ${movie.slug.slugToLink()}. Retrying..."
            )
            data.retries++
            return@withContext
        }
        data.logInfo("Successfully found movie video link with $data.retries retries.")

        try {
            if (data.mCurrentDownload == null) {
                data.mCurrentDownload = Download()
                data.currentDownload.downloadPath = saveFile.absolutePath
                //ignore these warnings
                data.currentDownload.name = data.currentEpisode.name
                data.currentDownload.slug = data.currentEpisode.slug
                data.currentDownload.seriesSlug = data.currentEpisode.seriesSlug
                data.currentDownload.resolution = Quality.LOW.resolution
                data.currentDownload.dateAdded = System.currentTimeMillis()
                data.currentDownload.fileSize = 0
                data.currentDownload.queued = true
                Core.child.addDownload(data.currentDownload)
                data.logInfo("Created new download.")
            } else {
                data.logInfo("Using existing download.")
            }
            data.driver.navigate().to(downloadLink)
            val originalFileSize = fileSize(downloadLink, data.base.userAgent)
            if (originalFileSize <= 5000) {
                data.writeMessage("Failed to determine movie file size. Retrying...")
                data.retries++
                return@withContext
            }
            if (saveFile.exists()) {
                if (saveFile.length() >= originalFileSize) {
                    data.writeMessage("[IO Skipping completed movie.")
                    data.currentDownload.downloadPath = saveFile.absolutePath
                    data.currentDownload.fileSize = originalFileSize
                    data.currentDownload.downloading = false
                    data.currentDownload.queued = false
                    Core.child.updateDownloadInDatabase(data.currentDownload, true)
                    data.finishEpisode()
                    return@withContext
                }
            } else {
                try {
                    val created = saveFile.createNewFile()
                    if (!created) {
                        throw Exception("No error thrown.")
                    }
                } catch (e: Exception) {
                    data.logError(
                        "Unable to create video file for the movie ${data.currentEpisode.name}",
                        e
                    )
                    data.writeMessage("Failed to create new video file for the movie ${data.currentEpisode.name} Retrying...")
                    data.retries++
                    return@withContext
                }
            }
            data.writeMessage("Downloading: " + data.currentDownload.name)
            data.currentDownload.queued = false
            data.currentDownload.downloading = true
            data.currentDownload.fileSize = originalFileSize
            Core.child.addDownload(data.currentDownload)
            Core.child.updateDownloadInDatabase(data.currentDownload, true)
            data.driver.navigate().back()
            downloadVideo(downloadLink, saveFile, data)
            data.currentDownload.downloading = false
            //second time to ensure ui update
            Core.child.updateDownloadInDatabase(data.currentDownload, true)
            if (saveFile.exists() && saveFile.length() >= originalFileSize) {
                Core.child.incrementDownloadsFinished()
                data.writeMessage("Successfully downloaded movie: $episodeName")
                data.finishEpisode()
            }
        } catch (e: IOException) {
            data.currentDownload.queued = true
            data.currentDownload.downloading = false
            Core.child.updateDownloadInDatabase(data.currentDownload, true)
            data.writeMessage(
                """
                   Failed to download the movie $episodeName
                   Error: ${e.localizedMessage}
                   Reattempting the download...
                """.trimIndent()
            )
            data.logError(
                "Failed to download the movie $episodeName",
                e
            )
        }
    }
}