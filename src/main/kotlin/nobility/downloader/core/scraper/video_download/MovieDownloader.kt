package nobility.downloader.core.scraper.video_download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nobility.downloader.core.BoxHelper.Companion.downloadForSlugAndQuality
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.Core
import nobility.downloader.core.entities.Download
import nobility.downloader.core.scraper.MovieHandler
import nobility.downloader.core.scraper.data.QualityAndDownload
import nobility.downloader.core.scraper.video_download.Functions.downloadVideo
import nobility.downloader.core.scraper.video_download.Functions.fileSize
import nobility.downloader.core.settings.Defaults
import nobility.downloader.core.settings.Quality
import nobility.downloader.utils.Constants
import nobility.downloader.utils.fixForFiles
import nobility.downloader.utils.slugToLink
import org.openqa.selenium.By
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.io.File
import java.time.Duration

object MovieDownloader {

    @Suppress("SelfAssignment")
    suspend fun handleMovie(
        movie: MovieHandler.Movie,
        data: VideoDownloadData
    ) = withContext(Dispatchers.IO) {
        data.logInfo("Detected movie for ${movie.slug}")
        var qualityOption = data.temporaryQuality ?: Quality.qualityForTag(
            Defaults.QUALITY.string()
        )
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
            if (data.premRetries < Constants.maxPremRetries) {
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
                        val movieQualities = mutableListOf<QualityAndDownload>()
                        val src = data.driver.pageSource.lines()
                        val hd1080 = """format: "hd1080", src: """"
                        val hd720 = """format: "hd720", src: """"
                        val sd576 = """format: "sd576", src: """"
                        val endTag = """", type: "video/mp4"},"""
                        src.forEach { line ->
                            if (line.contains(hd1080)) {
                                movieQualities.add(
                                    QualityAndDownload(
                                        Quality.HIGH,
                                        line.substringAfter(hd1080).substringBefore(endTag)
                                    )
                                )
                                data.logInfo("[PREM] Found ${Quality.HIGH} quality.")
                            } else if (line.contains(hd720)) {
                                movieQualities.add(
                                    QualityAndDownload(
                                        Quality.MED,
                                        line.substringAfter(hd720).substringBefore(endTag)
                                    )
                                )
                                data.logInfo("[PREM] Found ${Quality.MED} quality.")
                            } else if (line.contains(sd576)) {
                                movieQualities.add(
                                    QualityAndDownload(
                                        Quality.LOW,
                                        line.substringAfter(sd576).substringBefore(endTag)
                                    )
                                )
                                data.logInfo("[PREM] Found ${Quality.LOW} quality.")
                            }
                        }
                        if (movieQualities.isNotEmpty()) {
                            qualityOption = Quality.bestQuality(
                                qualityOption,
                                movieQualities.map { it.quality }
                            )
                            movieQualities.forEach {
                                if (it.quality == qualityOption) {
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
                        /*try {
                            val loginError = data.driver.findElement(By.id("login_error"))
                            if (loginError.text.contains("is not registered")) {

                            }
                        } catch (_: Exception) {}*/
                        //clears the login error
                        //data.driver.navigate().to(
                        //  "https://www.wcopremium.tv/wp-login.php?=random"
                        //)
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
                downloadLink = videoPlayer.getAttribute("src")
                videoLinkError = videoPlayer.getAttribute("innerHTML")
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
        if (downloadLink.isEmpty()) {
            data.writeMessage(
                "Failed to find video link for movie: ${movie.slug.slugToLink()}. | Retrying..."
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
                data.currentDownload.name = data.currentEpisode.name
                data.currentDownload.slug = data.currentEpisode.slug
                data.currentDownload.seriesSlug = data.currentEpisode.seriesSlug
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
            val originalFileSize = fileSize(downloadLink, data.base.userAgent)
            if (originalFileSize <= 5000) {
                data.writeMessage("Failed to determine movie file size. Retrying...")
                data.retries++
                return@withContext
            }
            if (saveFile.exists()) {
                if (saveFile.length() >= originalFileSize) {
                    data.writeMessage("[IO] Skipping completed movie.")
                    data.currentDownload.downloadPath = saveFile.absolutePath
                    data.currentDownload.fileSize = originalFileSize
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
                        "Failed to create video file.",
                        e
                    )
                    data.writeMessage("Failed to create new video file. Retrying...")
                    data.retries++
                    return@withContext
                }
            }
            data.writeMessage("Starting movie download with ${qualityOption.tag} quality.")
            data.currentDownload.queued = false
            data.currentDownload.downloading = true
            data.currentDownload.fileSize = originalFileSize
            Core.child.addDownload(data.currentDownload)
            Core.child.updateDownloadInDatabase(data.currentDownload, true)
            //data.driver.navigate().back()
            data.undriver.blank()
            downloadVideo(downloadLink, saveFile, data)
            data.currentDownload.downloading = false
            //second time to ensure ui update
            Core.child.updateDownloadInDatabase(data.currentDownload, true)
            if (saveFile.exists() && saveFile.length() >= originalFileSize) {
                Core.child.incrementDownloadsFinished()
                data.writeMessage("Successfully downloaded movie.")
                data.finishEpisode()
            }
        } catch (e: Exception) {
            data.currentDownload.queued = true
            data.currentDownload.downloading = false
            Core.child.updateDownloadInDatabase(data.currentDownload, true)
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