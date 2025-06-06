package nobility.downloader.core.scraper.video_download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nobility.downloader.core.BoxHelper.Companion.downloadForSlugAndQuality
import nobility.downloader.core.BoxHelper.Companion.int
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.Core
import nobility.downloader.core.entities.Download
import nobility.downloader.core.scraper.MovieHandler
import nobility.downloader.core.scraper.data.DownloadData
import nobility.downloader.core.scraper.video_download.Functions.downloadVideo
import nobility.downloader.core.scraper.video_download.Functions.fileSize
import nobility.downloader.core.settings.Defaults
import nobility.downloader.core.settings.Quality
import nobility.downloader.utils.*
import org.openqa.selenium.By
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.io.File
import java.time.Duration

object MovieDownloader {

    suspend fun handleMovie(
        movie: MovieHandler.Movie,
        data: VideoDownloadData
    ) = withContext(Dispatchers.IO) {
        data.logInfo("Detected movie for ${movie.slug}")
        var qualityOption = data.temporaryQuality ?: Quality.qualityForTag(
            Defaults.QUALITY.string()
        )
        var preferredDownload: DownloadData? = null
        var downloadLink = ""
        var videoLinkError = ""
        val downloadFolderPath = Defaults.SAVE_FOLDER.string()
        var saveFolder = File(
            downloadFolderPath + File.separator + "Movies"
        )
        if (!saveFolder.exists() && !saveFolder.mkdirs()) {
            data.writeMessage(
                "Failed to create movies save folder: ${saveFolder.absolutePath} " +
                        "\nDefaulting to $downloadFolderPath"
            )
            saveFolder = File(downloadFolderPath + File.separator)
        }
        if (data.quickCheckVideoExists(movie.slug)) {
            return@withContext
        }
        val wait = WebDriverWait(data.driver, Duration.ofSeconds(15))
        val premUser = Defaults.WCO_PREMIUM_USERNAME.string()
        val premPassword = Defaults.WCO_PREMIUM_PASSWORD.string()

        if (premUser.isNotEmpty() && premPassword.isNotEmpty()) {
            if (data.premRetries < Defaults.PREMIUM_RETRIES.int()) {
                if (data.downloadDatas.isEmpty()) {
                    try {
                        data.driver.navigate().to(
                            "https://www.wcopremium.tv/wp-login.php"
                        )
                        val usernameField = data.driver.findElement(By.id("user_login"))
                        val passwordField = data.driver.findElement(By.id("user_pass"))
                        usernameField.sendKeys(premUser)
                        passwordField.sendKeys(premPassword)
                        passwordField.submit()
                        try {
                            //look for an element only found while logged in.
                            wait.pollingEvery(Duration.ofSeconds(1))
                                .withTimeout(Duration.ofSeconds(15))
                                .until(
                                    ExpectedConditions.presenceOfElementLocated(
                                        By.className("header-top-right")
                                    )
                                )
                            //no error. logged in successfully
                            data.driver.navigate().to(
                                "https://www.wcopremium.tv/${movie.slug}"
                            )
                            val src = data.driver.source().lines()
                            val hd1080 = """format: "hd1080", src: """"
                            val hd720 = """format: "hd720", src: """"
                            val sd576 = """format: "sd576", src: """"
                            val endTag = """", type: "video/mp4"},"""
                            src.forEach { line ->
                                if (line.contains(hd1080)) {
                                    data.downloadDatas.add(
                                        DownloadData(
                                            Quality.HIGH,
                                            line.substringAfter(hd1080).substringBefore(endTag)
                                        )
                                    )
                                    data.logInfo("[PREM] Found ${Quality.HIGH} quality.")
                                } else if (line.contains(hd720)) {
                                    data.downloadDatas.add(
                                        DownloadData(
                                            Quality.MED,
                                            line.substringAfter(hd720).substringBefore(endTag)
                                        )
                                    )
                                    data.logInfo("[PREM] Found ${Quality.MED} quality.")
                                } else if (line.contains(sd576)) {
                                    data.downloadDatas.add(
                                        DownloadData(
                                            Quality.LOW,
                                            line.substringAfter(sd576).substringBefore(endTag)
                                        )
                                    )
                                    data.logInfo("[PREM] Found ${Quality.LOW} quality.")
                                }
                            }
                            if (data.downloadDatas.isNotEmpty()) {
                                qualityOption = Quality.bestQuality(
                                    qualityOption,
                                    data.downloadDatas.map { it.quality }
                                )
                                data.downloadDatas.forEach {
                                    if (it.quality == qualityOption) {
                                        preferredDownload = it
                                        downloadLink = it.downloadLink
                                    }
                                }
                            }
                        } catch (_: Exception) {
                            data.writeMessage(
                                "Your wcopremium.tv credentials didn't work. | Retrying..."
                            )
                            data.premRetries++
                            return@withContext
                        }
                    } catch (e: Exception) {
                        data.writeMessage(
                            "Failed to log into wcopremium.tv | Retrying..."
                        )
                        data.logError(
                            "Failed to log into wcopremium.tv",
                            e
                        )
                        data.retries++
                        return@withContext
                    }
                }
            } else {
                data.writeMessage("Reached max premium login retries. Using regular method.")
            }
        }
        if (downloadLink.isEmpty()) {
            val link = MovieHandler.wcoMoviePlaylistLink + "${movie.tag}/${movie.slug}"
            data.driver.navigate().to(link)
            try {
                val videoPlayer = data.driver.findElement(
                    By.xpath("//*[@id=\"my-video\"]/div[2]/video")
                )
                wait.pollingEvery(Duration.ofSeconds(1))
                    .withTimeout(Duration.ofSeconds(15))
                    .until(ExpectedConditions.attributeToBeNotEmpty(videoPlayer, "src"))
                val src = videoPlayer.getDomAttribute("src")
                val videoError = videoPlayer.getDomAttribute("innerHTML")
                if (src != null) {
                    downloadLink = src
                }
                if (videoError != null) {
                    videoLinkError = videoError
                }
                qualityOption = Quality.LOW
            } catch (e: Exception) {
                data.writeMessage(
                    "Failed to find video player for movie: $link | Retrying..."
                )
                data.logError(
                    "Failed to find video player for movie: $link",
                    e
                )
                data.retries++
                return@withContext
            }
        }
        if (videoLinkError.isNotEmpty()) {
            data.logError(
                "Failed to find video link for movie.",
                videoLinkError
            )
            data.retries++
            return@withContext
        }
        if (downloadLink.isEmpty()) {
            data.writeMessage(
                "Failed to find video link for movie. | Retrying..."
            )
            data.retries++
            return@withContext
        }
        val extraQualityName = if (qualityOption != Quality.LOW)
            " (${qualityOption.tag})" else ""
        val episodeName = movie.name.fixForFiles()
        val saveFile = File(
            saveFolder.absolutePath + File.separator
                    + "$episodeName$extraQualityName.mp4"
        )
        data.mCurrentDownload = downloadForSlugAndQuality(
            movie.slug,
            qualityOption
        )
        if (data.mCurrentDownload != null) {
            if (data.currentDownload.downloadPath.isEmpty()
                || !File(data.currentDownload.downloadPath).exists()
                || data.currentDownload.downloadPath != saveFile.absolutePath
            ) {
                data.currentDownload.downloadPath = saveFile.absolutePath
            }
            Core.child.addDownload(data.currentDownload)
            if (data.currentDownload.isComplete) {
                data.writeMessage("[DB] Skipping completed video.")
                data.finishEpisode()
                return@withContext
            } else {
                data.currentDownload.queued = true
                Core.child.updateDownloadProgress(data.currentDownload)
            }
        }
        data.logInfo("Successfully found movie video link with ${data.retries} retries.")
        try {
            if (data.mCurrentDownload == null) {
                data.mCurrentDownload = Download()
                data.currentDownload.downloadPath = saveFile.absolutePath
                data.currentDownload.name = data.episode.name
                data.currentDownload.slug = data.episode.slug
                data.currentDownload.seriesSlug = data.episode.seriesSlug
                data.currentDownload.resolution = qualityOption.resolution
                data.currentDownload.dateAdded = System.currentTimeMillis()
                data.currentDownload.fileSize = 0
                data.currentDownload.queued = true
                Core.child.addDownload(data.currentDownload)
                data.logInfo("Created new download for movie.")
            } else {
                data.logInfo("Using existing download for movie.")
            }
            data.driver.navigate().to(downloadLink)
            val fileSizeRetries = Defaults.FILE_SIZE_RETRIES.int()
            var originalFileSize = 0L
            var headMode = true
            data.logInfo("Checking movie file size with $fileSizeRetries retries.")
            for (i in 0..fileSizeRetries) {
                originalFileSize = fileSize(
                    downloadLink,
                    data.userAgent,
                    headMode
                )
                if (originalFileSize <= Constants.minFileSize) {
                    headMode = headMode.not()
                    if (i == fileSizeRetries / 2) {
                        data.writeMessage(
                            "Failed to find movie file size. Current retries: $i"
                        )
                    }
                    continue
                } else {
                    break
                }
            }
            if (originalFileSize <= Constants.minFileSize) {
                if (data.downloadDatas.isNotEmpty()) {
                    data.logError(
                        "Failed to find movie file size after $fileSizeRetries retries. | Using another quality."
                    )
                    if (preferredDownload != null) {
                        data.downloadDatas.remove(
                            preferredDownload
                        )
                    }
                    data.retries++
                } else {
                    data.logError(
                        "Failed to find movie file size after $fileSizeRetries retries. | Skipping movie..."
                    )
                    data.finishEpisode()
                }
                return@withContext
            }
            data.logDebug("Successfully found movie file size of: ${Tools.bytesToString(originalFileSize)}")
            if (saveFile.exists()) {
                if (saveFile.length() >= originalFileSize) {
                    data.writeMessage("[IO] Skipping completed movie.")
                    data.currentDownload.downloadPath = saveFile.absolutePath
                    data.currentDownload.fileSize = originalFileSize
                    data.currentDownload.update()
                    data.finishEpisode()
                    return@withContext
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
                        "Failed to create movie video file after 3 retries. | Skipping movie...",
                        fileError,
                        true
                    )
                    data.finishEpisode()
                    return@withContext
                }
            }
            data.writeMessage("Starting movie download with ${qualityOption.tag} quality.")
            data.currentDownload.queued = false
            data.currentDownload.downloading = true
            data.currentDownload.fileSize = originalFileSize
            Core.child.addDownload(data.currentDownload)
            data.currentDownload.update()
            //data.driver.navigate().back()
            data.undriver.blank()
            downloadVideo(downloadLink, saveFile, data)
            data.currentDownload.downloading = false
            //second time to ensure ui update
            data.currentDownload.update()
            if (saveFile.exists() && saveFile.length() >= originalFileSize) {
                Core.child.downloadThread.incrementDownloadsFinished()
                data.writeMessage("Successfully downloaded movie.")
                data.finishEpisode()
            }
        } catch (e: Exception) {
            data.currentDownload.queued = true
            data.currentDownload.downloading = false
            data.currentDownload.update()
            data.writeMessage(
                "Failed to download movie. | Retrying..."
            )
            data.logError(
                "Failed to download movie.",
                e
            )
        }
    }
}